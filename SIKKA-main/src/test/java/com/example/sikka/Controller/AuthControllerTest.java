package com.example.sikka.Controller;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Service.VerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    VerificationService verificationService;

    @Test
    void verifyEmailTest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@sikka.test\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("email verified successfully"));

        verify(verificationService).verify("a@sikka.test", "123456");
    }

    @Test
    void verifyEmailWithBadCodeFormatTest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@sikka.test\",\"code\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("code must be 6 digits"));

        verifyNoInteractions(verificationService);
    }

    @Test
    void verifyEmailWithWrongCodeTest() throws Exception {
        doThrow(new ApiException("invalid or expired code"))
                .when(verificationService).verify("a@sikka.test", "000000");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@sikka.test\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid or expired code"));
    }
}