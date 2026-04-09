package com.hcare.integration.billing;

/**
 * Credentials required to submit claims to a billing connector on behalf of an agency.
 *
 * @param submitterId   connector submitter ID
 * @param apiKey        API key or password for the connector
 * @param interchangeId ISA06 interchange sender ID
 * @param groupId       GS02 group sender ID
 */
public record AgencyBillingCredentials(
        String submitterId,
        String apiKey,
        String interchangeId,
        String groupId) {}
