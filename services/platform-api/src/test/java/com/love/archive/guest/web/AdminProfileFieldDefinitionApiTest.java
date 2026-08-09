package com.love.archive.guest.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.testsupport.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AdminProfileFieldDefinitionApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void cleanState() {
        resetDatabase();
    }

    @Test
    void definitionEndpointsRequireAdministratorLogin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profile-field-definitions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
        mockMvc.perform(post("/api/v1/admin/profile-field-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/admin/profile-field-definitions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
