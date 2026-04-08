package com.hcare.api.v1.servicetypes;

import com.hcare.api.v1.servicetypes.dto.ServiceTypeResponse;
import com.hcare.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-types")
public class ServiceTypeController {

  private final ServiceTypeService serviceTypeService;

  public ServiceTypeController(ServiceTypeService serviceTypeService) {
    this.serviceTypeService = serviceTypeService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
  public ResponseEntity<List<ServiceTypeResponse>> listServiceTypes(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(serviceTypeService.listServiceTypes(principal.getAgencyId()));
  }
}
