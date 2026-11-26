package com.vemdancarjp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.regex.Pattern;

@Component
public class AdminBootstrap implements ApplicationRunner {

    static final long LOCK_KEY = 0x76646A5F61646D69L;
    private static final Pattern SUBJECT = Pattern.compile("[A-Za-z0-9]{1,255}");
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String subject;

    public AdminBootstrap(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                          @Value("${APP_BOOTSTRAP_ADMIN_SUB:}") String subject) {
        if (subject == null) {
            subject = "";
        }
        if (!subject.isEmpty() && !SUBJECT.matcher(subject).matches()) {
            throw new IllegalStateException(
                "APP_BOOTSTRAP_ADMIN_SUB must contain only letters and digits, up to 255 characters");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.subject = subject;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (subject.isEmpty()) {
            return;
        }
        boolean applied = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            jdbcTemplate.queryForList("SELECT pg_advisory_xact_lock(?)", LOCK_KEY);
            Boolean approvedExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM admin_access WHERE status = 'APPROVED')", Boolean.class);
            if (Boolean.TRUE.equals(approvedExists)) {
                return false;
            }
            jdbcTemplate.update("""
                INSERT INTO admin_access (id, google_sub, status)
                VALUES (gen_random_uuid(), ?, 'APPROVED')
                ON CONFLICT (google_sub) DO UPDATE SET status = 'APPROVED'
                """, subject);
            return true;
        }));
        log.info(applied ? "Bootstrap admin applied" : "Bootstrap admin ignored: an approved admin already exists");
    }
}
