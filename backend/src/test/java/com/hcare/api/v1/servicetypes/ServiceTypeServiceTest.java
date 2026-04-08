package com.hcare.api.v1.servicetypes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.api.v1.servicetypes.dto.ServiceTypeResponse;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTypeServiceTest {

  @Mock private ServiceTypeRepository serviceTypeRepository;

  private ServiceTypeService service;

  private final UUID agencyId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ServiceTypeService(serviceTypeRepository, new ObjectMapper());
  }

  @Test
  void listServiceTypes_returns_alphabetically_sorted_by_name_case_insensitive() {
    ServiceType bravo = new ServiceType(agencyId, "bravo", "BR", false, "[]");
    ServiceType alpha = new ServiceType(agencyId, "Alpha", "AL", true, "[]");
    when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(bravo, alpha));

    List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("Alpha");
    assertThat(result.get(1).name()).isEqualTo("bravo");
  }

  @Test
  void listServiceTypes_parses_requiredCredentials_json_array() {
    ServiceType st = new ServiceType(agencyId, "PCS", "PCS", true, "[\"CPR\",\"FIRST_AID\"]");
    when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(st));

    List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

    assertThat(result.get(0).requiredCredentials()).containsExactly("CPR", "FIRST_AID");
  }

  @Test
  void listServiceTypes_parses_empty_credentials_array() {
    ServiceType st = new ServiceType(agencyId, "SNV", "SNV", true, "[]");
    when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(st));

    List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

    assertThat(result.get(0).requiredCredentials()).isEmpty();
  }

  @Test
  void listServiceTypes_returns_empty_credentials_on_malformed_json_and_does_not_throw() {
    ServiceType st = new ServiceType(agencyId, "PCS", "PCS", false, "NOT_JSON");
    when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(st));

    List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

    assertThat(result.get(0).requiredCredentials()).isEmpty();
  }

  @Test
  void listServiceTypes_returns_empty_credentials_when_requiredCredentials_is_null() {
    ServiceType st = new ServiceType(agencyId, "PCS", "PCS", false, null);
    when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of(st));

    List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

    assertThat(result.get(0).requiredCredentials()).isEmpty();
  }

  @Test
  void listServiceTypes_returns_empty_list_for_agency_with_no_service_types() {
    when(serviceTypeRepository.findByAgencyId(agencyId)).thenReturn(List.of());

    List<ServiceTypeResponse> result = service.listServiceTypes(agencyId);

    assertThat(result).isEmpty();
  }
}
