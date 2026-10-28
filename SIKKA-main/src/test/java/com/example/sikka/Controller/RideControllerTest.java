package com.example.sikka.Controller;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.Ride;
import com.example.sikka.Service.RideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RideController.class)
@AutoConfigureMockMvc(addFilters = false)
class RideControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RideService rideService;

    @Test
    void getRidesTest() throws Exception {
        Ride ride = new Ride();
        ride.setName("Sky Coaster");
        when(rideService.getRides()).thenReturn(List.of(ride));

        mockMvc.perform(get("/api/v1/ride/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sky Coaster"));
    }

    @Test
    void getRideByIdNotFoundTest() throws Exception {
        when(rideService.getRideById(99)).thenThrow(new ApiException("ride not found"));

        mockMvc.perform(get("/api/v1/ride/get/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ride not found"));
    }

    @Test
    void addRideTest() throws Exception {
        mockMvc.perform(post("/api/v1/ride/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sky Coaster\",\"category\":\"THRILL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Ride added successfully"));

        verify(rideService).addRide(any(Ride.class));
    }

    @Test
    void addRideWithBlankNameTest() throws Exception {
        mockMvc.perform(post("/api/v1/ride/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ride name cannot be blank"));

        verify(rideService, never()).addRide(any());
    }
}