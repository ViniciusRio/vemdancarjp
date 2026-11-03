package com.vemdancarjp.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AdminAccessAuthorizationManager adminAccessAuthorizationManager,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            @Value("${spring.security.oauth2.client.registration.google.client-secret:}") String clientSecret,
            @Value("${APP_FRONTEND_URL:}") String frontendUrl) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            if (request.getRequestURI().startsWith("/api/auth/")) {
                response.setHeader("Cache-Control", "no-store");
            }
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED");
        };
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

        RequestMatcher logoutRoute = request -> "POST".equals(request.getMethod()) &&
            (request.getContextPath() + "/api/auth/logout").equals(request.getRequestURI());

        http
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/agenda").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/session").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/oauth2/authorization/google", "/login/oauth2/code/google").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/admin/events").access(adminAccessAuthorizationManager)
                .requestMatchers("/api/admin/**").denyAll()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(unauthorized)
                .accessDeniedHandler(forbidden))
            .cors(cors -> cors.configurationSource(agendaCorsSource(frontendUrl)))
            .csrf(Customizer.withDefaults())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(logout -> logout
                .logoutRequestMatcher(logoutRoute)
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setHeader("Cache-Control", "no-store");
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }))
            .requestCache(AbstractHttpConfigurer::disable);

        http.addFilterBefore(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {
                if (logoutRoute.matches(request)) {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication == null || !authentication.isAuthenticated() ||
                            authentication instanceof AnonymousAuthenticationToken) {
                        unauthorized.commence(request, response,
                            new InsufficientAuthenticationException("Authentication is required"));
                        return;
                    }
                }
                filterChain.doFilter(request, response);
            }
        }, LogoutFilter.class);

        if (clientRegistrations.getIfAvailable() != null) {
            if (clientSecret.isBlank()) {
                throw new IllegalStateException("Google OAuth2 client secret must be configured");
            }
            URI target = parseFrontendUrl(frontendUrl);
            http.oauth2Login(login -> login
                .successHandler((request, response, authentication) -> response.sendRedirect(target.toString()))
                .failureHandler((request, response, exception) ->
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTHENTICATION_FAILED")));
        }

        return http.build();
    }

    private static URI parseFrontendUrl(String frontendUrl) {
        URI target = URI.create(frontendUrl);
        if (!target.isAbsolute() || target.getHost() == null ||
                !("http".equals(target.getScheme()) || "https".equals(target.getScheme())) ||
                target.getUserInfo() != null || target.getFragment() != null) {
            throw new IllegalStateException("APP_FRONTEND_URL must be an absolute HTTP(S) URL");
        }
        return target;
    }

    private static CorsConfigurationSource agendaCorsSource(String frontendUrl) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (frontendUrl.isBlank()) {
            return source;
        }
        URI target = parseFrontendUrl(frontendUrl);
        String origin = target.getScheme() + "://" + target.getHost() +
            (target.getPort() == -1 ? "" : ":" + target.getPort());
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name()));
        configuration.setAllowCredentials(false);
        source.registerCorsConfiguration("/api/agenda", configuration);
        return source;
    }

    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }
}
