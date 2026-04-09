package com.hcare.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (StringUtils.hasText(token)) {
            Claims claims = tokenProvider.parseAndValidate(token);
            if (claims != null) {
                String subject = claims.getSubject();
                String agencyIdStr = claims.get("agencyId", String.class);
                String role = claims.get("role", String.class);

                if (subject == null || agencyIdStr == null || role == null) {
                    log.warn("JWT missing required claims (sub/agencyId/role) — rejecting");
                    chain.doFilter(request, response);
                    return;
                }

                UUID userId = UUID.fromString(subject);
                UUID agencyId = UUID.fromString(agencyIdStr);

                // For FAMILY_PORTAL tokens, extract clientId and populate it on the principal.
                // This is the hard scope boundary — dashboard controller reads principal.getClientId()
                // to restrict data to exactly one client.
                //
                // C2 fix: read clientId from the already-parsed claims object rather than calling
                // tokenProvider.getClientId(token), which would trigger a second HMAC parse of the
                // same token. More critically, the original code was fail-open: if clientId was absent
                // the token was still authenticated with a null clientId. We now fail closed — a
                // FAMILY_PORTAL token missing the clientId claim is rejected outright.
                UUID clientId = null;
                if ("FAMILY_PORTAL".equals(role)) {
                    String clientIdStr = claims.get("clientId", String.class); // read from already-parsed claims — no second HMAC parse
                    if (clientIdStr == null) {
                        // Malformed portal token: clientId claim missing — fail closed, do not authenticate
                        log.warn("FAMILY_PORTAL token missing clientId claim — rejecting authentication");
                        chain.doFilter(request, response);
                        return;
                    }
                    clientId = UUID.fromString(clientIdStr);
                }

                UserPrincipal principal = new UserPrincipal(userId, agencyId, role, clientId);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null,
                        principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
