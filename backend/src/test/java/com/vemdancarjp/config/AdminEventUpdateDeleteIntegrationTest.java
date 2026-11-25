package com.vemdancarjp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemdancarjp.entity.Event;
import com.vemdancarjp.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "APP_FRONTEND_URL=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
class AdminEventUpdateDeleteIntegrationTest {

    private static final String APPROVED = "approved-subject";

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
    @SpyBean EventRepository eventRepository;

    @BeforeEach
    void reset() {
        eventRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM admin_access");
        approve(APPROVED);
        Mockito.clearInvocations(eventRepository);
    }

    @Test
    void updateReplacesAllFieldsAndReturnsFullEvent() throws Exception {
        Event stored = eventRepository.saveAndFlush(event("monday", "Antigo", 1, "https://instagram.com/a", "Semanal"));

        JsonNode body = json(mockMvc.perform(approved(put(item(stored.getId())), """
                {"dayId":" friday ","name":" Forró Novo ","venue":"Bar","neighborhood":"Bessa",
                 "instagram":"https://instagram.com/novo","frequency":"Quinzenal","sortOrder":9}
                """))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsByteArray());

        assertThat(body.properties().stream().map(Map.Entry::getKey).toList())
            .containsExactlyInAnyOrderElementsOf(Set.of("id", "dayId", "name", "venue", "neighborhood",
                "instagram", "frequency", "sortOrder"));
        assertThat(body.path("id").asLong()).isEqualTo(stored.getId());
        assertThat(body.path("dayId").asText()).isEqualTo("friday");
        assertThat(body.path("name").asText()).isEqualTo("Forró Novo");
        assertThat(body.path("sortOrder").asInt()).isEqualTo(9);

        Event reloaded = eventRepository.findById(stored.getId()).orElseThrow();
        assertThat(reloaded.getDayId()).isEqualTo("friday");
        assertThat(reloaded.getName()).isEqualTo("Forró Novo");
        assertThat(reloaded.getVenue()).isEqualTo("Bar");
        assertThat(reloaded.getNeighborhood()).isEqualTo("Bessa");
        assertThat(reloaded.getInstagram()).isEqualTo("https://instagram.com/novo");
        assertThat(reloaded.getFrequency()).isEqualTo("Quinzenal");
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void updateClearsOmittedNullAndBlankOptionals() throws Exception {
        Event first = eventRepository.saveAndFlush(event("monday", "A", 1, "https://instagram.com/a", "Semanal"));
        Event second = eventRepository.saveAndFlush(event("monday", "B", 2, "https://instagram.com/b", "Semanal"));

        mockMvc.perform(approved(put(item(first.getId())), validBody()))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"instagram\":null,\"frequency\":null}"));
        mockMvc.perform(approved(put(item(second.getId())),
                validBody().replace("\"sortOrder\"", "\"instagram\":null,\"frequency\":\"   \",\"sortOrder\"")))
            .andExpect(status().isOk());

