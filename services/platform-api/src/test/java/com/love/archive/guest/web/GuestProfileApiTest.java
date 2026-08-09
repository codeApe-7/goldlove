package com.love.archive.guest.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.testsupport.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class GuestProfileApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void cleanState() {
        resetDatabase();
    }

    @Test
    void guestProfileEndpointsRequireGuestLogin() throws Exception {
        mockMvc.perform(get("/api/v1/guest/profile/draft"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
        mockMvc.perform(get("/api/v1/guest/profile/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
        mockMvc.perform(put("/api/v1/guest/profile/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
    }
}
