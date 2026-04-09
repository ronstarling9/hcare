package com.hcare.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "hcare.portal")
public class PortalProperties {

    private String baseUrl = "http://localhost:5173";
    private Jwt jwt = new Jwt();

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }

    public static class Jwt {
        private int expirationDays = 30;

        /**
         * Required — must be at least 256 bits (32 bytes) of random data in production.
         * No safe default exists.
         */
        @NotBlank
        private String secret;

        public int getExpirationDays() { return expirationDays; }
        public void setExpirationDays(int expirationDays) { this.expirationDays = expirationDays; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }
}
