package com.hcare.api.v1.users;

import com.hcare.api.v1.users.dto.InviteUserRequest;
import com.hcare.api.v1.users.dto.UpdateUserRoleRequest;
import com.hcare.api.v1.users.dto.UserResponse;
import com.hcare.domain.*;
import com.hcare.multitenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final AgencyUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AgencyUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findByAgencyId(TenantContext.get()).stream()
            .map(UserResponse::from)
            .toList();
    }

    @Transactional
    public UserResponse inviteUser(InviteUserRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        AgencyUser user = userRepository.save(new AgencyUser(
            TenantContext.get(), req.email(),
            passwordEncoder.encode(req.temporaryPassword()), req.role()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUserRole(UUID userId, UpdateUserRoleRequest req) {
        AgencyUser user = requireUser(userId);
        user.setRole(req.role());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(UUID userId) {
        UUID agencyId = TenantContext.get();
        AgencyUser user = requireUser(userId);
        if (userRepository.countByAgencyIdAndRole(agencyId, UserRole.ADMIN) <= 1
                && user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot delete the last ADMIN user");
        }
        userRepository.delete(user);
    }

    private AgencyUser requireUser(UUID userId) {
        // Hibernate agencyFilter (TenantFilterAspect) scopes findById to the current tenant.
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
