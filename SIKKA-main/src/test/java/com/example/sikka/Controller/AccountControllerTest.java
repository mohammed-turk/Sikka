package com.example.sikka.Controller;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.RegisterDtoIn;
import com.example.sikka.Service.AccountService;
import com.example.sikka.Service.VisitorProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AccountService accountService;
    @MockitoBean
    VisitorProfileService visitorProfileService;

    @Test
    void registerTest() throws Exception {
        mockMvc.perform(post("/api/v1/account/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"email\":\"a@sikka.test\",\"password\":\"Passw0rd123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("registered successfully"));

        verify(accountService).register(any(RegisterDtoIn.class));
    }

    @Test
    void registerWithShortPasswordTest() throws Exception {
        mockMvc.perform(post("/api/v1/account/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"email\":\"a@sikka.test\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must be at least 8 characters"));

        verifyNoInteractions(accountService);
    }

    @Test
    void registerWithDuplicateEmailTest() throws Exception {
        doThrow(new ApiException("email already exists")).when(accountService).register(any(RegisterDtoIn.class));

        mockMvc.perform(post("/api/v1/account/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"email\":\"a@sikka.test\",\"password\":\"Passw0rd123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email already exists"));
    }
}