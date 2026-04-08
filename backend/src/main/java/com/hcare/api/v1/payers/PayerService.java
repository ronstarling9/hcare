package com.hcare.api.v1.payers;

import com.hcare.api.v1.clients.dto.AuthorizationResponse;
import com.hcare.api.v1.payers.dto.PayerResponse;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.evv.AggregatorType;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import com.hcare.multitenancy.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PayerService {

    private final PayerRepository payerRepository;
    private final EvvStateConfigRepository evvStateConfigRepository;
    private final AuthorizationRepository authorizationRepository;

    public PayerService(PayerRepository payerRepository,
                        EvvStateConfigRepository evvStateConfigRepository,
                        AuthorizationRepository authorizationRepository) {
        this.payerRepository = payerRepository;
        this.evvStateConfigRepository = evvStateConfigRepository;
        this.authorizationRepository = authorizationRepository;
    }

    @Transactional(readOnly = true)
    public Page<PayerResponse> listPayers(UUID agencyId, Pageable pageable) {
        // PayerRepository has findByAgencyId(agencyId) returning List — paginate manually
        // to avoid a separate count query on a typically small dataset (< 20 payers/agency).
        // Wrap in ArrayList so the list is mutable for sort; JPA may return a fixed-size list.
        List<Payer> all = new ArrayList<>(payerRepository.findByAgencyId(agencyId));
        all.sort(Comparator.comparing(Payer::getName, String.CASE_INSENSITIVE_ORDER));

        // Slice the raw list FIRST so toResponse is only called for payers on this page.
        // Mapping before paginating caused spurious state-config DB calls for payers that
        // would never appear in the response (and NPEs in tests for out-of-bounds pages).
        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), all.size());
        List<Payer> slice = start >= all.size() ? List.of() : all.subList(start, end);

        // Cache keyed by Optional so that absent-config lookups are also stored.
        // HashMap.computeIfAbsent does not insert null-returning mapping functions,
        // so using Optional<EvvStateConfig> as the value type is required.
        Map<String, Optional<EvvStateConfig>> stateConfigCache = new HashMap<>();

        List<PayerResponse> page = slice.stream()
            .map(p -> toResponse(p, stateConfigCache))
            .toList();

        return new PageImpl<>(page, pageable, all.size());
    }

    @Transactional(readOnly = true)
    public PayerResponse getPayer(UUID payerId) {
        Payer payer = payerRepository.findByIdAndAgencyId(payerId, TenantContext.get())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payer not found: " + payerId));

        Map<String, Optional<EvvStateConfig>> cache = new HashMap<>();
        return toResponse(payer, cache);
    }

    @Transactional(readOnly = true)
    public Page<AuthorizationResponse> listAuthorizationsForPayer(UUID payerId, Pageable pageable) {
        getPayer(payerId);
        return authorizationRepository.findByPayerId(payerId, pageable)
            .map(AuthorizationResponse::from);
    }

    private PayerResponse toResponse(Payer payer, Map<String, Optional<EvvStateConfig>> cache) {
        String aggregatorName = null;
        if (payer.getState() != null) {
            // computeIfAbsent with Optional value caches absent results too
            Optional<EvvStateConfig> configOpt = cache.computeIfAbsent(
                payer.getState(),
                evvStateConfigRepository::findByStateCode);
            if (configOpt.isPresent()) {
                AggregatorType aggregator = configOpt.get().getDefaultAggregator();
                aggregatorName = aggregator != null ? aggregator.name() : null;
            }
        }
        return new PayerResponse(
            payer.getId(),
            payer.getName(),
            payer.getPayerType(),
            payer.getState(),
            aggregatorName,
            payer.getCreatedAt()
        );
    }
}
