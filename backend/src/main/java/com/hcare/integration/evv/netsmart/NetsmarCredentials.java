package com.hcare.integration.evv.netsmart;

public record NetsmarCredentials(
        String username,
        String sftpHost,
        int port,
        String sourceId,
        String privateKeyRef) {}
