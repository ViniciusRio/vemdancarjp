package com.vemdancarjp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapIntegrationTest {

    private static final String SUBJECT = "bootstrapSubject123";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM admin_access");
    }

    @Test
    void emptyDatabaseCreatesApprovedSubject(CapturedOutput output) {
        bootstrap(SUBJECT).run(null);

        assertThat(rows()).containsExactly(Map.of("google_sub", SUBJECT, "status", "APPROVED"));
        assertThat(output).contains("Bootstrap admin applied").doesNotContain(SUBJECT);
    }

    @Test
    void pendingRequestOfSameSubjectIsApprovedWithoutDuplicate() {
        insert(SUBJECT, "PENDING");

        bootstrap(SUBJECT).run(null);

        assertThat(rows()).containsExactly(Map.of("google_sub", SUBJECT, "status", "APPROVED"));
    }

    @Test
    void existingApprovedAdminMakesVariableIgnored(CapturedOutput output) {
        insert("existingAdmin", "APPROVED");
        insert(SUBJECT, "PENDING");

        bootstrap(SUBJECT).run(null);

        assertThat(rows()).containsExactlyInAnyOrder(
            Map.of("google_sub", "existingAdmin", "status", "APPROVED"),
            Map.of("google_sub", SUBJECT, "status", "PENDING"));
        assertThat(output).contains("Bootstrap admin ignored").doesNotContain(SUBJECT);
    }

    @Test
    void emptyVariableDoesNothing() {
        insert("someone", "PENDING");

        bootstrap("").run(null);
        new AdminBootstrap(jdbcTemplate, transactionTemplate, null).run(null);

        assertThat(rows()).containsExactly(Map.of("google_sub", "someone", "status", "PENDING"));
    }

    @Test
    void invalidFormatFailsWithoutRevealingValueOrWriting() {
        for (String invalid : List.of("with space", " leading", "sub-with-dash", "ação", "a".repeat(256), "   ")) {
            assertThatThrownBy(() -> bootstrap(invalid))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(invalid));
        }
        assertThat(rows()).isEmpty();
    }

    @Test
    void invalidFormatPreventsApplicationStartup() {
        new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class))
            .withBean(AdminBootstrap.class)
            .withPropertyValues("APP_BOOTSTRAP_ADMIN_SUB=wrong value!")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageNotContaining("wrong value!")
                    .hasMessageContaining("APP_BOOTSTRAP_ADMIN_SUB");
            });
    }

    @Test
    void recoveryInDocumentedOrderApprovesCorrectedSubject() {
        bootstrap("wrongSubject").run(null);
        assertThat(rows()).containsExactly(Map.of("google_sub", "wrongSubject", "status", "APPROVED"));

        AdminBootstrap corrected = bootstrap(SUBJECT);
        jdbcTemplate.update("DELETE FROM admin_access WHERE status = 'APPROVED'");
        corrected.run(null);

        assertThat(rows()).containsExactly(Map.of("google_sub", SUBJECT, "status", "APPROVED"));
    }

    @Test
    void simultaneousStartupsWithDifferentSubjectsApproveExactlyOne() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 10; attempt++) {
                jdbcTemplate.update("DELETE FROM admin_access");
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> runs = List.of(
                    executor.submit(() -> { await(start); bootstrap("firstSubject").run(null); }),
                    executor.submit(() -> { await(start); bootstrap("secondSubject").run(null); }));
                start.countDown();
                for (Future<?> run : runs) {
                    run.get(30, TimeUnit.SECONDS);
                }
                List<Map<String, Object>> rows = rows();
                assertThat(rows).hasSize(1);
                assertThat(rows.get(0).get("status")).isEqualTo("APPROVED");
                assertThat(rows.get(0).get("google_sub")).isIn("firstSubject", "secondSubject");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private AdminBootstrap bootstrap(String subject) {
        return new AdminBootstrap(jdbcTemplate, transactionTemplate, subject);
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("SELECT google_sub, status FROM admin_access ORDER BY google_sub");
    }

    private void insert(String subject, String status) {
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, ?)",
            UUID.randomUUID(), subject, status);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
