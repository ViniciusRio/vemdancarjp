package com.vemdancarjp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemdancarjp.entity.Event;
import com.vemdancarjp.entity.OtherVenue;
import com.vemdancarjp.entity.VariableVenue;
import com.vemdancarjp.repository.EventRepository;
import com.vemdancarjp.repository.OtherVenueRepository;
import com.vemdancarjp.repository.VariableVenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AgendaControllerIntegrationTest {

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
    @Autowired VariableVenueRepository variableVenueRepository;
    @Autowired OtherVenueRepository otherVenueRepository;

    @BeforeEach
    void clearAgendaTables() {
        eventRepository.deleteAllInBatch();
        variableVenueRepository.deleteAllInBatch();
        otherVenueRepository.deleteAllInBatch();
    }

    @Test
    void returnsOkWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/agenda"))
            .andExpect(status().isOk());
    }

    @Test
    void returnsSevenOrderedEmptyDaysWhenDatabaseIsEmpty() throws Exception {
        JsonNode agenda = getAgenda();

        assertThat(agenda.path("days").isArray()).isTrue();
        assertThat(agenda.path("days").size()).isEqualTo(7);
        assertThat(agenda.path("days").findValuesAsText("id"))
            .containsExactly("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");
        assertThat(agenda.path("days").findValuesAsText("label"))
            .containsExactly("Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo");
        agenda.path("days").forEach(day -> assertThat(day.path("events").isEmpty()).isTrue());
        assertThat(agenda.path("variableVenues").isEmpty()).isTrue();
        assertThat(agenda.path("otherVenues").isEmpty()).isTrue();
    }

    @Test
    void groupsPersistedEventsByDay() throws Exception {
        eventRepository.saveAndFlush(event("friday", "Baile do Ipê", 1));
        eventRepository.saveAndFlush(event("monday", "Forró da Lua", 2));

        JsonNode days = getAgenda().path("days");

        assertThat(days.get(0).path("events").findValuesAsText("name")).containsExactly("Forró da Lua");
        assertThat(days.get(4).path("events").findValuesAsText("name")).containsExactly("Baile do Ipê");
        assertThat(days.get(1).path("events").isEmpty()).isTrue();
    }

    @Test
    void ordersEventsWithinTheSameDayBySortOrder() throws Exception {
        eventRepository.saveAndFlush(event("tuesday", "Terceiro Baile", 30));
        eventRepository.saveAndFlush(event("tuesday", "Primeiro Baile", 10));
        eventRepository.saveAndFlush(event("tuesday", "Segundo Baile", 20));

        assertThat(getAgenda().path("days").get(1).path("events").findValuesAsText("name"))
            .containsExactly("Primeiro Baile", "Segundo Baile", "Terceiro Baile");
    }

    @Test
    void returnsBothVenueListsOrderedBySortOrder() throws Exception {
        variableVenueRepository.saveAndFlush(variableVenue("Casa do Cedro", 20));
        variableVenueRepository.saveAndFlush(variableVenue("Pátio do Sol", 10));
        otherVenueRepository.saveAndFlush(otherVenue("Salão da Praça", 20));
        otherVenueRepository.saveAndFlush(otherVenue("Espaço das Flores", 10));

        JsonNode agenda = getAgenda();

        assertThat(agenda.path("variableVenues").findValuesAsText("name"))
            .containsExactly("Pátio do Sol", "Casa do Cedro");
        assertThat(agenda.path("otherVenues").findValuesAsText("name"))
            .containsExactly("Espaço das Flores", "Salão da Praça");
    }

    @Test
    void returnsFrontendJsonContractWithExpectedTypes() throws Exception {
        Event event = event("monday", "Forró da Lua", 1);
        event.setInstagram(null);
        event.setFrequency(null);
        eventRepository.saveAndFlush(event);
        variableVenueRepository.saveAndFlush(variableVenue("Pátio do Sol", 1));
        otherVenueRepository.saveAndFlush(otherVenue("Espaço das Flores", 1));

        JsonNode agenda = getAgenda();
        JsonNode day = agenda.path("days").get(0);
        JsonNode eventJson = day.path("events").get(0);
        JsonNode variableVenue = agenda.path("variableVenues").get(0);
        JsonNode otherVenue = agenda.path("otherVenues").get(0);

        assertThat(agenda.size()).isEqualTo(7);
        assertThat(agenda.path("city").asText()).isEqualTo("João Pessoa, PB");
        assertThat(agenda.path("title").asText()).isEqualTo("Vem Dançar JP");
        assertThat(agenda.path("subtitle").isTextual()).isTrue();
        assertThat(agenda.path("lastUpdated").isTextual()).isTrue();
        assertThat(LocalDate.parse(agenda.path("lastUpdated").asText())).isNotNull();
        assertThat(day.size()).isEqualTo(3);
        assertThat(day.path("id").isTextual()).isTrue();
        assertThat(day.path("label").isTextual()).isTrue();
        assertThat(day.path("events").isArray()).isTrue();
        assertThat(eventJson.size()).isEqualTo(5);
        assertThat(eventJson.path("name").isTextual()).isTrue();
        assertThat(eventJson.path("venue").isTextual()).isTrue();
        assertThat(eventJson.path("neighborhood").isTextual()).isTrue();
        assertThat(eventJson.path("instagram").isNull()).isTrue();
        assertThat(eventJson.path("frequency").isNull()).isTrue();
        assertThat(variableVenue.size()).isEqualTo(4);
        assertThat(variableVenue.path("name").isTextual()).isTrue();
        assertThat(variableVenue.path("neighborhood").isTextual()).isTrue();
        assertThat(variableVenue.path("days").isTextual()).isTrue();
        assertThat(variableVenue.path("instagram").isTextual()).isTrue();
        assertThat(otherVenue.size()).isEqualTo(3);
        assertThat(otherVenue.path("name").isTextual()).isTrue();
        assertThat(otherVenue.path("neighborhood").isTextual()).isTrue();
        assertThat(otherVenue.path("instagram").isTextual()).isTrue();
    }

    @Test
    void postgresRejectsInvalidDayId() {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
            Integer.class
        )).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO events (day_id, name, venue, neighborhood, sort_order) VALUES (?, ?, ?, ?, ?)",
            "holiday", "Baile Imaginário", "Pátio Fictício", "Centro", 1
        )).isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("events_day_id_chk");

        assertThat(eventRepository.count()).isZero();
    }

    private JsonNode getAgenda() throws Exception {
        byte[] body = mockMvc.perform(get("/api/agenda"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        return objectMapper.readTree(body);
    }

    private Event event(String dayId, String name, int sortOrder) {
        Event event = new Event();
        event.setDayId(dayId);
        event.setName(name);
        event.setVenue("Salão Fictício");
        event.setNeighborhood("Centro");
        event.setInstagram("@bailesficticios");
        event.setFrequency("Semanal");
        event.setSortOrder(sortOrder);
        return event;
    }

    private VariableVenue variableVenue(String name, int sortOrder) {
        VariableVenue venue = new VariableVenue();
        venue.setName(name);
        venue.setNeighborhood("Centro");
        venue.setDays("Quartas e sextas");
        venue.setInstagram("@locaisficticios");
        venue.setSortOrder(sortOrder);
        return venue;
    }

    private OtherVenue otherVenue(String name, int sortOrder) {
        OtherVenue venue = new OtherVenue();
        venue.setName(name);
        venue.setNeighborhood("Centro");
        venue.setInstagram("@outrosficticios");
        venue.setSortOrder(sortOrder);
        return venue;
    }
}
