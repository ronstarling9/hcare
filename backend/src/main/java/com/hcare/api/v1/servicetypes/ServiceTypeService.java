package com.hcare.api.v1.servicetypes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.api.v1.servicetypes.dto.ServiceTypeResponse;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceTypeService {

  private static final Logger log = LoggerFactory.getLogger(ServiceTypeService.class);
  private static final TypeReference<List<String>> STRING_LIST_TYPE =
      new TypeReference<>() {};

  private final ServiceTypeRepository serviceTypeRepository;
  private final ObjectMapper objectMapper;

  public ServiceTypeService(ServiceTypeRepository serviceTypeRepository, ObjectMapper objectMapper) {
    this.serviceTypeRepository = serviceTypeRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<ServiceTypeResponse> listServiceTypes(UUID agencyId) {
    List<ServiceType> all = new ArrayList<>(serviceTypeRepository.findByAgencyId(agencyId));
    all.sort(Comparator.comparing(ServiceType::getName, String.CASE_INSENSITIVE_ORDER));
    return all.stream().map(this::toResponse).toList();
  }

  private ServiceTypeResponse toResponse(ServiceType st) {
    List<String> credentials;
    String raw = st.getRequiredCredentials();
    if (raw == null || raw.isBlank()) {
      credentials = List.of();
    } else {
      try {
        credentials = objectMapper.readValue(raw, STRING_LIST_TYPE);
      } catch (Exception e) {
        log.warn("Failed to parse requiredCredentials for ServiceType {}: {}",
            st.getId(), e.getMessage());
        credentials = List.of();
      }
    }
    return new ServiceTypeResponse(
        st.getId(),
        st.getName(),
        st.getCode(),
        st.isRequiresEvv(),
        credentials
    );
  }
}
