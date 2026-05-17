package com.example.mall.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need a real Postgres (for Flyway migrations, row locks, JSONB). Subclasses
 * must be annotated with {@code @SpringBootTest} and {@link Testcontainers @Testcontainers}.
 */
@Testcontainers
public abstract class PostgresBackedTest {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("mall")
                    .withUsername("mall")
                    .withPassword("mall")
                    .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Redis disabled — these tests don't exercise it
        r.add(
                "spring.autoconfigure.exclude",
                () ->
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        r.add(
                "mall.jwt.secret",
                () -> "test-secret-must-be-at-least-32-bytes-long-yes-it-is");
    }
}
