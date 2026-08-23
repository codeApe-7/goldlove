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
        registry.add("app.identity.security.argon2.memory-ki-b", () -> "1024");
        registry.add("app.identity.security.argon2.iterations", () -> "1");
    }

    /**
     * 清空 Redis。限流计数与会话都在 Redis 里，且不随数据库 TRUNCATE 一起消失，
     * 复用同一手机号的用例必须显式重置，否则会互相撞上限流阈值。
     */
    protected final void resetRateLimits(org.springframework.data.redis.core.StringRedisTemplate redis) {
        var connectionFactory = redis.getConnectionFactory();
        if (connectionFactory == null) {
            throw new IllegalStateException("Redis 连接工厂不可用");
        }
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    /**
     * 走公开注册接口建一个访客并返回会话令牌。注册免费且无前置条件，
     * 所以这是所有访客用例最短的准备路径。
     */
    protected final String registerGuest(
            org.springframework.test.web.servlet.MockMvc mockMvc,
            String phone,
            String password) throws Exception {
        var result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/guest/auth/register")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {"phone":"%s","password":"%s","confirmPassword":"%s",
                                         "acceptedAuthorization":true}
                                        """.formatted(phone, password, password)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }

    protected final void resetDatabase() {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE audit_log, profile_photo, profile_field_value,
                        guest_profile, authorization_record, activation_code,
                        payment_order, payment_record, payment_setting,
                        course_video_upload, course, course_collection, user_account,
                        admin_user RESTART IDENTITY CASCADE
                    """);
            // 授权书由迁移种下，TRUNCATE 没有清它；这里只保证测试拿到确定的一版。
            statement.execute("DELETE FROM authorization_document");
            statement.execute("""
                    INSERT INTO authorization_document (
                        document_code, version, title, content, content_sha256, status, effective_at
                    ) VALUES (
                        'PROFILE_LIVE_CONTENT', 'v1.0', '档案与直播内容授权书',
                        '测试授权书内容',
                        encode(digest(convert_to('测试授权书内容', 'UTF8'), 'sha256'), 'hex'),
                        'ACTIVE', CURRENT_TIMESTAMP
                    )
                    """);
            // 合集是 V6 种下的，上面那句 TRUNCATE ... RESTART IDENTITY 会把它们连 id 一起清掉。
            // 课程相关用例都要挂在某个合集下，所以这里重新种一遍，
            // 并且因为 id 已重置，第一个合集的 id 稳定是 1，用例可以直接依赖这一点。
            statement.execute("""
                    INSERT INTO course_collection (name, description, sort_order) VALUES
                        ('情绪与认知', '识别情绪与认知偏差，先把自己看清楚', 10),
                        ('择偶与筛选', '把标准说清楚，把人看明白', 20),
                        ('恋爱关系', '关系里的相处、沟通与经营', 30),
                        ('形象与状态', '外在形象与内在状态的日常管理', 40)
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("测试数据库清理失败", exception);
        }
    }
}
