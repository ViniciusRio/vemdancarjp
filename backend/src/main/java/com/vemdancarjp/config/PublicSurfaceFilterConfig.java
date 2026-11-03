package com.vemdancarjp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "APP_PUBLIC_SURFACE", havingValue = "agenda-only")
public class PublicSurfaceFilterConfig {

    @Bean
    FilterRegistrationBean<PublicSurfaceFilter> publicSurfaceFilter() {
        FilterRegistrationBean<PublicSurfaceFilter> registration =
            new FilterRegistrationBean<>(new PublicSurfaceFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static class PublicSurfaceFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            if ("GET".equals(request.getMethod()) && "/api/agenda".equals(request.getRequestURI())) {
                chain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
