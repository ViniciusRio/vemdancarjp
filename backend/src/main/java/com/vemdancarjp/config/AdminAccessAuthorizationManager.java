package com.vemdancarjp.config;

import com.vemdancarjp.entity.AdminAccessStatus;
import com.vemdancarjp.repository.AdminAccessRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class AdminAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AdminAccessRepository repository;

    public AdminAccessAuthorizationManager(AdminAccessRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        boolean approved = GoogleIdentity.from(authenticationSupplier.get())
            .flatMap(oidcUser -> repository.findByGoogleSub(oidcUser.getSubject()))
            .map(access -> access.getStatus() == AdminAccessStatus.APPROVED)
            .orElse(false);
        return new AuthorizationDecision(approved);
    }
}
