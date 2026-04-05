package com.hcare.api.v1.auth;

import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.domain.AgencyUser;
import com.hcare.domain.AgencyUserRepository;
import com.hcare.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;

@Service
public class AuthService {

    // BCrypt hash of a dummy password — used on the user-not-found path to normalize
    // response time and prevent email enumeration via timing differences.
    private static final String DUMMY_HASH =
        "$2a$10$dummyhashfortimingnormalizationXXXXXXXXXXXXXXXXXXXXXX";

    private final AgencyUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(AgencyUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        Optional<AgencyUser> userOpt = userRepository.findByEmail(request.email());

        if (userOpt.isEmpty()) {
            // Timing normalization: always run BCrypt to prevent email enumeration
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        AgencyUser user = userOpt.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = tokenProvider.generateToken(
            user.getId(), user.getAgencyId(), user.getRole().name());

        return new LoginResponse(token, user.getId(), user.getAgencyId(), user.getRole().name());
    }
}
