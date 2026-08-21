package com.love.archive.guest.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.ProfileFieldType;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class GuestProfileFieldDefinitionsApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private ProfileFieldDefinitionMapper definitionMapper;
    @Autowired private StringRedisTemplate redis;

    private long adminId;
    private String guestToken;

    @BeforeEach
    void prepareGuest() throws Exception {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        adminId = insertAdmin();
        guestToken = registerGuest(mockMvc, "13800138000", "Guest-fields-2026");
    }

    @Test
    void requiresGuestLogin() throws Exception {
        mockMvc.perform(get("/api/v1/guest/profile/field-definitions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsEnabledDefinitionsSortedAndExcludesDisabled() throws Exception {
        ProfileFieldDefinitionEntity disabled = new ProfileFieldDefinitionEntity();
        disabled.setFieldCode("hidden_field");
        disabled.setLabel("隐藏字段");
        disabled.setStorageKind(FieldStorageKind.DYNAMIC);
        disabled.setDataType(ProfileFieldType.TEXT);
        disabled.setRequired(false);
        disabled.setEnabled(false);
        disabled.setSortOrder(5);
        disabled.setVersion(0L);
        OffsetDateTime now = OffsetDateTime.now();
        disabled.setCreatedAt(now);
        disabled.setUpdatedAt(now);
        definitionMapper.insert(disabled);

        mockMvc.perform(get("/api/v1/guest/profile/field-definitions")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fieldCode").value("gender"))
                .andExpect(jsonPath("$.data[0].dataType").value("SINGLE_OPTION"))
                .andExpect(jsonPath("$.data[0].required").value(true))
                .andExpect(jsonPath("$.data[0].options[0]").value("男"))
                .andExpect(jsonPath("$.data.length()").value(11))
                .andExpect(jsonPath("$.data[?(@.fieldCode == 'wechat_id')].label")
                        .value("微信号"))
                .andExpect(jsonPath("$.data[?(@.fieldCode == 'wechat_id')].required")
                        .value(true))
                .andExpect(jsonPath("$.data[?(@.fieldCode == 'douyin_id')].required")
                        .value(false))
                .andExpect(jsonPath("$.data[?(@.fieldCode == 'douyin_nickname')].required")
                        .value(false))
                .andExpect(jsonPath("$.data[?(@.fieldCode == 'douyin_profile_url')].required")
                        .value(false))
                .andExpect(jsonPath("$.data[?(@.fieldCode == 'hidden_field')]").isEmpty());
    }

    private long insertAdmin() {
        char[] password = "guest-fields-2026".toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("guest-fields-admin");
        admin.setDisplayName("Guest Fields Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        return admin.getId();
    }

}
