package com.vemdancarjp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AdminAccessAuthorizationIntegrationTest {

    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminAccessAuthorizationManager policy;

    @Test
    void readsApprovalAndRevocationFromPostgresForEachEvaluation() {
        String subject = "subject-policy-integration";
        jdbcTemplate.update("DELETE FROM admin_access WHERE google_sub = ?", subject);
        Authentication authentication = oidcAuthentication(subject);

        assertThat(allowed(authentication)).isFalse();

        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, ?)",
            UUID.randomUUID(), subject, "APPROVED");
        assertThat(allowed(authentication)).isTrue();

        jdbcTemplate.update("UPDATE admin_access SET status = 'PENDING' WHERE google_sub = ?", subject);
        assertThat(allowed(authentication)).isFalse();

        jdbcTemplate.update("UPDATE admin_access SET status = 'APPROVED' WHERE google_sub = ?", subject);
        assertThat(allowed(authentication)).isTrue();

        jdbcTemplate.update("DELETE FROM admin_access WHERE google_sub = ?", subject);
        assertThat(allowed(authentication)).isFalse();
    }

    private boolean allowed(Authentication authentication) {
        return policy.check(() -> authentication,
            new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/api/admin/events")))
            .isGranted();
    }

    private static Authentication oidcAuthentication(String subject) {
        OidcIdToken token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("sub", subject, "iss", "https://accounts.google.com"));
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "google");
    }
}
