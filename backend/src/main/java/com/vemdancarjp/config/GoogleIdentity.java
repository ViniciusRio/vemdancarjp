package com.vemdancarjp.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.net.URL;
import java.util.Optional;

public final class GoogleIdentity {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private GoogleIdentity() {
    }

    public static Optional<OidcUser> from(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth) || !authentication.isAuthenticated() ||
                !"google".equals(oauth.getAuthorizedClientRegistrationId()) ||
                !(oauth.getPrincipal() instanceof OidcUser oidcUser)) {
            return Optional.empty();
        }
        URL issuer = oidcUser.getIssuer();
        if (issuer == null || !GOOGLE_ISSUER.equals(issuer.toString())) {
            return Optional.empty();
        }
        String subject = oidcUser.getSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(oidcUser);
    }
}
