package com.vemdancarjp.service;

import com.vemdancarjp.dto.response.AdminAccessResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminAccessService {

    public enum RequestOutcome { CREATED, EXISTING, EMAIL_NOT_VERIFIED }

    public record AccessRequestResult(RequestOutcome outcome, String accessStatus) {
    }

    public static final class ExecutorNotApprovedException extends RuntimeException {
    }

    public static final class CannotRemoveSelfException extends RuntimeException {
    }

    private static final String SELECT_ACCESS = "SELECT id, email, name, status, requested_at FROM admin_access";
    private static final RowMapper<AdminAccessResponse> ACCESS_ROW = (rs, rowNum) -> new AdminAccessResponse(
        rs.getObject("id", UUID.class),
        rs.getString("email"),
        rs.getString("name"),
        rs.getString("status"),
        rs.getTimestamp("requested_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public AdminAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AccessRequestResult request(String subject, String email, boolean emailVerified, String name) {
        Optional<String> existing = statusOf(subject);
        if (existing.isPresent()) {
            return new AccessRequestResult(RequestOutcome.EXISTING, existing.get());
        }
        if (email == null || email.isBlank() || !emailVerified) {
            return new AccessRequestResult(RequestOutcome.EMAIL_NOT_VERIFIED, null);
        }
        List<UUID> created = jdbcTemplate.queryForList("""
            INSERT INTO admin_access (id, google_sub, status, email, name)
            VALUES (gen_random_uuid(), ?, 'PENDING', ?, ?)
            ON CONFLICT (google_sub) DO NOTHING
            RETURNING id
            """, UUID.class, subject, email.strip(), blankToNull(name));
        if (!created.isEmpty()) {
            return new AccessRequestResult(RequestOutcome.CREATED, "PENDING");
        }
        return new AccessRequestResult(RequestOutcome.EXISTING, statusOf(subject).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<AdminAccessResponse> list() {
        return jdbcTemplate.query(SELECT_ACCESS +
            " ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, requested_at, id", ACCESS_ROW);
    }

    @Transactional
    public Optional<AdminAccessResponse> approve(String executorSubject, UUID id) {
        requireExecutorApproved(executorSubject, lockApproved());
        jdbcTemplate.update("UPDATE admin_access SET status = 'APPROVED' WHERE id = ? AND status = 'PENDING'", id);
        return jdbcTemplate.query(SELECT_ACCESS + " WHERE id = ?", ACCESS_ROW, id).stream().findFirst();
    }

    @Transactional
    public void remove(String executorSubject, UUID id) {
        List<String> targetSubject = jdbcTemplate.queryForList(
            "SELECT google_sub FROM admin_access WHERE id = ?", String.class, id);
        if (targetSubject.contains(executorSubject)) {
            throw new CannotRemoveSelfException();
        }
        List<String> approved = lockApproved();
        requireExecutorApproved(executorSubject, approved);
        List<Map<String, Object>> target = jdbcTemplate.queryForList(
            "SELECT google_sub, status FROM admin_access WHERE id = ?", id);
        if (target.isEmpty()) {
            return;
        }
        if ("APPROVED".equals(target.get(0).get("status")) && approved.size() <= 1) {
            throw new IllegalStateException("Removing this access would leave no approved admin");
        }
        jdbcTemplate.update("DELETE FROM admin_access WHERE id = ?", id);
    }

    private List<String> lockApproved() {
        return jdbcTemplate.queryForList(
            "SELECT google_sub FROM admin_access WHERE status = 'APPROVED' FOR UPDATE", String.class);
    }

    private static void requireExecutorApproved(String executorSubject, List<String> approved) {
        if (!approved.contains(executorSubject)) {
            throw new ExecutorNotApprovedException();
        }
    }

    private Optional<String> statusOf(String subject) {
        return jdbcTemplate.queryForList("SELECT status FROM admin_access WHERE google_sub = ?", String.class, subject)
            .stream().findFirst();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
