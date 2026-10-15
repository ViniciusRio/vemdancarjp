package com.vemdancarjp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.security.oauth2.client.registration.google.client-id=test-client", "spring.security.oauth2.client.registration.google.client-secret=test-secret", "APP_FRONTEND_URL=http://localhost:5173"})
@AutoConfigureMockMvc
@Testcontainers
class AuthEndpointsIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @BeforeEach void cleanAccess() { jdbcTemplate.update("DELETE FROM admin_access"); }

    @Test void anonymousSessionIsUnauthorizedWithoutCaching() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test void sessionUsesSubjectAndFreshDatabaseStatusWithoutExposingProfile() throws Exception {
        MockHttpSession session = oidcSession("subject-a", "shared@example.test");
        insertAccess("subject-b", "APPROVED", "shared@example.test");
        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.accessStatus").value("NONE"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.sub").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_access", Integer.class)).isEqualTo(1);
        insertAccess("subject-a", "PENDING", "shared@example.test");
        mockMvc.perform(get("/api/auth/session").session(session)).andExpect(jsonPath("$.accessStatus").value("PENDING"));
        jdbcTemplate.update("UPDATE admin_access SET status = 'APPROVED' WHERE google_sub = 'subject-a'");
        mockMvc.perform(get("/api/auth/session").session(session)).andExpect(jsonPath("$.accessStatus").value("APPROVED"));
        jdbcTemplate.update("DELETE FROM admin_access WHERE google_sub = 'subject-a'");
        mockMvc.perform(get("/api/auth/session").session(session)).andExpect(jsonPath("$.accessStatus").value("NONE"));
    }

    @Test void nonOidcPrincipalIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/session").with(user("subject-a")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test void anonymousCanGetCsrfButCannotLogout() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", token(csrf)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
        assertThat(session.isInvalid()).isFalse();
    }

    @Test void logoutRequiresCsrfAndInvalidatesSession() throws Exception {
        MockHttpSession session = oidcSession("subject-a", "person@example.test");
        String token = token(mockMvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", "invalid"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("CSRF_INVALID"));
        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("CSRF_INVALID"));
        mockMvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", token))
            .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Location"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
        assertThat(session.isInvalid()).isTrue();
        String nextToken = token(mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn());
        assertThat(nextToken).isNotEqualTo(token);
    }

    @Test void invalidatedSessionCannotRestoreAuthenticatedResponse() throws Exception {
        MockHttpSession session = oidcSession("subject-a", "person@example.test");
        String token = token(mockMvc.perform(get("/api/auth/csrf").session(session)).andReturn());
        mockMvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.authenticated").doesNotExist())
            .andExpect(jsonPath("$.accessStatus").doesNotExist());
    }

    @Test void otherMethodsAndPathsDoNotLogout() throws Exception {
        MockHttpSession session = oidcSession("subject-a", "person@example.test");
        String token = token(mockMvc.perform(get("/api/auth/csrf").session(session)).andReturn());

        mockMvc.perform(get("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout/other").session(session).header("X-CSRF-TOKEN", token))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/logout").session(session).header("X-CSRF-TOKEN", token))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk());
    }

    @Test void approvedSessionCannotAccessAdminAndAgendaIsPublic() throws Exception {
        insertAccess("subject-a", "APPROVED", "person@example.test");
        mockMvc.perform(get("/api/admin/events").session(oidcSession("subject-a", "person@example.test")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda")).andExpect(status().isOk());
    }

    private MockHttpSession oidcSession(String sub, String email) {
        OidcIdToken idToken = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600), Map.of("sub", sub, "email", email));
        DefaultOidcUser principal = new DefaultOidcUser(List.of(), idToken);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, List.of(), "google"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
    private void insertAccess(String sub, String status, String email) {
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status, email) VALUES (?, ?, ?, ?)", UUID.randomUUID(), sub, status, email);
    }
    private String token(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("token").asText();
    }
}
