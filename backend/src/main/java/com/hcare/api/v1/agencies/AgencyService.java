package com.hcare.api.v1.agencies;

import com.hcare.api.v1.agencies.dto.AgencyResponse;
import com.hcare.api.v1.agencies.dto.RegisterAgencyRequest;
import com.hcare.api.v1.agencies.dto.UpdateAgencyRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.domain.*;
import com.hcare.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AgencyService {

    private final AgencyRepository agencyRepository;
    private final AgencyUserRepository userRepository;
    private final FeatureFlagsRepository featureFlagsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AgencyService(AgencyRepository agencyRepository,
                         AgencyUserRepository userRepository,
                         FeatureFlagsRepository featureFlagsRepository,
                         PasswordEncoder passwordEncoder,
                         JwtTokenProvider tokenProvider) {
        this.agencyRepository = agencyRepository;
        this.userRepository = userRepository;
        this.featureFlagsRepository = featureFlagsRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public LoginResponse register(RegisterAgencyRequest req) {
        if (userRepository.findByEmail(req.adminEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        Agency agency = agencyRepository.save(new Agency(req.agencyName(), req.state()));
        AgencyUser admin = userRepository.save(new AgencyUser(
            agency.getId(), req.adminEmail(),
            passwordEncoder.encode(req.adminPassword()), UserRole.ADMIN));
        featureFlagsRepository.save(new FeatureFlags(agency.getId()));
        String token = tokenProvider.generateToken(admin.getId(), agency.getId(), UserRole.ADMIN.name());
        return new LoginResponse(token, admin.getId(), agency.getId(), UserRole.ADMIN.name());
    }

    @Transactional(readOnly = true)
    public AgencyResponse getAgency(UUID agencyId) {
        return AgencyResponse.from(agencyRepository.findById(agencyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found")));
    }

    @Transactional
    public AgencyResponse updateAgency(UUID agencyId, UpdateAgencyRequest req) {
        if (req.name() != null && req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency name cannot be blank");
        }
        Agency agency = agencyRepository.findById(agencyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found"));
        if (req.name() != null) agency.setName(req.name());
        if (req.state() != null) agency.setState(req.state());
        return AgencyResponse.from(agencyRepository.save(agency));
    }
}
