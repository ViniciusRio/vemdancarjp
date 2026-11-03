package com.vemdancarjp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AgendaInitialSeedIntegrationTest {

    private static final Path SEED = Path.of("../deploy/seed/agenda-initial.sql");
    private static final Path SOURCE = Path.of("../public/agenda.json");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void emptyAgendaTables() {
        jdbcTemplate.execute("TRUNCATE events, variable_venues, other_venues");
    }

    @Test
    void loadsEmptyDatabaseWithTheSourceAgendaInOrder() throws Exception {
        runSeed();

        String body = mockMvc.perform(get("/api/agenda"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode agenda = objectMapper.readTree(body);
        JsonNode source = objectMapper.readTree(SOURCE.toFile());

        assertThat(agenda.get("days")).isEqualTo(source.get("days"));
        assertThat(agenda.get("variableVenues")).isEqualTo(source.get("variableVenues"));
        assertThat(agenda.get("otherVenues")).isEqualTo(source.get("otherVenues"));
    }

    @Test
    void leavesAdministrativeTablesEmpty() throws Exception {
        runSeed();

        assertThat(countOf("admin_access")).isZero();
        assertThat(countOf("admins")).isZero();
        assertThat(countOf("pending_admins")).isZero();
    }

    @Test
    void failsWithoutChangesWhenAnyAgendaTableHasRows() {
        jdbcTemplate.update("""
            INSERT INTO other_venues (name, neighborhood, sort_order) VALUES ('Existing', 'Centro', 0)
            """);

        assertThatThrownBy(this::runSeed)
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("not empty");

        assertThat(countOf("events")).isZero();
        assertThat(countOf("variable_venues")).isZero();
        assertThat(countOf("other_venues")).isEqualTo(1);
    }

    private int countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void runSeed() throws IOException, SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(SEED));
        }
    }
}
