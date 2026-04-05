package com.hcare.api.v1.users;

import com.hcare.domain.AgencyUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AgencyUserRepository userRepository;

    public UserController(AgencyUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // @Transactional ensures an outer transaction is open before the repository is called,
    // so TenantFilterAspect.@Before fires with the live Hibernate session already bound.
    // Without this, TenantFilterAspect and Spring Data's TransactionInterceptor both apply
    // to the repository proxy at the same @Order — execution order is undefined.
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<UUID>> listUsers() {
        List<UUID> ids = userRepository.findAll().stream()
            .map(u -> u.getId())
            .toList();
        return ResponseEntity.ok(ids);
    }
}
