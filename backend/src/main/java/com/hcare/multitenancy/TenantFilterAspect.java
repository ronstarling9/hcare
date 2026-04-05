package com.hcare.multitenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    // This aspect requires an active @Transactional session to be open on the current thread
    // before it fires. Ensure all authenticated repository calls originate from @Transactional
    // service or controller methods. See UserController for the reference pattern.
    @Before("@within(org.springframework.stereotype.Repository)")
    public void enableAgencyFilter() {
        UUID agencyId = TenantContext.get();
        if (agencyId != null) {
            entityManager.unwrap(Session.class)
                .enableFilter("agencyFilter")
                .setParameter("agencyId", agencyId);
        }
    }
}
