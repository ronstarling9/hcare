package com.hcare.integration.evv;

import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.EvvRecord;
import com.hcare.domain.EvvRecordRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import com.hcare.evv.AggregatorType;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import com.hcare.evv.EvvSystemModel;
import com.hcare.evv.PayerEvvRoutingConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Assembles an {@link EvvSubmissionContext} from domain entities.
 *
 * <p>C16: No credential values are included in the context — credentials are resolved at
 * submit time by the caller. The resulting context is safe to serialize to {@code context_json}.
 *
 * <p>Routing resolution order:
 * <ol>
 *   <li>Look up {@link com.hcare.evv.PayerEvvRoutingConfig} by (stateCode, payerType) for
 *       payer-specific aggregator selection (e.g. MCO-specific mappings in NY/FL/NC/VA).
 *   <li>Fall back to {@link EvvStateConfig#getDefaultAggregator()} for the state.
 * </ol>
 */
@Service
public class EvvSubmissionContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(EvvSubmissionContextAssembler.class);

    private final EvvRecordRepository evvRecordRepository;
    private final ShiftRepository shiftRepository;
    private final AuthorizationRepository authorizationRepository;
    private final CaregiverRepository caregiverRepository;
    private final ClientRepository clientRepository;
    private final PayerRepository payerRepository;
    private final EvvStateConfigRepository evvStateConfigRepository;
    private final PayerEvvRoutingConfigRepository payerEvvRoutingConfigRepository;

    public EvvSubmissionContextAssembler(
            EvvRecordRepository evvRecordRepository,
            ShiftRepository shiftRepository,
            AuthorizationRepository authorizationRepository,
            CaregiverRepository caregiverRepository,
            ClientRepository clientRepository,
            PayerRepository payerRepository,
            EvvStateConfigRepository evvStateConfigRepository,
            PayerEvvRoutingConfigRepository payerEvvRoutingConfigRepository) {
        this.evvRecordRepository = evvRecordRepository;
        this.shiftRepository = shiftRepository;
        this.authorizationRepository = authorizationRepository;
        this.caregiverRepository = caregiverRepository;
        this.clientRepository = clientRepository;
        this.payerRepository = payerRepository;
        this.evvStateConfigRepository = evvStateConfigRepository;
        this.payerEvvRoutingConfigRepository = payerEvvRoutingConfigRepository;
    }

    /**
     * Assembles an {@link EvvSubmissionContext} for the given EVV record.
     *
     * <p>Entity loading order:
     * <ol>
     *   <li>{@link EvvRecord} → shiftId, agencyId, denormalized clientMedicaidId
     *   <li>{@link Shift} → clientId, caregiverId, serviceTypeId, authorizationId, hcpcsCode
     *   <li>{@link Authorization} (if present on shift) → payerId
     *   <li>{@link Client} → medicaidId fallback, serviceState
     *   <li>{@link Caregiver} → NPI
     *   <li>{@link Payer} → payerType, state (for routing)
     * </ol>
     *
     * @param evvRecordId    the EVV record to assemble context for
     * @param aggregatorType the resolved aggregator type (caller has already done routing)
     * @return a populated, credential-free submission context
     * @throws IllegalStateException if any required entity is missing
     */
    @Transactional(readOnly = true)
    public EvvSubmissionContext assemble(UUID evvRecordId, AggregatorType aggregatorType) {
        EvvRecord evvRecord = evvRecordRepository.findById(evvRecordId)
                .orElseThrow(() -> new IllegalStateException("EvvRecord not found: " + evvRecordId));

        Shift shift = shiftRepository.findById(evvRecord.getShiftId())
                .orElseThrow(() -> new IllegalStateException(
                        "Shift not found for evvRecordId=" + evvRecordId
                                + " shiftId=" + evvRecord.getShiftId()));

        // Resolve payerId via Authorization (Shift → Authorization → payerId)
        UUID payerId = null;
        if (shift.getAuthorizationId() != null) {
            payerId = authorizationRepository.findById(shift.getAuthorizationId())
                    .map(Authorization::getPayerId)
                    .orElse(null);
            if (payerId == null) {
                log.warn("Authorization not found or has no payerId: authorizationId={}",
                        shift.getAuthorizationId());
            }
        }

        Client client = clientRepository.findById(shift.getClientId())
                .orElseThrow(() -> new IllegalStateException(
                        "Client not found: " + shift.getClientId()));

        Caregiver caregiver = shift.getCaregiverId() != null
                ? caregiverRepository.findById(shift.getCaregiverId())
                        .orElse(null)
                : null;

        if (caregiver == null && shift.getCaregiverId() != null) {
            log.warn("Caregiver not found: caregiverId={}", shift.getCaregiverId());
        }

        Payer payer = payerId != null
                ? payerRepository.findById(payerId).orElse(null)
                : null;

        if (payer == null && payerId != null) {
            log.warn("Payer not found: payerId={}", payerId);
        }

        // State for EVV routing: client's serviceState overrides the payer's state for
        // border-county agencies serving clients across state lines.
        String serviceState = client.getServiceState() != null
                ? client.getServiceState()
                : (payer != null ? payer.getState() : null);

        // stateCode used for routing is the same as serviceState in P1
        String stateCode = serviceState;

        // clientMedicaidId: prefer the denormalized value on EvvRecord (captured at clock-out),
        // fall back to the Client entity.
        String clientMedicaidId = evvRecord.getClientMedicaidId() != null
                ? evvRecord.getClientMedicaidId()
                : client.getMedicaidId();

        if (clientMedicaidId == null) {
            log.warn("No medicaidId for clientId={} — context will have null clientMedicaidId",
                    client.getId());
        }

        return new EvvSubmissionContext(
                evvRecordId,
                evvRecord.getShiftId(),
                evvRecord.getAgencyId(),
                shift.getClientId(),
                shift.getCaregiverId(),
                payerId,
                aggregatorType,
                stateCode,
                caregiver != null ? caregiver.getNpi() : null,
                clientMedicaidId,
                shift.getHcpcsCode(),
                evvRecord.getTimeIn(),
                evvRecord.getTimeOut(),
                serviceState,
                evvRecord.getAggregatorVisitId()
        );
    }

    /**
     * Resolves the aggregator type for the given EVV record using the two-tier routing model:
     * PayerEvvRoutingConfig (payer-specific) first, then EvvStateConfig.defaultAggregator fallback.
     *
     * @param stateCode the two-letter state code for EVV routing
     * @param payer     the payer associated with the shift (may be null)
     * @return the resolved aggregator type, or {@code null} if state config not found
     */
    public AggregatorType resolveAggregatorType(String stateCode, Payer payer) {
        if (stateCode == null) {
            log.warn("Cannot resolve aggregatorType — stateCode is null");
            return null;
        }

        // Tier 1: payer-specific routing (MCO-specific aggregator mappings)
        if (payer != null) {
            var payerRouting = payerEvvRoutingConfigRepository
                    .findByStateCodeAndPayerType(stateCode, payer.getPayerType());
            if (payerRouting.isPresent()) {
                return payerRouting.get().getAggregatorType();
            }
        }

        // Tier 2: state-level default aggregator
        return evvStateConfigRepository.findByStateCode(stateCode)
                .map(EvvStateConfig::getDefaultAggregator)
                .orElse(null);
    }

    /**
     * Returns the {@link EvvSystemModel} for the given state code, or {@code null} if not found.
     */
    public EvvSystemModel resolveSystemModel(String stateCode) {
        if (stateCode == null) return null;
        return evvStateConfigRepository.findByStateCode(stateCode)
                .map(EvvStateConfig::getSystemModel)
                .orElse(null);
    }

    /**
     * Returns {@code true} if the state requires real-time EVV submission.
     */
    public boolean requiresRealTimeSubmission(String stateCode) {
        if (stateCode == null) return false;
        return evvStateConfigRepository.findByStateCode(stateCode)
                .map(EvvStateConfig::isRequiresRealTimeSubmission)
                .orElse(false);
    }
}
