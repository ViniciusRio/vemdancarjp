package com.vemdancarjp.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            @Value("${spring.security.oauth2.client.registration.google.client-secret:}") String clientSecret,
            @Value("${APP_FRONTEND_URL:}") String frontendUrl) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, exception) ->
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED");
        AccessDeniedHandler forbidden = (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated() ||
                        authentication instanceof AnonymousAuthenticationToken) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED");
                    return;
                }
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "CSRF_INVALID");
                return;
            }
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN");
        };

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/agenda").permitAll()
                .requestMatchers(HttpMethod.GET, "/oauth2/authorization/google", "/login/oauth2/code/google").permitAll()
                .requestMatchers("/api/admin/**").denyAll()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(unauthorized)
                .accessDeniedHandler(forbidden))
            .csrf(Customizer.withDefaults())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable);

        if (clientRegistrations.getIfAvailable() != null) {
            if (clientSecret.isBlank()) {
                throw new IllegalStateException("Google OAuth2 client secret must be configured");
            }
            URI target = URI.create(frontendUrl);
            if (!target.isAbsolute() || target.getHost() == null ||
                    !("http".equals(target.getScheme()) || "https".equals(target.getScheme())) ||
                    target.getUserInfo() != null || target.getFragment() != null) {
                throw new IllegalStateException("APP_FRONTEND_URL must be an absolute HTTP(S) URL");
            }
            http.oauth2Login(login -> login
                .successHandler((request, response, authentication) -> response.sendRedirect(target.toString()))
                .failureHandler((request, response, exception) ->
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTHENTICATION_FAILED")));
        }

        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }
}
