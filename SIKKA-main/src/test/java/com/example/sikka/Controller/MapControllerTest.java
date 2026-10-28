package com.example.sikka.Controller;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoOut.MapRouteDtoOut;
import com.example.sikka.Service.MapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MapController.class)
@AutoConfigureMockMvc(addFilters = false)
class MapControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MapService mapService;

    @Test
    void getMapRidesTest() throws Exception {
        when(mapService.getMapRides()).thenReturn(new JsonMapper().readTree("[{\"id\":\"r1\"}]"));

        mockMvc.perform(get("/api/v1/map/get/rides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"));
    }

    @Test
    void getRouteTest() throws Exception {
        MapRouteDtoOut route = new MapRouteDtoOut();
        route.setDistanceMeters(120.0);
        when(mapService.getRoute("RIDE", 1, "PLACE", 2)).thenReturn(route);

        mockMvc.perform(get("/api/v1/map/route/RIDE/1/PLACE/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceMeters").value(120.0));
    }

    @Test
    void getRouteWithBadIdTest() throws Exception {
        mockMvc.perform(get("/api/v1/map/route/RIDE/abc/PLACE/2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for fromId"));

        verifyNoInteractions(mapService);
    }

    @Test
    void getRouteFailureTest() throws Exception {
        when(mapService.getRoute("RIDE", 1, "PLACE", 9)).thenThrow(new ApiException("place not found"));

        mockMvc.perform(get("/api/v1/map/route/RIDE/1/PLACE/9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("place not found"));
    }
}