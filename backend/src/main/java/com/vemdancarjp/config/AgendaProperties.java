package com.vemdancarjp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agenda")
public record AgendaProperties(
    String city,
    String title,
    String subtitle
) {}
