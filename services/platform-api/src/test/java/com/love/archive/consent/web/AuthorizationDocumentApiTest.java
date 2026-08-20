package com.love.archive.consent.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.testsupport.ApiIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 授权书完全公开：注册页的勾选框要能点开全文，而此时用户还没有账号。
 */
class AuthorizationDocumentApiTest extends ApiIntegrationTest {

    private static final String DOCUMENT_CODE = "PROFILE_LIVE_CONTENT";

    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void cleanState() {
        resetDatabase();
    }

    @Test
    void exposesTheActiveDocumentWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/v1/public/authorization-documents/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("v1.0"))
                .andExpect(jsonPath("$.data.contentSha256").isNotEmpty());
    }

    @Test
    void exposesASpecificActiveVersionWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/v1/public/authorization-documents/v1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("v1.0"));
    }

    @Test
    void rejectsUnknownVersion() throws Exception {
        mockMvc.perform(get("/api/v1/public/authorization-documents/v9.9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DOCUMENT_NOT_FOUND"));
    }

    @Test
    void refusesRetiredVersions() throws Exception {
        insertAuthorizationDocument("v0.9", "RETIRED");

        mockMvc.perform(get("/api/v1/public/authorization-documents/v0.9"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DOCUMENT_NOT_ACTIVE"));
    }

    private void insertAuthorizationDocument(String version, String status) throws SQLException {
        try (Connection owner = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement insert = owner.prepareStatement("""
                        INSERT INTO authorization_document
                            (document_code, version, title, content, content_sha256, status, effective_at)
                        VALUES (
                            ?, ?, '测试授权书', '测试授权书内容',
                            encode(digest(convert_to('测试授权书内容', 'UTF8'), 'sha256'), 'hex'),
                            ?, CURRENT_TIMESTAMP
                        )
                        """)) {
            insert.setString(1, DOCUMENT_CODE);
            insert.setString(2, version);
            insert.setString(3, status);
            insert.executeUpdate();
        }
    }
}
