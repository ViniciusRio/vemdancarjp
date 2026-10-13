package com.vemdancarjp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "APP_FRONTEND_URL=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
class SecurityConfigIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;

    @Test
    void agendaRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/agenda"))
            .andExpect(status().isOk());
    }

    @Test
    void anonymousAdministrativeRequestReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/events"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void authenticatedAdministrativeRequestReturnsJsonForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/events").with(user("google-sub")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void anonymousUnsafeRequestReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/events"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void unsafeRequestWithoutCsrfReturnsJsonForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/events").with(user("google-sub")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("CSRF_INVALID"));
    }

    @Test
    void unsafeRequestWithCsrfStillCannotAccessAdminApi() throws Exception {
        mockMvc.perform(post("/api/admin/events").with(user("google-sub")).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void googleLoginStartsWithProviderRedirect() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", startsWith("https://accounts.google.com/")))
            .andExpect(header().string("Location", containsString("state=")))
            .andExpect(header().string("Location", containsString("scope=openid")))
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
    }
}
