package com.vemdancarjp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemdancarjp.entity.Event;
import com.vemdancarjp.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "APP_FRONTEND_URL=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
class AdminEventListingIntegrationTest {

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
    void clearTables() {
        eventRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM admin_access");
        approve("approved-subject");
    }

    @Test
    void emptyListAndUnknownParameters() throws Exception {
        assertThat(list("/api/admin/events?ignored=x")).isEmpty();
        assertThat(list("/api/admin/events?dayId=friday&ignored=x")).isEmpty();
    }

    @Test
    void returnsExactlyEightFieldsWithNullsAndStoredTypes() throws Exception {
        Event stored = eventRepository.saveAndFlush(event("friday", "Baile", 7));
        JsonNode response = list("/api/admin/events");
        assertThat(response).hasSize(1);
        JsonNode item = response.get(0);
        assertThat(item.size()).isEqualTo(8);
        assertThat(item.properties().stream().map(Map.Entry::getKey).toList())
            .containsExactlyInAnyOrderElementsOf(Set.of("id", "dayId", "name", "venue", "neighborhood",
                "instagram", "frequency", "sortOrder"));
        assertThat(item.path("id").isIntegralNumber()).isTrue();
        assertThat(item.path("id").asLong()).isEqualTo(stored.getId());
        assertThat(item.path("dayId").asText()).isEqualTo("friday");
        assertThat(item.path("name").asText()).isEqualTo("Baile");
        assertThat(item.path("venue").asText()).isEqualTo("Salão");
        assertThat(item.path("neighborhood").asText()).isEqualTo("Centro");
        assertThat(item.path("instagram").isNull()).isTrue();
        assertThat(item.path("frequency").isNull()).isTrue();
        assertThat(item.path("sortOrder").isIntegralNumber()).isTrue();
        assertThat(item.path("sortOrder").asInt()).isEqualTo(7);
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void ordersWeekdaysAndSortOrderWithoutAssumingTieOrder() throws Exception {
        eventRepository.saveAndFlush(event("sunday", "Sunday", 1));
        eventRepository.saveAndFlush(event("tuesday", "High", 20));
        eventRepository.saveAndFlush(event("tuesday", "Tie A", 10));
        eventRepository.saveAndFlush(event("monday", "Monday", 100));
        eventRepository.saveAndFlush(event("tuesday", "Low", 5));
        eventRepository.saveAndFlush(event("tuesday", "Tie B", 10));
        eventRepository.saveAndFlush(event("friday", "Friday", 0));
        eventRepository.saveAndFlush(event("wednesday", "Wednesday", 0));
        eventRepository.saveAndFlush(event("thursday", "Thursday", 0));
        eventRepository.saveAndFlush(event("saturday", "Saturday", 0));

        JsonNode all = list("/api/admin/events?ignored=x");
        assertThat(all.findValuesAsText("name")).hasSize(10);
        assertThat(all.get(0).path("name").asText()).isEqualTo("Monday");
        assertThat(all.get(1).path("name").asText()).isEqualTo("Low");
        assertThat(List.of(all.get(2).path("name").asText(), all.get(3).path("name").asText()))
            .containsExactlyInAnyOrder("Tie A", "Tie B");
        assertThat(all.get(4).path("name").asText()).isEqualTo("High");
        assertThat(all.get(5).path("name").asText()).isEqualTo("Wednesday");
        assertThat(all.get(6).path("name").asText()).isEqualTo("Thursday");
        assertThat(all.get(7).path("name").asText()).isEqualTo("Friday");
        assertThat(all.get(8).path("name").asText()).isEqualTo("Saturday");
        assertThat(all.get(9).path("name").asText()).isEqualTo("Sunday");

        JsonNode friday = list("/api/admin/events?dayId=friday&ignored=x");
        assertThat(friday.findValuesAsText("name")).containsExactly("Friday");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dayId=", "dayId=Friday", "dayId=%20friday", "dayId=friday%20", "dayId=holiday", "dayId=friday&dayId=friday", "dayId=friday&dayId=monday"})
    void invalidDayDoesNotReadEvents(String query) throws Exception {
        eventRepository.saveAndFlush(event("friday", "Stored", 1));
        mockMvc.perform(get("/api/admin/events?" + query)
                .with(authentication(googleAuthentication("approved-subject"))))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .isEqualTo("{\"error\":\"VALIDATION_ERROR\"}"));
        assertThat(eventRepository.count()).isEqualTo(1);
        verify(eventRepository, never()).findAllByOrderBySortOrderAsc();
        verify(eventRepository, never()).findAllByDayIdOrderBySortOrderAsc(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void accessControlCsrfAndOtherRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/events"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .isEqualTo("{\"error\":\"UNAUTHENTICATED\"}"));
        mockMvc.perform(get("/api/admin/events").with(user("legacy-user")))
            .andExpect(status().isForbidden())
            .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/admin/events").with(authentication(googleAuthentication("missing"))))
            .andExpect(status().isForbidden())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .isEqualTo("{\"error\":\"FORBIDDEN\"}"));
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, 'PENDING')",
            UUID.randomUUID(), "pending");
        mockMvc.perform(get("/api/admin/events").with(authentication(googleAuthentication("pending"))))
            .andExpect(status().isForbidden());
        assertThat(list("/api/admin/events")).isEmpty();
        mockMvc.perform(post("/api/admin/events")
                .with(authentication(googleAuthentication("approved-subject")))
                .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/events")
                .with(authentication(googleAuthentication("approved-subject"))).with(csrf())
                .contentType("application/json")
                .content("{\"dayId\":\"monday\",\"name\":\"Forró\",\"venue\":\"Salão\",\"neighborhood\":\"Centro\",\"sortOrder\":1}"))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/api/admin/events")
                .with(authentication(googleAuthentication("approved-subject"))).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/other-venues")
                .with(authentication(googleAuthentication("approved-subject"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/events/")
                .with(authentication(googleAuthentication("approved-subject"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/error").with(authentication(googleAuthentication("approved-subject"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda")).andExpect(status().isOk());
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void revocationTakesEffectInSameSession() throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(googleAuthentication("approved-subject"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        mockMvc.perform(get("/api/admin/events").session(session))
            .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE admin_access SET status = 'PENDING' WHERE google_sub = ?", "approved-subject");
        mockMvc.perform(get("/api/admin/events").session(session))
            .andExpect(status().isForbidden())
            .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(session.isInvalid()).isFalse();
    }

    private JsonNode list(String url) throws Exception {
        byte[] body = mockMvc.perform(get(url).with(authentication(googleAuthentication("approved-subject"))))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsByteArray();
        return objectMapper.readTree(body);
    }

    private void approve(String subject) {
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, 'APPROVED')",
            UUID.randomUUID(), subject);
    }

    private static Event event(String dayId, String name, int order) {
        Event event = new Event();
        event.setDayId(dayId);
        event.setName(name);
        event.setVenue("Salão");
        event.setNeighborhood("Centro");
        event.setSortOrder(order);
        return event;
    }

    private static OAuth2AuthenticationToken googleAuthentication(String subject) {
        OidcIdToken token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("sub", subject, "iss", "https://accounts.google.com"));
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "google");
    }
}
