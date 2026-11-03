package com.vemdancarjp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.Socket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PUBLIC_SURFACE=agenda-only",
        "APP_FRONTEND_URL=https://vemdancarjp.com.br"
    })
@Testcontainers
class PublicSurfaceFilterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void agendaIsReadable() throws IOException {
        assertThat(statusOf("GET", "/api/agenda")).isEqualTo(200);
    }

    @Test
    void agendaKeepsCorsForTheSite() throws IOException {
        String response = rawResponse("GET", "/api/agenda", "Origin: https://vemdancarjp.com.br\r\n");

        assertThat(response).startsWith("HTTP/1.1 200");
        assertThat(response).containsIgnoringCase("Access-Control-Allow-Origin: https://vemdancarjp.com.br");
    }

    @ParameterizedTest
    @CsvSource({
        "GET, /api/admin/events",
        "POST, /api/admin/events",
        "GET, /api/auth/session",
        "GET, /api/auth/csrf",
        "GET, /oauth2/authorization/google",
        "GET, /login/oauth2/code/google",
        "GET, /actuator/health",
        "GET, /"
    })
    void everyOtherRouteIsNotFound(String method, String path) throws IOException {
        assertThat(statusOf(method, path)).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"})
    void otherMethodsOnAgendaAreNotFound(String method) throws IOException {
        assertThat(statusOf(method, "/api/agenda")).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/agenda/",
        "/api/agenda;x=1",
        "/api/agenda/../admin/events",
        "/api/%61genda",
        "/api/agenda/extra"
    })
    void pathVariationsAreNotFound(String path) throws IOException {
        assertThat(statusOf("GET", path)).isEqualTo(404);
    }

    @Test
    void rejectedWriteChangesNothing() throws IOException {
        Integer before = jdbcTemplate.queryForObject("select count(*) from events", Integer.class);

        assertThat(statusOf("POST", "/api/admin/events")).isEqualTo(404);

        assertThat(jdbcTemplate.queryForObject("select count(*) from events", Integer.class)).isEqualTo(before);
    }

    private int statusOf(String method, String rawPath) throws IOException {
        String statusLine = rawResponse(method, rawPath, "").lines().findFirst().orElseThrow();
        return Integer.parseInt(statusLine.split(" ")[1]);
    }

    private String rawResponse(String method, String rawPath, String extraHeaders) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            String request = method + " " + rawPath + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 0\r\n"
                + extraHeaders
                + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
