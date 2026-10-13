package com.vemdancarjp.repository;

import com.vemdancarjp.entity.AdminAccess;
import com.vemdancarjp.entity.AdminAccessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AdminAccessPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminAccessRepository adminAccessRepository;

    @Test
    void savesAndFindsAccessByGoogleSubject() {
        AdminAccess access = new AdminAccess();
        access.setGoogleSub("google-repository");
        access.setStatus(AdminAccessStatus.PENDING);
        access.setRequestedAt(Instant.parse("2026-01-01T00:00:00Z"));

        AdminAccess saved = adminAccessRepository.saveAndFlush(access);

        assertThat(saved.getId()).isNotNull();
        assertThat(adminAccessRepository.findByGoogleSub("google-repository"))
            .get()
            .extracting(AdminAccess::getStatus)
            .isEqualTo(AdminAccessStatus.PENDING);
    }

    @Test
    void acceptsBothStatusesAndOptionalProfileFields() {
        UUID pendingId = UUID.randomUUID();
        UUID approvedId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, ?)",
            pendingId, "google-pending", "PENDING");
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status, email, name) VALUES (?, ?, ?, ?, ?)",
            approvedId, "google-approved", "APPROVED", "shared@example.test", "Test User");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT requested_at IS NOT NULL FROM admin_access WHERE id = ?", Boolean.class, pendingId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM admin_access WHERE id = ?", String.class, approvedId)).isEqualTo("APPROVED");

        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status, email) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), "google-same-email", "PENDING", "shared@example.test");
    }

    @Test
    void rejectsDuplicateOrMissingGoogleSubject() {
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, ?)",
            UUID.randomUUID(), "google-duplicate", "PENDING");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, ?)",
            UUID.randomUUID(), "google-duplicate", "APPROVED"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, ?)",
            UUID.randomUUID(), null, "PENDING"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidOrMissingStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, ?)",
            UUID.randomUUID(), "google-invalid-status", "REJECTED"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, ?)",
            UUID.randomUUID(), "google-missing-status", null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingRequestedAt() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO admin_access (id, google_sub, status, requested_at) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), "google-missing-requested-at", "PENDING", null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
