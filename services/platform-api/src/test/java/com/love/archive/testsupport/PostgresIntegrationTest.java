package com.love.archive.testsupport;

import com.love.archive.PlatformApiApplication;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test")
@SpringBootTest(classes = PlatformApiApplication.class)
public abstract class PostgresIntegrationTest {

    private static final String OWNER_PASSWORD = "integration-owner-only";
    private static final String RUNTIME_PASSWORD = "integration-runtime-only";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("marriage_archive")
                    .withUsername("archive_owner")
                    .withPassword(OWNER_PASSWORD)
                    .withInitScript("db/test-init/create-runtime-role.sql");

    static {
        POSTGRES.start();
    }

    @BeforeAll
    static void verifyContainerIsRunning() {
        if (!POSTGRES.isRunning()) {
            throw new IllegalStateException("PostgreSQL test container did not start");
        }
    }

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "archive_app");
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }
}
