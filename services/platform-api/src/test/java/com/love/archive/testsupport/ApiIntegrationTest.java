package com.love.archive.testsupport;

import com.love.archive.PlatformApiApplication;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.sql.DriverManager;
import java.sql.SQLException;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = PlatformApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class ApiIntegrationTest {

    private static final String OWNER_PASSWORD = "integration-owner-only";
    private static final String RUNTIME_PASSWORD = "integration-runtime-only";

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("marriage_archive")
                    .withUsername("archive_owner")
                    .withPassword(OWNER_PASSWORD)
                    .withInitScript("db/test-init/create-runtime-role.sql");

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
        registry.add("spring.datasource.username", () -> "archive_app");
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("app.identity.security.phone-encryption-key",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("app.identity.security.phone-search-key",
                () -> "//////////////////////////////////////////8=");
        registry.add("app.identity.security.argon2.memory-ki-b", () -> "1024");
        registry.add("app.identity.security.argon2.iterations", () -> "1");
        registry.add("app.sensitive-security.encryption-key",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("app.sensitive-security.hmac-key",
                () -> "//////////////////////////////////////////8=");
    }

    protected final void resetDatabase() {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE audit_log, profile_review_record, profile_revision_field_value,
                        profile_revision, profile_revision_photo, profile_photo,
                        profile_field_value, guest_profile, authorization_record,
                        activation_credential, payment_record, external_identity, user_account,
                        admin_user RESTART IDENTITY CASCADE
                    """);
            statement.execute("""
                    INSERT INTO authorization_document (
                        document_code, version, title, content, content_sha256, status, effective_at
                    ) VALUES (
                        'PAID_PROFILE_LIVE_CONTENT', 'v0.3', '付费建档与直播内容授权书',
                        '测试授权书内容',
                        encode(digest(convert_to('测试授权书内容', 'UTF8'), 'sha256'), 'hex'),
                        'ACTIVE', CURRENT_TIMESTAMP
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("测试数据库清理失败", exception);
        }
    }
}
