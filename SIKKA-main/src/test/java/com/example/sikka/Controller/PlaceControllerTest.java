package com.example.sikka.Controller;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.Place;
import com.example.sikka.Service.PlaceService;
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

@WebMvcTest(PlaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlaceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PlaceService placeService;

    @Test
    void getPlacesTest() throws Exception {
        Place place = new Place();
        place.setName("Cafe");
        when(placeService.getPlaces()).thenReturn(List.of(place));

        mockMvc.perform(get("/api/v1/place/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cafe"));
    }

    @Test
    void getPlaceByIdNotFoundTest() throws Exception {
        when(placeService.getPlaceById(5)).thenThrow(new ApiException("place not found"));

        mockMvc.perform(get("/api/v1/place/get/5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("place not found"));
    }

    @Test
    void addPlaceWithBlankNameTest() throws Exception {
        mockMvc.perform(post("/api/v1/place/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Place name cannot be blank"));

        verify(placeService, never()).addPlace(any());
    }

    @Test
    void deletePlaceTest() throws Exception {
        mockMvc.perform(delete("/api/v1/place/delete/5"))
                .andExpect(status().isOk());

        verify(placeService).deletePlace(5);
    }
}