        for (Long id : List.of(first.getId(), second.getId())) {
            Event reloaded = eventRepository.findById(id).orElseThrow();
            assertThat(reloaded.getInstagram()).isNull();
            assertThat(reloaded.getFrequency()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"dayId\":\"monday\",\"name\":\"X\",\"venue\":\"Y\",\"neighborhood\":\"Z\",\"sortOrder\":1,\"id\":5}",
        "{\"dayId\":\"holiday\",\"name\":\"X\",\"venue\":\"Y\",\"neighborhood\":\"Z\",\"sortOrder\":1}",
        "{\"dayId\":\"monday\",\"name\":\"  \",\"venue\":\"Y\",\"neighborhood\":\"Z\",\"sortOrder\":1}",
        "{\"dayId\":\"monday\",\"name\":\"X\",\"venue\":\"Y\",\"neighborhood\":\"Z\",\"sortOrder\":1.5}",
        "{\"dayId\":\"monday\",\"name\":\"X\",\"venue\":\"Y\",\"neighborhood\":\"Z\",\"sortOrder\":1,\"instagram\":\"@perfil\"}",
        "{\"dayId\":\"monday\",",
        "[]"
    })
    void invalidUpdateBodyDoesNotChangeEvent(String body) throws Exception {
        Event stored = eventRepository.saveAndFlush(event("monday", "Original", 1, null, null));
        Mockito.clearInvocations(eventRepository);

        mockMvc.perform(approved(put(item(stored.getId())), body))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));

        assertThat(eventRepository.findById(stored.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void deleteRemovesOnlyTargetAndRepeatsWith204() throws Exception {
        Event target = eventRepository.saveAndFlush(event("monday", "Alvo", 1, null, null));
        Event other = eventRepository.saveAndFlush(event("monday", "Outro", 2, null, null));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(approved(delete(item(target.getId())), null))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        }
        mockMvc.perform(approved(delete(item(other.getId() + 1000)), null))
            .andExpect(status().isNoContent());

        assertThat(eventRepository.findById(target.getId())).isEmpty();
        assertThat(eventRepository.findById(other.getId())).isPresent();
    }

    @Test
    void updateOfMissingEventReturns404WithoutCreating() throws Exception {
        mockMvc.perform(approved(put(item(999_999L)), validBody()))
            .andExpect(status().isNotFound())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string("{\"error\":\"NOT_FOUND\"}"));
        assertThat(eventRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "007", "+5", "1.0", "9223372036854775808", "99999999999999999999"})
    void invalidIdIsRejectedBeforeTouchingEvents(String id) throws Exception {
        eventRepository.saveAndFlush(event("monday", "Stored", 1, null, null));
        Mockito.clearInvocations(eventRepository);

        mockMvc.perform(approved(put("/api/admin/events/" + id), validBody()))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));
        mockMvc.perform(approved(delete("/api/admin/events/" + id), null))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));

        Mockito.verifyNoInteractions(eventRepository);
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%31", "%2B1", "1%20", "%201", "0x1"})
    void encodedOrNonLiteralIdIsRejectedWithoutTouchingEvents(String rawId) throws Exception {
        Event stored = eventRepository.saveAndFlush(event("monday", "Original", 1, null, null));
        URI uri = URI.create("/api/admin/events/" + rawId);
        Mockito.clearInvocations(eventRepository);

        mockMvc.perform(approved(put(uri), validBody()))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));
        mockMvc.perform(approved(delete(uri), null))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));

        Mockito.verifyNoInteractions(eventRepository);
        assertThat(eventRepository.findById(stored.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1;x=1", "1%2F2", "1/"})
    void pathTricksOnItemRouteAreNotAccepted(String rawPath) throws Exception {
        Event stored = eventRepository.saveAndFlush(event("monday", "Original", 1, null, null));
        URI uri = URI.create("/api/admin/events/" + rawPath.replace("1", stored.getId().toString()));

        for (MockHttpServletRequestBuilder request : List.of(put(uri), delete(uri))) {
            int status = mockMvc.perform(withBody(request, validBody())
                    .with(authentication(googleAuthentication(APPROVED))).with(csrf()))
                .andReturn().getResponse().getStatus();
            assertThat(status).isBetween(400, 499);
        }
        assertThat(eventRepository.findById(stored.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void authorizationAndCsrfForBothRoutes() throws Exception {
        Event stored = eventRepository.saveAndFlush(event("monday", "Original", 1, null, null));
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, 'PENDING')",
            UUID.randomUUID(), "pending");

        List<Supplier<MockHttpServletRequestBuilder>> routes = List.of(
            () -> put(item(stored.getId())), () -> delete(item(stored.getId())));
        for (Supplier<MockHttpServletRequestBuilder> base : routes) {
            mockMvc.perform(withBody(base.get(), validBody()).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("{\"error\":\"UNAUTHENTICATED\"}"));
            mockMvc.perform(withBody(base.get(), validBody()).with(user("legacy-user")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("{\"error\":\"FORBIDDEN\"}"));
            for (String subject : List.of("missing", "pending")) {
                mockMvc.perform(withBody(base.get(), validBody())
                        .with(authentication(googleAuthentication(subject))).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string("{\"error\":\"FORBIDDEN\"}"));
            }
            mockMvc.perform(withBody(base.get(), validBody()).with(authentication(googleAuthentication(APPROVED))))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("{\"error\":\"CSRF_INVALID\"}"));
            mockMvc.perform(withBody(base.get(), validBody())
                    .with(authentication(googleAuthentication(APPROVED))).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"error\":\"CSRF_INVALID\"}"));
        }

        Event reloaded = eventRepository.findById(stored.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Original");
    }

    @Test
    void csrfTokenFromAnotherSessionIsRejected() throws Exception {
        Event stored = eventRepository.saveAndFlush(event("monday", "Original", 1, null, null));
        MockHttpSession owner = approvedSession();
        MockHttpSession other = approvedSession();
        String otherToken = objectMapper.readTree(mockMvc.perform(get("/api/auth/csrf").session(other))
            .andReturn().getResponse().getContentAsByteArray()).path("token").asText();

        mockMvc.perform(put(item(stored.getId())).session(owner).header("X-CSRF-TOKEN", otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isForbidden())
            .andExpect(content().string("{\"error\":\"CSRF_INVALID\"}"));
        mockMvc.perform(delete(item(stored.getId())).session(owner).header("X-CSRF-TOKEN", otherToken))
            .andExpect(status().isForbidden())
            .andExpect(content().string("{\"error\":\"CSRF_INVALID\"}"));
        assertThat(eventRepository.findById(stored.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void revocationTakesEffectInSameSession() throws Exception {
        Event first = eventRepository.saveAndFlush(event("monday", "A", 1, null, null));
        Event second = eventRepository.saveAndFlush(event("monday", "B", 2, null, null));
        MockHttpSession session = approvedSession();

        mockMvc.perform(put(item(first.getId())).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE admin_access SET status = 'PENDING' WHERE google_sub = ?", APPROVED);

        mockMvc.perform(put(item(second.getId())).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isForbidden())
            .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(delete(item(second.getId())).session(session).with(csrf()))
            .andExpect(status().isForbidden());
        assertThat(eventRepository.findById(second.getId()).orElseThrow().getName()).isEqualTo("B");
    }

    @Test
    void otherAdministrativeRoutesStayBlocked() throws Exception {
        Event stored = eventRepository.saveAndFlush(event("monday", "Original", 1, null, null));
        String id = stored.getId().toString();

        List<MockHttpServletRequestBuilder> blocked = List.of(
            get("/api/admin/events/" + id),
            patch("/api/admin/events/" + id),
            put("/api/admin/events"),
            delete("/api/admin/events"),
            put("/api/admin/events/"),
            delete("/api/admin/events/"),
            put("/api/admin/events/" + id + "/x"),
            delete("/api/admin/events/" + id + "/x"),
            delete("/api/admin/other-venues/" + id));
        for (MockHttpServletRequestBuilder request : blocked) {
            mockMvc.perform(withBody(request, validBody())
                    .with(authentication(googleAuthentication(APPROVED))).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"error\":\"FORBIDDEN\"}"));
        }
        mockMvc.perform(get("/api/agenda")).andExpect(status().isOk());
        assertThat(eventRepository.findById(stored.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void publicAgendaReflectsUpdateAndDelete() throws Exception {
        Event moved = eventRepository.saveAndFlush(event("monday", "Muda", 1, null, null));
        Event removed = eventRepository.saveAndFlush(event("monday", "Sai", 2, null, null));
        eventRepository.saveAndFlush(event("friday", "Antes", 1, null, null));
        eventRepository.saveAndFlush(event("friday", "Depois", 10, null, null));

        mockMvc.perform(approved(put(item(moved.getId())), """
                {"dayId":"friday","name":"Muda","venue":"Salão","neighborhood":"Centro","sortOrder":5}
                """))
            .andExpect(status().isOk());
        mockMvc.perform(approved(delete(item(removed.getId())), null))
            .andExpect(status().isNoContent());

        JsonNode days = json(mockMvc.perform(get("/api/agenda"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray()).path("days");
        JsonNode monday = dayById(days, "monday");
        JsonNode friday = dayById(days, "friday");
        assertThat(monday.path("events").findValuesAsText("name")).isEmpty();
        assertThat(friday.path("events").findValuesAsText("name")).containsExactly("Antes", "Muda", "Depois");
        assertThat(days.findValuesAsText("name")).doesNotContain("Sai");
    }

    private MockHttpServletRequestBuilder approved(MockHttpServletRequestBuilder request, String body) {
        return withBody(request, body).with(authentication(googleAuthentication(APPROVED))).with(csrf());
    }

    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body) {
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpSession approvedSession() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(googleAuthentication(APPROVED));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private JsonNode json(byte[] body) throws Exception {
        return objectMapper.readTree(body);
    }

    private static JsonNode dayById(JsonNode days, String id) {
        for (JsonNode day : days) {
            if (id.equals(day.path("id").asText())) {
                return day;
            }
        }
        throw new AssertionError("day not found: " + id);
    }

    private static String item(Long id) {
        return "/api/admin/events/" + id;
    }

    private static String validBody() {
        return "{\"dayId\":\"tuesday\",\"name\":\"Editado\",\"venue\":\"Salão\",\"neighborhood\":\"Centro\",\"sortOrder\":3}";
    }

    private void approve(String subject) {
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, 'APPROVED')",
            UUID.randomUUID(), subject);
    }

    private static Event event(String dayId, String name, int order, String instagram, String frequency) {
        Event event = new Event();
        event.setDayId(dayId);
        event.setName(name);
        event.setVenue("Salão");
        event.setNeighborhood("Centro");
        event.setInstagram(instagram);
        event.setFrequency(frequency);
        event.setSortOrder(order);
        return event;
    }

    private static OAuth2AuthenticationToken googleAuthentication(String subject) {
        OidcIdToken token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("sub", subject, "iss", "https://accounts.google.com"));
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "google");
    }
}
