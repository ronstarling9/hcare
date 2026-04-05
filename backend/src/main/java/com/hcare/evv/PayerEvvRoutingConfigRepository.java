package com.hcare.evv;

import com.hcare.domain.PayerType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PayerEvvRoutingConfigRepository extends JpaRepository<PayerEvvRoutingConfig, UUID> {
    Optional<PayerEvvRoutingConfig> findByStateCodeAndPayerType(String stateCode, PayerType payerType);
}
