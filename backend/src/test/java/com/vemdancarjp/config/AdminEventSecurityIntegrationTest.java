package com.vemdancarjp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemdancarjp.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "APP_FRONTEND_URL=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
class AdminEventSecurityIntegrationTest {

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
    @Autowired EventRepository eventRepository;

    @BeforeEach
    void clearTables() {
        eventRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM admin_access");
    }

    @Test
    void approvedGoogleSessionCreatesEvent() throws Exception {
        approve("approved-subject");

        byte[] body = mockMvc.perform(post("/api/admin/events")
                .with(authentication(googleAuthentication("approved-subject")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEvent()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsByteArray();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.path("id").isIntegralNumber()).isTrue();
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void createdEventPersistsOptionalNullsAndAppearsOnPublicAgenda() throws Exception {
        approve("public-agenda-subject");

        byte[] body = mockMvc.perform(approvedRequest("public-agenda-subject"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsByteArray();
        long createdId = objectMapper.readTree(body).path("id").asLong();

        var persisted = eventRepository.findById(createdId).orElseThrow();
        assertThat(persisted.getDayId()).isEqualTo("monday");
        assertThat(persisted.getName()).isEqualTo("Forró da Lua");
        assertThat(persisted.getVenue()).isEqualTo("Salão");
        assertThat(persisted.getNeighborhood()).isEqualTo("Centro");
        assertThat(persisted.getInstagram()).isNull();
        assertThat(persisted.getFrequency()).isNull();
        assertThat(persisted.getSortOrder()).isEqualTo(7);

        byte[] agendaBody = mockMvc.perform(get("/api/agenda"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        JsonNode event = objectMapper.readTree(agendaBody).path("days").get(0).path("events").get(0);
        assertThat(event.path("name").asText()).isEqualTo("Forró da Lua");
        assertThat(event.path("instagram").isNull()).isTrue();
        assertThat(event.path("frequency").isNull()).isTrue();
        assertThat(event.size()).isEqualTo(5);
    }

    @Test
    void pendingAndMissingApprovalCannotCreateEvents() throws Exception {
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, 'PENDING')",
            UUID.randomUUID(), "pending-subject");

        mockMvc.perform(approvedRequest("pending-subject"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(approvedRequest("missing-subject"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void revocationBlocksTheNextRequestWithoutAnotherLogin() throws Exception {
        approve("revoked-subject");
        OAuth2AuthenticationToken authentication = googleAuthentication("revoked-subject");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        byte[] csrfBody = mockMvc.perform(get("/api/auth/csrf").session(session))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        String csrfToken = objectMapper.readTree(csrfBody).path("token").asText();
        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.accessStatus").value("APPROVED"));

        mockMvc.perform(post("/api/admin/events").session(session).header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON).content(validEvent()))
            .andExpect(status().isCreated());
        assertThat(eventRepository.count()).isEqualTo(1);
        jdbcTemplate.update("UPDATE admin_access SET status = 'PENDING' WHERE google_sub = ?", "revoked-subject");
        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.accessStatus").value("PENDING"));

        mockMvc.perform(post("/api/admin/events").session(session).header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON).content(validEvent()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        assertThat(session.isInvalid()).isFalse();
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void anonymousAndNonOidcSessionsCannotCreateEvents() throws Exception {
        mockMvc.perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON).content(validEvent()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/admin/events").with(user("legacy-user")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(validEvent()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void missingOrInvalidCsrfBlocksApprovedSession() throws Exception {
        approve("csrf-subject");

        mockMvc.perform(post("/api/admin/events").with(authentication(googleAuthentication("csrf-subject")))
                .contentType(MediaType.APPLICATION_JSON).content(validEvent()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("CSRF_INVALID"));
        mockMvc.perform(post("/api/admin/events").with(authentication(googleAuthentication("csrf-subject")))
                .with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content(validEvent()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("CSRF_INVALID"));
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void csrfTokenFromAnotherSessionCannotCreateEvent() throws Exception {
        approve("csrf-subject");
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();
        byte[] csrfBody = mockMvc.perform(get("/api/auth/csrf").session(firstSession))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        String token = objectMapper.readTree(csrfBody).path("token").asText();

        mockMvc.perform(post("/api/admin/events").session(secondSession)
                .with(authentication(googleAuthentication("csrf-subject")))
                .header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEvent()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("CSRF_INVALID"));
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void approvedSessionCannotUseOtherAdministrativeRoutes() throws Exception {
        approve("isolated-subject");

        mockMvc.perform(get("/api/admin/events").with(authentication(googleAuthentication("isolated-subject"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(post("/api/admin/other-venues")
                .with(authentication(googleAuthentication("isolated-subject"))).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(put("/api/admin/events/1")
                .with(authentication(googleAuthentication("isolated-subject"))).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(get("/api/agenda"))
            .andExpect(status().isOk());
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void directErrorRequestRemainsBlocked() throws Exception {
        approve("isolated-subject");

        mockMvc.perform(get("/error").with(authentication(googleAuthentication("isolated-subject"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void clientSuppliedIdIsRejectedWithoutPersistingEvent() throws Exception {
        approve("validation-subject");
        String body = validEvent().replace("}", ",\"id\":123}");

        mockMvc.perform(approvedRequest("validation-subject", body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        assertThat(eventRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void invalidInputReturnsValidationErrorWithoutPersistingEvent(String caseName, String body) throws Exception {
        approve("validation-subject");

        mockMvc.perform(approvedRequest("validation-subject", body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void trimsTextAndAcceptsFrontendInstagramUrl() throws Exception {
        approve("trim-subject");
        String body = """
            {"dayId":" monday ","name":" Forró da Lua ","venue":" Salão ","neighborhood":" Centro ","instagram":" https://www.instagram.com/forro ","frequency":" Semanal ","sortOrder":-2}
            """;

        mockMvc.perform(approvedRequest("trim-subject", body))
            .andExpect(status().isCreated());

        var event = eventRepository.findAll().get(0);
        assertThat(event.getDayId()).isEqualTo("monday");
        assertThat(event.getName()).isEqualTo("Forró da Lua");
        assertThat(event.getVenue()).isEqualTo("Salão");
        assertThat(event.getNeighborhood()).isEqualTo("Centro");
        assertThat(event.getInstagram()).isEqualTo("https://www.instagram.com/forro");
        assertThat(event.getFrequency()).isEqualTo("Semanal");
        assertThat(event.getSortOrder()).isEqualTo(-2);
    }

    @Test
    void optionalWhitespaceIsStoredAsNull() throws Exception {
        approve("optional-subject");
        String body = validEvent().replace("}", ",\"instagram\":\"   \",\"frequency\":\"   \"}");

        mockMvc.perform(approvedRequest("optional-subject", body))
            .andExpect(status().isCreated());

        var event = eventRepository.findAll().get(0);
        assertThat(event.getInstagram()).isNull();
        assertThat(event.getFrequency()).isNull();
    }

    @Test
    void acceptsAbsoluteHttpUrlAndExplicitNullFrequency() throws Exception {
        approve("http-url-subject");
        String body = validEvent().replace("}", ",\"instagram\":\"http://example.com/forro\",\"frequency\":null}");

        mockMvc.perform(approvedRequest("http-url-subject", body))
            .andExpect(status().isCreated());

        var event = eventRepository.findAll().get(0);
        assertThat(event.getInstagram()).isEqualTo("http://example.com/forro");
        assertThat(event.getFrequency()).isNull();
    }

    private static Stream<Arguments> invalidRequests() {
        String valid = validEvent();
        return Stream.of(
            Arguments.of("missing required name", """
                {"dayId":"monday","venue":"Salão","neighborhood":"Centro","sortOrder":7}
                """),
            Arguments.of("blank required name", valid.replace("Forró da Lua", "   ")),
            Arguments.of("invalid day", valid.replace("monday", "holiday")),
            Arguments.of("day longer than ten", valid.replace("monday", "wednesdayxx")),
            Arguments.of("sort order string", valid.replace("\"sortOrder\":7", "\"sortOrder\":\"7\"")),
            Arguments.of("sort order missing", valid.replace(",\"sortOrder\":7", "")),
            Arguments.of("sort order decimal", valid.replace("\"sortOrder\":7", "\"sortOrder\":7.5")),
            Arguments.of("sort order overflow", valid.replace("\"sortOrder\":7", "\"sortOrder\":2147483648")),
            Arguments.of("required text has wrong type", valid.replace("\"name\":\"Forró da Lua\"", "\"name\":12")),
            Arguments.of("optional text has wrong type", valid.replace("}", ",\"frequency\":12}")),
            Arguments.of("instagram username instead of url", valid.replace("}", ",\"instagram\":\"@forro\"}")),
            Arguments.of("instagram non http scheme", valid.replace("}", ",\"instagram\":\"javascript:alert(1)\"}")),
            Arguments.of("instagram relative url", valid.replace("}", ",\"instagram\":\"/forro\"}")),
            Arguments.of("instagram missing host", valid.replace("}", ",\"instagram\":\"https:///forro\"}")),
            Arguments.of("unknown role", valid.replace("}", ",\"role\":\"admin\"}")),
            Arguments.of("nul in text", valid.replace("Forró da Lua", "Forró" + "\\" + "u0000" + " da Lua")),
            Arguments.of("malformed json", "{\"dayId\":"),
            Arguments.of("non object body", "[]")
        );
    }

    private static MockHttpServletRequestBuilder approvedRequest(String subject) {
        return approvedRequest(subject, validEvent());
    }

    private static MockHttpServletRequestBuilder approvedRequest(String subject, String body) {
        return post("/api/admin/events")
            .with(authentication(googleAuthentication(subject)))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    private void approve(String subject) {
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, 'APPROVED')",
            UUID.randomUUID(), subject);
    }

    private static OAuth2AuthenticationToken googleAuthentication(String subject) {
        OidcIdToken token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("sub", subject, "iss", "https://accounts.google.com"));
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "google");
    }

    private static String validEvent() {
        return """
            {"dayId":"monday","name":"Forró da Lua","venue":"Salão","neighborhood":"Centro","sortOrder":7}
            """;
    }
}
