package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CommunicationMessageRepository extends JpaRepository<CommunicationMessage, UUID> {}
