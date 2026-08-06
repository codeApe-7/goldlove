package com.love.archive.consent.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthorizationDocumentApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminUserMapper;
    @Autowired private GuestProvisioningService provisioningService;

    private long adminId;

    @BeforeEach
    void cleanState() {
        resetDatabase();
        resetAuthorizationDocuments();
        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("authorization-document-admin");
        admin.setDisplayName("Authorization Document Admin");
        admin.setPasswordHash("$argon2id$test-placeholder");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
    }

    @Test
    void exposesOnlyTheActiveDocumentPublicly() throws Exception {
        mockMvc.perform(get("/api/v1/public/authorization-documents/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("v0.3"))
                .andExpect(jsonPath("$.data.contentSha256").isNotEmpty());
    }

    @Test
    void requiresLoginForRetiredDocumentVersion() throws Exception {
        retireV03AndActivateV04();

        mockMvc.perform(get("/api/v1/guest/authorization-documents/v0.3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void letsGuestReadRetiredVersionReferencedByOwnPaidRecord() throws Exception {
        ProvisionedGuestView provisioned = provisioningService.provision(
                adminId,
                "13800138000",
                "PAY-RETIRED-DOCUMENT",
                199_00L,
                OffsetDateTime.now().minusMinutes(5),
                "v0.3",
                "线下付款",
                "retired-document-test");
        retireV03AndActivateV04();
        Cookie guestCookie = activateAndLogin(provisioned);

        mockMvc.perform(get("/api/v1/guest/authorization-documents/v0.3").cookie(guestCookie))
                .andExpect(status().isOk());
    }

    private Cookie activateAndLogin(ProvisionedGuestView provisioned) throws Exception {
        mockMvc.perform(post("/api/v1/guest/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800138000","initialCredential":"%s","newPassword":"New-password-2026"}
                                """.formatted(provisioned.initialCredential())))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800138000","password":"New-password-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookies()[0];
    }

    private void retireV03AndActivateV04() throws SQLException {
        try (Connection owner = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement retire = owner.prepareStatement("""
                        UPDATE authorization_document
                        SET status = 'RETIRED'
                        WHERE document_code = 'PAID_PROFILE_LIVE_CONTENT' AND version = 'v0.3'
                        """)) {
            retire.executeUpdate();
        }
        insertAuthorizationDocument("v0.4", "ACTIVE");
    }

    private void resetAuthorizationDocuments() {
        try (Connection owner = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement delete = owner.prepareStatement("""
                        DELETE FROM authorization_document
                        WHERE document_code = 'PAID_PROFILE_LIVE_CONTENT' AND version <> 'v0.3'
                        """);
                PreparedStatement activate = owner.prepareStatement("""
                        UPDATE authorization_document
                        SET status = 'ACTIVE'
                        WHERE document_code = 'PAID_PROFILE_LIVE_CONTENT' AND version = 'v0.3'
                        """)) {
            delete.executeUpdate();
            activate.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("测试授权文档重置失败", exception);
        }
    }

    private void insertAuthorizationDocument(String version, String status) throws SQLException {
        try (Connection owner = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement insert = owner.prepareStatement("""
                        INSERT INTO authorization_document
                            (document_code, version, title, content, content_sha256, status, effective_at)
                        VALUES (
                            'PAID_PROFILE_LIVE_CONTENT', ?, '测试授权书', '测试授权书内容',
                            encode(digest(convert_to('测试授权书内容', 'UTF8'), 'sha256'), 'hex'),
                            ?, CURRENT_TIMESTAMP
                        )
                        """)) {
            insert.setString(1, version);
            insert.setString(2, status);
            insert.executeUpdate();
        }
    }
}
