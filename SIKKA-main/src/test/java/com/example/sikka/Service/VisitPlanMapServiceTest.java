package com.example.sikka.Service;

import com.example.sikka.DtoOut.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisitPlanMapServiceTest {
    @Test
    void keepEachSplitMembersPreviousLocationAndIncludeEntranceSegments() {
        VisitPlanService plans = mock(VisitPlanService.class);
        MapService maps = mock(MapService.class);
        when(maps.getMapData()).thenReturn(tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(Map.of("entrancesVerified", false)));
        List<PlanItemDtoOut> items = List.of(item(1, 1, 0, "[10]"), item(2, 2, 0, "[11]"), item(3, 3, 20, "[10]"), item(4, 4, 20, "[11]"));
        VisitPlanDetailsDtoOut details = new VisitPlanDetailsDtoOut();
        details.setPlanItems(items);
        when(plans.getVisitPlanDetails(2, 9)).thenReturn(details);
        when(maps.getEntranceRoute(anyString(), anyInt())).thenReturn(new MapRouteDtoOut());
        when(maps.getRoute(anyString(), anyInt(), anyString(), anyInt())).thenReturn(new MapRouteDtoOut());
        Map<String, Object> result = new VisitPlanMapService(plans, maps).getVisitPlanMapRoute(2, 9);
        assertEquals("PER_MEMBER", result.get("routeScope"));
        assertEquals(4, ((List<?>) result.get("segments")).size());
        verify(maps).getEntranceRoute("RIDE", 1);
        verify(maps).getEntranceRoute("RIDE", 2);
        verify(maps).getRoute("RIDE", 1, "RIDE", 3);
        verify(maps).getRoute("RIDE", 2, "RIDE", 4);
        verify(maps, never()).getRoute("RIDE", 2, "RIDE", 3);
    }

    @Test
    void exposeUnavailableRouteWithoutInventingGeometry() {
        VisitPlanService plans = mock(VisitPlanService.class);
        MapService maps = mock(MapService.class);
        when(maps.getMapData()).thenReturn(tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(Map.of()));
        VisitPlanDetailsDtoOut details = new VisitPlanDetailsDtoOut();
        details.setPlanItems(List.of(item(1, 1, 0, "[10]")));
        when(plans.getVisitPlanDetails(2, 9)).thenReturn(details);
        when(maps.getEntranceRoute("RIDE", 1)).thenThrow(new com.example.sikka.Api.ApiException("Unlinked"));
        List<?> segments = (List<?>) new VisitPlanMapService(plans, maps).getVisitPlanMapRoute(2, 9).get("segments");
        Map<?, ?> segment = (Map<?, ?>) segments.get(0);
        assertEquals(false, segment.get("available"));
        assertFalse(segment.containsKey("route"));
    }

    private PlanItemDtoOut item(int id, int rideId, int start, String members) {
        PlanItemDtoOut item = new PlanItemDtoOut();
        item.setId(id);
        item.setRideId(rideId);
        item.setActivityKind("RIDE");
        item.setRelativeStartMinutes(start);
        item.setRelativeEndMinutes(start + 10);
        item.setParticipantVisitMemberIds(members);
        return item;
    }
}
