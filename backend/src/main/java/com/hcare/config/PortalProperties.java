package com.hcare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
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
        public int getExpirationDays() { return expirationDays; }
        public void setExpirationDays(int expirationDays) { this.expirationDays = expirationDays; }
    }
}
