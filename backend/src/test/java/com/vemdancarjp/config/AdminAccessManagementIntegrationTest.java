package com.vemdancarjp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemdancarjp.service.AdminAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "APP_FRONTEND_URL=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
class AdminAccessManagementIntegrationTest {

    private static final String ADMIN = "adminSubject";
    private static final String OTHER_ADMIN = "otherAdminSubject";
    private static final String REQUESTER = "requesterSubject";
    private static final String ACCESS = "/api/admin/access";
    private static final String ACCESS_REQUEST = "/api/auth/access-request";
    private static final String APPROVE = "{\"status\":\"APPROVED\"}";
    private static final String GOOGLE = "https://accounts.google.com";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DataSource dataSource;
    @SpyBean AdminAccessService adminAccessService;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM admin_access");
        insert(ADMIN, "APPROVED", "admin@example.test", "Admin", Instant.parse("2026-01-01T00:00:00Z"));
        Mockito.clearInvocations(adminAccessService);
    }

    // 3.2 Pedido de acesso

    @Test
    void firstRequestCreatesPendingFromTokenClaims() throws Exception {
        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER))).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(noStore())
            .andExpect(content().json("{\"accessStatus\":\"PENDING\"}", true));

        assertThat(jdbcTemplate.queryForMap(
                "SELECT status, email, name FROM admin_access WHERE google_sub = ?", REQUESTER))
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                "status", "PENDING", "email", "requester@example.test", "name", "Pessoa Solicitante"));
    }

    @Test
    void requestWithoutVerifiedEmailIsRejectedWithoutCreating() throws Exception {
        List<Map<String, Object>> claimSets = new ArrayList<>();
        claimSets.add(Map.of("email_verified", true));
        claimSets.add(Map.of("email", "  ", "email_verified", true));
        claimSets.add(Map.of("email", "x@example.test", "email_verified", false));
        claimSets.add(Map.of("email", "x@example.test"));
        claimSets.add(Map.of("email", "x@example.test", "email_verified", "true"));
        for (Map<String, Object> claims : claimSets) {
            mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER, GOOGLE, claims))).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(content().json("{\"error\":\"EMAIL_NOT_VERIFIED\"}", true));
        }
        assertThat(count(REQUESTER)).isZero();
    }

    @Test
    void repeatedRequestAndAdminRequestKeepRecordUnchanged() throws Exception {
        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER))).with(csrf()))
            .andExpect(status().isCreated());
        Map<String, Object> before = record(REQUESTER);

        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER, GOOGLE, Map.of())))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(APPROVE))
            .andExpect(status().isOk())
            .andExpect(noStore())
            .andExpect(content().json("{\"accessStatus\":\"PENDING\"}", true));
        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\",\"email\":\"evil@example.test\",\"googleSub\":\"x\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"accessStatus\":\"PENDING\"}", true));
        assertThat(record(REQUESTER)).isEqualTo(before);

        Map<String, Object> adminBefore = record(ADMIN);
        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(ADMIN))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
            .andExpect(status().isOk())
            .andExpect(noStore())
            .andExpect(content().json("{\"accessStatus\":\"APPROVED\"}", true));
        assertThat(record(ADMIN)).isEqualTo(adminBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_access", Integer.class)).isEqualTo(2);
    }

    @Test
    void requestRequiresGoogleIdentityAndCsrf() throws Exception {
        mockMvc.perform(post(ACCESS_REQUEST).with(csrf()))
            .andExpect(status().isUnauthorized())
            .andExpect(noStore())
            .andExpect(content().json("{\"error\":\"UNAUTHENTICATED\"}", true));
        mockMvc.perform(post(ACCESS_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(noStore());
        mockMvc.perform(post(ACCESS_REQUEST).with(user("legacy-user")).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(noStore())
            .andExpect(content().json("{\"error\":\"FORBIDDEN\"}", true));
        mockMvc.perform(post(ACCESS_REQUEST)
                .with(authentication(google(REQUESTER, "https://issuer.example.test", verifiedClaims()))).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(noStore())
            .andExpect(content().json("{\"error\":\"FORBIDDEN\"}", true));
        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER))))
            .andExpect(status().isForbidden())
            .andExpect(noStore())
            .andExpect(content().json("{\"error\":\"CSRF_INVALID\"}", true));
        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER))).with(csrf().useInvalidToken()))
            .andExpect(status().isForbidden())
            .andExpect(noStore())
            .andExpect(content().json("{\"error\":\"CSRF_INVALID\"}", true));

        assertThat(count(REQUESTER)).isZero();
    }

    @Test
    void simultaneousRequestsOfSameSubjectCreateSingleRecord() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            jdbcTemplate.update("DELETE FROM admin_access WHERE google_sub = ?", REQUESTER);
            List<Integer> statuses = concurrently(4, () -> mockMvc.perform(
                    post(ACCESS_REQUEST).with(authentication(google(REQUESTER))).with(csrf()))
                .andReturn().getResponse().getStatus());
            assertThat(statuses).containsOnly(201, 200);
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(count(REQUESTER)).isEqualTo(1);
        }
    }

    // 3.3 Listagem

    @Test
    void listingOrdersPendingFirstWithExactFieldsAndNoSubject() throws Exception {
        insert("approvedLater", "APPROVED", "late@example.test", null, Instant.parse("2026-03-01T00:00:00Z"));
        insert("pendingLater", "PENDING", "p2@example.test", "P2", Instant.parse("2026-02-02T00:00:00Z"));
        insert("pendingEarlier", "PENDING", "p1@example.test", "P1", Instant.parse("2026-02-01T00:00:00Z"));

        MockHttpServletResponse response = mockMvc.perform(get(ACCESS).with(authentication(google(ADMIN))))
            .andExpect(status().isOk())
            .andExpect(noStore())
            .andReturn().getResponse();
        JsonNode listed = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(listed.findValuesAsText("email"))
            .containsExactly("p1@example.test", "p2@example.test", "admin@example.test", "late@example.test");
        assertThat(listed.findValuesAsText("status")).containsExactly("PENDING", "PENDING", "APPROVED", "APPROVED");
        for (JsonNode item : listed) {
            assertThat(fieldNames(item)).containsExactly("id", "email", "name", "status", "requestedAt");
        }
        assertThat(listed.get(0).path("requestedAt").asText()).isEqualTo("2026-02-01T00:00:00Z");
        assertThat(listed.get(3).path("name").isNull()).isTrue();
        assertThat(listed.get(0).path("id").asText()).isEqualTo(idOf("pendingEarlier").toString());
        assertThat(response.getContentAsString())
            .doesNotContain(ADMIN, "approvedLater", "pendingLater", "pendingEarlier", "googleSub", "google_sub");
    }

    // 3.4 Aprovação

    @Test
    void approvingPendingGrantsAccessOnNextRequest() throws Exception {
        insert(REQUESTER, "PENDING", "requester@example.test", "Pessoa", Instant.parse("2026-02-01T00:00:00Z"));
        UUID id = idOf(REQUESTER);
        MockHttpSession requesterSession = session(REQUESTER);
        mockMvc.perform(get("/api/admin/events").session(requesterSession)).andExpect(status().isForbidden());

        MockHttpServletResponse response = mockMvc.perform(approve(id, ADMIN))
            .andExpect(status().isOk())
            .andExpect(noStore())
            .andReturn().getResponse();
        JsonNode item = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(fieldNames(item)).containsExactly("id", "email", "name", "status", "requestedAt");
        assertThat(item.path("id").asText()).isEqualTo(id.toString());
        assertThat(item.path("status").asText()).isEqualTo("APPROVED");
        assertThat(item.path("email").asText()).isEqualTo("requester@example.test");
        assertThat(response.getContentAsString()).doesNotContain(REQUESTER);

        mockMvc.perform(get("/api/admin/events").session(requesterSession)).andExpect(status().isOk());

        Map<String, Object> before = record(REQUESTER);
        mockMvc.perform(approve(id, ADMIN))
            .andExpect(status().isOk())
            .andExpect(noStore())
            .andExpect(content().json("{\"status\":\"APPROVED\"}"));
        assertThat(record(REQUESTER)).isEqualTo(before);
    }

    @Test
    void approvingMissingRecordReturnsNotFound() throws Exception {
        mockMvc.perform(approve(UUID.randomUUID(), ADMIN))
            .andExpect(status().isNotFound())
            .andExpect(noStore())
            .andExpect(content().json("{\"error\":\"NOT_FOUND\"}", true));
    }

    @Test
    void invalidApprovalBodiesAreRejected() throws Exception {
        insert(REQUESTER, "PENDING", "requester@example.test", null, Instant.parse("2026-02-01T00:00:00Z"));
        UUID id = idOf(REQUESTER);
        for (String body : List.of("{\"status\":\"PENDING\"}", "{\"status\":\"REVOKED\"}", "{\"status\":\"approved\"}",
                "{\"status\":true}", "{\"status\":null}", "{}", "{\"status\":\"APPROVED\",\"email\":\"x@example.test\"}",
                "{\"status\":\"APPROVED\",\"googleSub\":\"x\"}", "[]", "\"APPROVED\"", "null", "{\"status\":")) {
            mockMvc.perform(put(ACCESS + "/" + id).with(authentication(google(ADMIN))).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(noStore())
                .andExpect(content().json("{\"error\":\"VALIDATION_ERROR\"}", true));
        }
        assertThat(record(REQUESTER).get("status")).isEqualTo("PENDING");
    }

    @Test
    void simultaneousApprovalsBothSucceedWithSingleRecord() throws Exception {
        insert(OTHER_ADMIN, "APPROVED", "other@example.test", null, Instant.parse("2026-01-02T00:00:00Z"));
        for (int attempt = 0; attempt < 5; attempt++) {
            jdbcTemplate.update("DELETE FROM admin_access WHERE google_sub = ?", REQUESTER);
            insert(REQUESTER, "PENDING", "requester@example.test", null, Instant.parse("2026-02-01T00:00:00Z"));
            UUID id = idOf(REQUESTER);
            List<String> admins = List.of(ADMIN, OTHER_ADMIN);
            List<Integer> statuses = concurrently(2, index -> mockMvc.perform(approve(id, admins.get(index)))
                .andReturn().getResponse().getStatus());
            assertThat(statuses).containsExactly(200, 200);
            assertThat(count(REQUESTER)).isEqualTo(1);
            assertThat(record(REQUESTER).get("status")).isEqualTo("APPROVED");
        }
    }

    // 3.5 Executor revogado no meio da operação

    @Test
    void executorRevokedWhileWaitingForLockGetsForbidden() throws Exception {
        insert(OTHER_ADMIN, "APPROVED", "other@example.test", null, Instant.parse("2026-01-02T00:00:00Z"));
        insert(REQUESTER, "PENDING", "requester@example.test", null, Instant.parse("2026-02-01T00:00:00Z"));
        insert("thirdAdmin", "APPROVED", "third@example.test", null, Instant.parse("2026-01-03T00:00:00Z"));
        UUID pending = idOf(REQUESTER);
        UUID third = idOf("thirdAdmin");
        UUID otherAdmin = idOf(OTHER_ADMIN);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (Statement statement = holder.createStatement()) {
                statement.executeQuery("SELECT id FROM admin_access WHERE status = 'APPROVED' FOR UPDATE").close();
                statement.executeUpdate("DELETE FROM admin_access WHERE google_sub = '" + OTHER_ADMIN + "'");
            }
            Future<MockHttpServletResponse> approval = executor.submit(() ->
                mockMvc.perform(approve(pending, OTHER_ADMIN)).andReturn().getResponse());
            Future<MockHttpServletResponse> removal = executor.submit(() ->
                mockMvc.perform(remove(third, OTHER_ADMIN)).andReturn().getResponse());
            Future<MockHttpServletResponse> selfRemoval = executor.submit(() ->
                mockMvc.perform(remove(otherAdmin, OTHER_ADMIN)).andReturn().getResponse());
            waitForLockWaiters(3);
            holder.commit();

            for (Future<MockHttpServletResponse> blocked : List.of(approval, removal, selfRemoval)) {
                MockHttpServletResponse response = blocked.get(30, TimeUnit.SECONDS);
                assertThat(response.getStatus()).isEqualTo(403);
                assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"FORBIDDEN\"}");
                assertThat(response.getHeaders("Cache-Control")).containsExactly("no-store");
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(record(REQUESTER).get("status")).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_access WHERE id = ?", Integer.class, third))
            .isEqualTo(1);
    }

    // 3.6 Remoção

    @Test
    void rejectingPendingAllowsNewRequest() throws Exception {
        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER))).with(csrf()))
            .andExpect(status().isCreated());
        UUID id = idOf(REQUESTER);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(remove(id, ADMIN))
                .andExpect(status().isNoContent())
                .andExpect(noStore())
                .andExpect(content().string(""));
        }
        assertThat(count(REQUESTER)).isZero();

        mockMvc.perform(post(ACCESS_REQUEST).with(authentication(google(REQUESTER))).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(content().json("{\"accessStatus\":\"PENDING\"}", true));
    }

    @Test
    void revokingAdminBlocksTheirActiveSession() throws Exception {
        insert(OTHER_ADMIN, "APPROVED", "other@example.test", null, Instant.parse("2026-01-02T00:00:00Z"));
        MockHttpSession otherSession = session(OTHER_ADMIN);
        mockMvc.perform(get(ACCESS).session(otherSession)).andExpect(status().isOk());

        mockMvc.perform(remove(idOf(OTHER_ADMIN), ADMIN)).andExpect(status().isNoContent());

        mockMvc.perform(get(ACCESS).session(otherSession))
            .andExpect(status().isForbidden())
            .andExpect(noStore())
            .andExpect(content().json("{\"error\":\"FORBIDDEN\"}", true));
        mockMvc.perform(get("/api/admin/events").session(otherSession)).andExpect(status().isForbidden());
    }

    @Test
    void removingSelfIsRejectedEvenAsOnlyAdmin() throws Exception {
        UUID self = idOf(ADMIN);
        mockMvc.perform(remove(self, ADMIN))
            .andExpect(status().isConflict())
            .andExpect(noStore())
            .andExpect(content().json("{\"error\":\"CANNOT_REMOVE_SELF\"}", true));
        assertThat(record(ADMIN).get("status")).isEqualTo("APPROVED");

        insert(OTHER_ADMIN, "APPROVED", "other@example.test", null, Instant.parse("2026-01-02T00:00:00Z"));
        mockMvc.perform(remove(self, ADMIN))
            .andExpect(status().isConflict())
            .andExpect(content().json("{\"error\":\"CANNOT_REMOVE_SELF\"}", true));
        assertThat(count(ADMIN)).isEqualTo(1);
    }

    @Test
    void crossRevocationLeavesExactlyOneAdmin() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            jdbcTemplate.update("DELETE FROM admin_access");
            insert(ADMIN, "APPROVED", "admin@example.test", null, Instant.parse("2026-01-01T00:00:00Z"));
            insert(OTHER_ADMIN, "APPROVED", "other@example.test", null, Instant.parse("2026-01-02T00:00:00Z"));
            UUID adminId = idOf(ADMIN);
            UUID otherId = idOf(OTHER_ADMIN);

            List<MockHttpServletResponse> responses = concurrently(2, index -> index == 0
                ? mockMvc.perform(remove(otherId, ADMIN)).andReturn().getResponse()
                : mockMvc.perform(remove(adminId, OTHER_ADMIN)).andReturn().getResponse());

            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(204, 403);
            MockHttpServletResponse loser = responses.stream().filter(r -> r.getStatus() == 403).findFirst().orElseThrow();
            assertThat(loser.getContentAsString()).isEqualTo("{\"error\":\"FORBIDDEN\"}");
            assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_access WHERE status = 'APPROVED'", Integer.class)).isEqualTo(1);
        }
    }

    // 3.7 Identificador inválido

    @Test
    void invalidIdentifierIsRejectedBeforeTouchingAccess() throws Exception {
        insert(REQUESTER, "PENDING", "requester@example.test", null, Instant.parse("2026-02-01T00:00:00Z"));
        String valid = idOf(REQUESTER).toString();
        Map<String, Object> before = record(REQUESTER);
        Mockito.clearInvocations(adminAccessService);

        List<String> invalid = List.of(
            valid.toUpperCase(),
            valid.replace("-", ""),
            "%" + Integer.toHexString(valid.charAt(0)) + valid.substring(1),
            "1",
            valid + "0",
            valid.substring(1),
            "{" + valid + "}");
        for (String rawId : invalid) {
            URI uri = URI.create(ACCESS + "/" + rawId.replace("{", "%7B").replace("}", "%7D"));
            mockMvc.perform(put(uri).with(authentication(google(ADMIN))).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                .andExpect(status().isBadRequest())
                .andExpect(noStore())
                .andExpect(content().json("{\"error\":\"VALIDATION_ERROR\"}", true));
            mockMvc.perform(delete(uri).with(authentication(google(ADMIN))).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(noStore())
                .andExpect(content().json("{\"error\":\"VALIDATION_ERROR\"}", true));
        }

        Mockito.verifyNoInteractions(adminAccessService);
        assertThat(record(REQUESTER)).isEqualTo(before);
    }

    // 3.8 Autorização e isolamento

    @Test
    void managementRoutesRequireApprovedGoogleSession() throws Exception {
        insert(REQUESTER, "PENDING", "requester@example.test", null, Instant.parse("2026-02-01T00:00:00Z"));
        UUID id = idOf(REQUESTER);
        List<Supplier<MockHttpServletRequestBuilder>> routes = List.of(
            () -> get(ACCESS),
            () -> put(ACCESS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(APPROVE),
            () -> delete(ACCESS + "/" + id));

        for (Supplier<MockHttpServletRequestBuilder> route : routes) {
            mockMvc.perform(route.get().with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(noStore())
                .andExpect(content().json("{\"error\":\"UNAUTHENTICATED\"}", true));
            mockMvc.perform(route.get().with(user("legacy-user")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(content().json("{\"error\":\"FORBIDDEN\"}", true));
            for (OAuth2AuthenticationToken identity : List.of(google("missingSubject"), google(REQUESTER),
                    google(ADMIN, "https://issuer.example.test", verifiedClaims()))) {
                mockMvc.perform(route.get().with(authentication(identity)).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(noStore())
                    .andExpect(content().json("{\"error\":\"FORBIDDEN\"}", true));
            }
        }

        for (Supplier<MockHttpServletRequestBuilder> write : routes.subList(1, 3)) {
            mockMvc.perform(write.get().with(authentication(google(ADMIN))))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(content().json("{\"error\":\"CSRF_INVALID\"}", true));
            mockMvc.perform(write.get().with(authentication(google(ADMIN))).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(content().json("{\"error\":\"CSRF_INVALID\"}", true));
        }

        Mockito.verifyNoInteractions(adminAccessService);
        MockHttpSession adminSession = session(ADMIN);
        mockMvc.perform(get(ACCESS).session(adminSession)).andExpect(status().isOk());
        Mockito.clearInvocations(adminAccessService);
        jdbcTemplate.update("UPDATE admin_access SET status = 'PENDING' WHERE google_sub = ?", ADMIN);
        mockMvc.perform(get(ACCESS).session(adminSession))
            .andExpect(status().isForbidden())
            .andExpect(noStore());
        mockMvc.perform(put(ACCESS + "/" + id).session(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
            .andExpect(status().isForbidden())
            .andExpect(noStore());
        mockMvc.perform(delete(ACCESS + "/" + id).session(adminSession).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(noStore());

        assertThat(record(REQUESTER).get("status")).isEqualTo("PENDING");
        Mockito.verifyNoInteractions(adminAccessService);
    }

    @Test
    void otherAccessRoutesStayBlocked() throws Exception {
        insert(REQUESTER, "PENDING", "requester@example.test", null, Instant.parse("2026-02-01T00:00:00Z"));
        UUID id = idOf(REQUESTER);
        List<MockHttpServletRequestBuilder> blocked = List.of(
            post(ACCESS),
            put(ACCESS),
            delete(ACCESS),
            get(ACCESS + "/" + id),
            patch(ACCESS + "/" + id),
            post(ACCESS + "/" + id),
            put(ACCESS + "/" + id + "/status"),
            delete(ACCESS + "/" + id + "/x"),
            get(ACCESS + "/" + id + "/x"),
            put(ACCESS + "/"),
            get("/api/admin/admins"));
        for (MockHttpServletRequestBuilder request : blocked) {
            mockMvc.perform(request.with(authentication(google(ADMIN))).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"FORBIDDEN\"}", true));
        }
        assertThat(record(REQUESTER).get("status")).isEqualTo("PENDING");
        Mockito.verifyNoInteractions(adminAccessService);
    }

    private MockHttpServletRequestBuilder approve(UUID id, String executor) {
        return put(ACCESS + "/" + id).with(authentication(google(executor))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(APPROVE);
    }

    private MockHttpServletRequestBuilder remove(UUID id, String executor) {
        return delete(ACCESS + "/" + id).with(authentication(google(executor))).with(csrf());
    }

    private static ResultMatcher noStore() {
        return result -> assertThat(result.getResponse().getHeaders("Cache-Control")).containsExactly("no-store");
    }

    private void waitForLockWaiters(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()",
                Integer.class);
            if (waiting != null && waiting >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Requests did not block on the approved admin lock");
    }

    private interface IndexedCall<T> {
        T call(int index) throws Exception;
    }

    private static <T> List<T> concurrently(int threads, Callable<T> call) throws Exception {
        return concurrently(threads, index -> call.call());
    }

    private static <T> List<T> concurrently(int threads, IndexedCall<T> call) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                int current = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call(current);
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void insert(String subject, String status, String email, String name, Instant requestedAt) {
        jdbcTemplate.update(
            "INSERT INTO admin_access (id, google_sub, status, email, name, requested_at) VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), subject, status, email, name, Timestamp.from(requestedAt));
    }

    private UUID idOf(String subject) {
        return jdbcTemplate.queryForObject("SELECT id FROM admin_access WHERE google_sub = ?", UUID.class, subject);
    }

    private int count(String subject) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM admin_access WHERE google_sub = ?",
            Integer.class, subject);
    }

    private Map<String, Object> record(String subject) {
        return jdbcTemplate.queryForMap("SELECT * FROM admin_access WHERE google_sub = ?", subject);
    }

    private static List<String> fieldNames(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).toList();
    }

    private static MockHttpSession session(String subject) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(google(subject));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private static Map<String, Object> verifiedClaims() {
        return Map.of("email", "requester@example.test", "email_verified", true, "name", "Pessoa Solicitante");
    }

    private static OAuth2AuthenticationToken google(String subject) {
        return google(subject, GOOGLE, verifiedClaims());
    }

    private static OAuth2AuthenticationToken google(String subject, String issuer, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", subject);
        claims.put("iss", issuer);
        OidcIdToken token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600), claims);
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "google");
    }
}
