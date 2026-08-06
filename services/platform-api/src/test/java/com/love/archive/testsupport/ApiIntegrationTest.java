package com.love.archive.testsupport;

import com.love.archive.PlatformApiApplication;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(
        classes = PlatformApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class ApiIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("marriage_archive")
                    .withUsername("archive_app")
                    .withPassword("integration-test-only");

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @BeforeAll
    static void verifyContainersAreRunning() {
        if (!POSTGRES.isRunning() || !REDIS.isRunning()) {
            throw new IllegalStateException("API integration containers did not start");
        }
    }

    @DynamicPropertySource
    static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("app.identity.security.phone-encryption-key",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("app.identity.security.phone-search-key",
                () -> "//////////////////////////////////////////8=");
        registry.add("app.identity.security.argon2.memory-ki-b", () -> "1024");
        registry.add("app.identity.security.argon2.iterations", () -> "1");
    }
}
