package com.hcare.api.v1.payers;

import com.hcare.api.v1.payers.dto.PayerResponse;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.PayerType;
import com.hcare.evv.AggregatorType;
import com.hcare.evv.EvvStateConfig;
import com.hcare.evv.EvvStateConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayerServiceTest {

    @Mock private PayerRepository payerRepository;
    @Mock private EvvStateConfigRepository evvStateConfigRepository;

    private PayerService service;

    @BeforeEach
    void setUp() {
        service = new PayerService(payerRepository, evvStateConfigRepository);
    }

    @Test
    void listPayers_returnsMappedPageWithEvvAggregator() {
        UUID agencyId = UUID.randomUUID();
        Payer payer = buildPayer(agencyId, "TX", PayerType.MEDICAID);
        EvvStateConfig config = buildStateConfig("TX", AggregatorType.SANDATA);

        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(payer));
        when(evvStateConfigRepository.findByStateCode("TX")).thenReturn(Optional.of(config));

        Page<PayerResponse> result = service.listPayers(agencyId, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        PayerResponse row = result.getContent().get(0);
        assertThat(row.evvAggregator()).isEqualTo("SANDATA");
        assertThat(row.state()).isEqualTo("TX");
    }

    @Test
    void listPayers_nullEvvAggregatorWhenNoStateConfig() {
        UUID agencyId = UUID.randomUUID();
        Payer payer = buildPayer(agencyId, "ZZ", PayerType.PRIVATE_PAY);

        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(payer));
        when(evvStateConfigRepository.findByStateCode("ZZ")).thenReturn(Optional.empty());

        Page<PayerResponse> result = service.listPayers(agencyId, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).evvAggregator()).isNull();
    }

    @Test
    void listPayers_stateConfigCachedAcrossMultiplePayersSameState() {
        UUID agencyId = UUID.randomUUID();
        Payer p1 = buildPayer(agencyId, "TX", PayerType.MEDICAID);
        Payer p2 = buildPayer(agencyId, "TX", PayerType.MEDICARE);
        EvvStateConfig config = buildStateConfig("TX", AggregatorType.SANDATA);

        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(p1, p2));
        when(evvStateConfigRepository.findByStateCode("TX")).thenReturn(Optional.of(config));

        service.listPayers(agencyId, PageRequest.of(0, 20));

        // Should only hit the repo once for "TX" despite two payers
        verify(evvStateConfigRepository, times(1)).findByStateCode("TX");
    }

    @Test
    void listPayers_unknownStateConfigCachedAcrossMultiplePayers() {
        // Absent (Optional.empty) results must also be cached so the repo is not called
        // once per payer when multiple payers share the same unknown state code.
        UUID agencyId = UUID.randomUUID();
        Payer p1 = buildPayer(agencyId, "ZZ", PayerType.PRIVATE_PAY);
        Payer p2 = buildPayer(agencyId, "ZZ", PayerType.MEDICAID);

        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(p1, p2));
        when(evvStateConfigRepository.findByStateCode("ZZ")).thenReturn(Optional.empty());

        Page<PayerResponse> result = service.listPayers(agencyId, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(r -> r.evvAggregator() == null);
        // Absent result must be cached — repo called only once for "ZZ" despite two payers
        verify(evvStateConfigRepository, times(1)).findByStateCode("ZZ");
    }

    @Test
    void listPayers_returnsEmptyPageWhenOutOfBounds() {
        UUID agencyId = UUID.randomUUID();
        when(payerRepository.findByAgencyId(agencyId)).thenReturn(List.of(buildPayer(agencyId, "TX", PayerType.MEDICAID)));

        Page<PayerResponse> result = service.listPayers(agencyId, PageRequest.of(5, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        // No evvStateConfigRepository stub needed — the service slices the raw List<Payer>
        // before calling toResponse, so toResponse is never called for out-of-bounds pages.
    }

    @Test
    void getPayer_returnsResponseWhenFound() {
        UUID payerId = UUID.randomUUID();
        Payer payer = buildPayer(UUID.randomUUID(), "NY", PayerType.MEDICAID);

        when(payerRepository.findById(payerId)).thenReturn(Optional.of(payer));
        when(evvStateConfigRepository.findByStateCode("NY")).thenReturn(Optional.empty());

        PayerResponse result = service.getPayer(payerId);

        assertThat(result.state()).isEqualTo("NY");
    }

    @Test
    void getPayer_throwsNotFoundWhenMissing() {
        UUID payerId = UUID.randomUUID();
        when(payerRepository.findById(payerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayer(payerId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- helpers ---

    private Payer buildPayer(UUID agencyId, String state, PayerType type) {
        return new Payer(agencyId, "Test Payer", type, state);
    }

    private EvvStateConfig buildStateConfig(String stateCode, AggregatorType aggregator) {
        EvvStateConfig config = mock(EvvStateConfig.class);
        when(config.getDefaultAggregator()).thenReturn(aggregator);
        return config;
    }
}
