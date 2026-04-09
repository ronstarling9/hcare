package com.hcare.integration;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port for checking whether a given unit quantity would push an authorization over its
 * authorized-hours limit.
 *
 * <p>Implementations query the {@code Authorization} aggregate to compare consumed units
 * against the authorized cap, applying any in-flight adjustments. This interface exists as a
 * seam so that integration sub-modules and outbound services can validate utilization without
 * taking a direct dependency on the domain repository layer.
 */
public interface AuthorizationChecker {

  /**
   * Returns {@code true} if adding {@code units} to the current consumed total for the given
   * authorization would exceed (or exactly meet) the authorized limit.
   *
   * @param authorizationId the UUID of the {@code Authorization} record to check
   * @param units           the number of units (hours, visits, etc.) being requested
   * @return {@code true} if the authorization cap would be exceeded; {@code false} otherwise
   */
  boolean wouldExceedAuthorizedHours(UUID authorizationId, BigDecimal units);
}
