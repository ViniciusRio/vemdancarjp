package com.vemdancarjp.config;

import com.vemdancarjp.entity.AdminAccess;
import com.vemdancarjp.entity.AdminAccessStatus;
import com.vemdancarjp.repository.AdminAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessAuthorizationManagerTest {

    @Mock AdminAccessRepository repository;
    private AdminAccessAuthorizationManager policy;

    @BeforeEach
    void setUp() {
        policy = new AdminAccessAuthorizationManager(repository);
    }

    @Test
    void deniesAnonymousAndNonOidcAuthentication() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        Authentication user = new UsernamePasswordAuthenticationToken("subject-a", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(allowed(anonymous)).isFalse();
        assertThat(allowed(user)).isFalse();
    }

    @Test
    void grantsOnlyApprovedGoogleSubject() {
        Authentication google = oidcAuthentication("subject-a", "google", "https://accounts.google.com");
        when(repository.findByGoogleSub("subject-a")).thenReturn(Optional.empty());
        assertThat(allowed(google)).isFalse();

        when(repository.findByGoogleSub("subject-a"))
            .thenReturn(Optional.of(access(AdminAccessStatus.PENDING)));
        assertThat(allowed(google)).isFalse();

        when(repository.findByGoogleSub("subject-a"))
            .thenReturn(Optional.of(access(AdminAccessStatus.APPROVED)));
        assertThat(allowed(google)).isTrue();
    }

    @Test
    void rejectsOidcIdentityFromOtherRegistrationOrIssuer() {
        when(repository.findByGoogleSub("subject-a"))
            .thenReturn(Optional.of(access(AdminAccessStatus.APPROVED)));

        assertThat(allowed(oidcAuthentication("subject-a", "google", "https://accounts.google.com"))).isTrue();
        assertThat(allowed(oidcAuthentication("subject-a", "other", "https://accounts.google.com"))).isFalse();
        assertThat(allowed(oidcAuthentication("subject-a", "google", "https://other.example.test"))).isFalse();
    }

    @Test
    void rejectsGoogleRegistrationWithoutOidcPrincipalOrAuthenticatedSession() {
        Authentication oauthUser = new OAuth2AuthenticationToken(
            new DefaultOAuth2User(List.of(), Map.of("sub", "subject-a"), "sub"), List.of(), "google");
        OAuth2AuthenticationToken unauthenticated = (OAuth2AuthenticationToken) oidcAuthentication(
            "subject-a", "google", "https://accounts.google.com");
        unauthenticated.setAuthenticated(false);

        assertThat(allowed(oauthUser)).isFalse();
        assertThat(allowed(unauthenticated)).isFalse();
    }

    @Test
    void matchingEmailDoesNotAuthorizeDifferentSubject() {
        when(repository.findByGoogleSub("subject-a"))
            .thenReturn(Optional.of(access(AdminAccessStatus.APPROVED)));

        assertThat(allowed(oidcAuthentication("subject-a", "google", "https://accounts.google.com")))
            .isTrue();
        assertThat(allowed(oidcAuthentication("subject-b", "google", "https://accounts.google.com")))
            .isFalse();
    }

    @Test
    void databaseFailurePropagatesInsteadOfLookingLikeMissingApproval() {
        when(repository.findByGoogleSub("subject-a"))
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> allowed(oidcAuthentication("subject-a", "google", "https://accounts.google.com")))
            .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private boolean allowed(Authentication authentication) {
        return policy.check(() -> authentication,
            new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/api/admin/events")))
            .isGranted();
    }

    private static Authentication oidcAuthentication(String sub, String registrationId, String issuer) {
        OidcIdToken token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("sub", sub, "iss", issuer, "email", "shared@example.test"));
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), registrationId);
    }

    private static AdminAccess access(AdminAccessStatus status) {
        AdminAccess access = new AdminAccess();
        access.setStatus(status);
        return access;
    }
}
