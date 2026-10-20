package com.vemdancarjp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemdancarjp.repository.AdminAccessRepository;
import com.vemdancarjp.repository.EventRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "APP_FRONTEND_URL=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(AdminEventAuthorizationFailureIntegrationTest.TestSessionConfiguration.class)
class AdminEventAuthorizationFailureIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired TestRestTemplate restTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired EventRepository eventRepository;
    @MockBean AdminAccessRepository adminAccessRepository;

    @BeforeEach
    void clearEvents() {
        eventRepository.deleteAllInBatch();
        when(adminAccessRepository.findByGoogleSub("database-failure"))
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));
    }

    @Test
    void repositoryFailurePropagatesThroughMockMvcWithoutCreatingEvent() {
        assertThatThrownBy(() -> mockMvc.perform(post("/api/admin/events")
                .with(authentication(googleAuthentication()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEvent())))
            .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void repositoryFailureReturnsServerErrorOverHttpWithoutCreatingEvent() throws Exception {
        ResponseEntity<Void> login = restTemplate.postForEntity("/test/session", null, Void.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotBlank();

        HttpHeaders csrfHeaders = new HttpHeaders();
        csrfHeaders.set(HttpHeaders.COOKIE, cookie.split(";", 2)[0]);
        ResponseEntity<String> csrfResponse = restTemplate.exchange("/api/auth/csrf", HttpMethod.GET,
            new HttpEntity<>(csrfHeaders), String.class);
        assertThat(csrfResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode csrfBody = objectMapper.readTree(csrfResponse.getBody());

        HttpHeaders createHeaders = new HttpHeaders();
        createHeaders.set(HttpHeaders.COOKIE, cookie.split(";", 2)[0]);
        createHeaders.set("X-CSRF-TOKEN", csrfBody.path("token").asText());
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> createResponse = restTemplate.exchange("/api/admin/events", HttpMethod.POST,
            new HttpEntity<>(validEvent(), createHeaders), String.class);

        assertThat(createResponse.getStatusCode().is5xxServerError())
            .withFailMessage("status=%s body=%s", createResponse.getStatusCode(), createResponse.getBody())
            .isTrue();
        assertThat(createResponse.getBody()).doesNotContain("database unavailable");
        assertThat(eventRepository.count()).isZero();
    }

    private static OAuth2AuthenticationToken googleAuthentication() {
        OidcIdToken token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("sub", "database-failure", "iss", "https://accounts.google.com"));
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "google");
    }

    private static String validEvent() {
        return """
            {"dayId":"monday","name":"Forró da Lua","venue":"Salão","neighborhood":"Centro","sortOrder":7}
            """;
    }

    @TestConfiguration
    static class TestSessionConfiguration {

        @Bean
        @Order(0)
        SecurityFilterChain testSessionSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/test/session")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .build();
        }

        @Bean
        TestSessionController testSessionController() {
            return new TestSessionController();
        }
    }

    @RestController
    static class TestSessionController {

        @PostMapping("/test/session")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void login(HttpServletRequest request, HttpServletResponse response) {
            request.getSession(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(googleAuthentication());
            new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        }
    }
}
