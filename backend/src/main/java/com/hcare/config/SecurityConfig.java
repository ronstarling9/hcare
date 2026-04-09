package com.hcare.config;

import com.hcare.security.JwtAuthenticationFilter;
import com.hcare.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    // C7 fix: inject PortalProperties so corsConfigurationSource() can permit the portal
    // frontend origin (hcare.portal.base-url). Without this, pre-flight CORS requests from
    // the portal domain are rejected in staging/production — this is a silent runtime blocker.
    private final PortalProperties portalProperties;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider, PortalProperties portalProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.portalProperties = portalProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/agencies/register").permitAll()
                .requestMatchers("/api/v1/family/auth/verify").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader("WWW-Authenticate", "Bearer");
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Unauthorized\",\"status\":401}");
                })
            )
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // C7 fix: include the portal base URL so the portal frontend origin is permitted
        // in staging and production. Build the list defensively to avoid duplicates when
        // hcare.portal.base-url equals the dev admin origin (http://localhost:5173).
        List<String> origins = new ArrayList<>(List.of("http://localhost:5173"));
        if (!origins.contains(portalProperties.getBaseUrl())) {
            origins.add(portalProperties.getBaseUrl());
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
