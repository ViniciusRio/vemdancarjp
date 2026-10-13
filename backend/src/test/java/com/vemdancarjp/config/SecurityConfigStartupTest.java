package com.vemdancarjp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigStartupTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            OAuth2ClientAutoConfiguration.class))
        .withUserConfiguration(SecurityConfig.class);

    @Test
    void completeConfigurationStarts() {
        contextRunner.withPropertyValues(
            "spring.security.oauth2.client.registration.google.client-id=test-client",
            "spring.security.oauth2.client.registration.google.client-secret=test-secret",
            "APP_FRONTEND_URL=http://localhost:5173"
        ).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void absentGoogleConfigurationStartsWithoutLogin() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class);
        });
    }

    @Test
    void missingGoogleClientIdDoesNotStartLogin() {
        contextRunner.withPropertyValues(
            "spring.security.oauth2.client.registration.google.client-secret=test-secret",
            "APP_FRONTEND_URL=http://localhost:5173"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingGoogleClientSecretDoesNotStartLogin() {
        contextRunner.withPropertyValues(
            "spring.security.oauth2.client.registration.google.client-id=test-client",
            "APP_FRONTEND_URL=http://localhost:5173"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void blankGoogleClientIdDoesNotStartLogin() {
        contextRunner.withPropertyValues(
            "spring.security.oauth2.client.registration.google.client-id=",
            "spring.security.oauth2.client.registration.google.client-secret=test-secret",
            "APP_FRONTEND_URL=http://localhost:5173"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void blankGoogleClientSecretDoesNotStartLogin() {
        contextRunner.withPropertyValues(
            "spring.security.oauth2.client.registration.google.client-id=test-client",
            "spring.security.oauth2.client.registration.google.client-secret=",
            "APP_FRONTEND_URL=http://localhost:5173"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingFrontendUrlDoesNotStartLogin() {
        contextRunner.withPropertyValues(
            "spring.security.oauth2.client.registration.google.client-id=test-client",
            "spring.security.oauth2.client.registration.google.client-secret=test-secret"
        ).run(context -> assertThat(context).hasFailed());
    }
}
