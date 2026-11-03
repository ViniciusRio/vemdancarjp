package com.vemdancarjp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=0",
    "spring.datasource.hikari.idle-timeout=60000",
    "spring.datasource.hikari.keepalive-time=0"
})
@AutoConfigureMockMvc
@Testcontainers
class DatabaseReconnectionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final long HIKARI_VALIDATION_BYPASS_MILLIS = 500;

    @Autowired MockMvc mockMvc;

    @Test
    void firstRequestAfterConnectionsAreCutSucceeds() throws Exception {
        mockMvc.perform(get("/api/agenda")).andExpect(status().isOk());

        assertThat(terminateOtherConnections()).isPositive();
        Thread.sleep(HIKARI_VALIDATION_BYPASS_MILLIS + 500);

        mockMvc.perform(get("/api/agenda")).andExpect(status().isOk());
    }

    private int terminateOtherConnections() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 select count(pg_terminate_backend(pid))
                 from pg_stat_activity
                 where datname = current_database() and pid <> pg_backend_pid()
                 """)) {
            result.next();
            return result.getInt(1);
        }
    }
}
