package com.vemdancarjp.config;

import com.vemdancarjp.entity.AdminAccessStatus;
import com.vemdancarjp.repository.AdminAccessRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.function.Supplier;

@Component
public class AdminAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private final AdminAccessRepository repository;

    public AdminAccessAuthorizationManager(AdminAccessRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (!(authentication instanceof OAuth2AuthenticationToken oauth) || !authentication.isAuthenticated() ||
                !"google".equals(oauth.getAuthorizedClientRegistrationId()) ||
                !(oauth.getPrincipal() instanceof OidcUser oidcUser)) {
            return new AuthorizationDecision(false);
        }

        URL issuer = oidcUser.getIssuer();
        if (issuer == null || !GOOGLE_ISSUER.equals(issuer.toString())) {
            return new AuthorizationDecision(false);
        }

        String subject = oidcUser.getSubject();
        if (subject == null || subject.isBlank()) {
            return new AuthorizationDecision(false);
        }

        boolean approved = repository.findByGoogleSub(subject)
            .map(access -> access.getStatus() == AdminAccessStatus.APPROVED)
            .orElse(false);
        return new AuthorizationDecision(approved);
    }
}
