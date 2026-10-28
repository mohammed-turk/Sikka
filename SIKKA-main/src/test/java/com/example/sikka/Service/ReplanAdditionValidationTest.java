package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoOut.AiPlanItemDtoOut;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReplanAdditionValidationTest {
    private final ServerItineraryService planner = new ServerItineraryService();

    private JsonNode input() {
        return JsonMapper.builder().build().valueToTree(Map.of("allowSplit", false, "rides", List.of(Map.of("id", 1, "status", "OPEN", "nodeKey", "RIDE:1", "planningWaitMinutes", 5, "planningActivityMinutes", 5, "features", List.of())), "members", List.of(Map.of("visitMemberId", 10, "dislikedFeatures", List.of())), "eligibilityByRideAndMember", Map.of("1", Map.of("10", "UNKNOWN")), "walking", Map.of("entranceNodeKey", "ENTRANCE", "routes", List.of(Map.of("fromNodeKey", "ENTRANCE", "toNodeKey", "RIDE:1", "minutes", 2)))));
    }

    private void validate(List<AiPlanItemDtoOut> baseline, AiPlanItemDtoOut addition, List<Integer> affected) {
        VisitPlanService service = mock(VisitPlanService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "serverItineraryService", planner);
        ReflectionTestUtils.invokeMethod(service, "validateAdditionalRides", input(), baseline, List.of(addition), affected);
    }

    @Test
    void acceptOnlyNewRideWithSuppliedWaitAndRouteWithinAffectedScope() {
        AiPlanItemDtoOut addition = planner.rideActivity(input().path("rides").get(0), List.of(10), 20, 2);
        assertDoesNotThrow(() -> validate(List.of(), addition, List.of(10)));
        addition.setWaitingMinutes(0);
        assertThrows(ApiException.class, () -> validate(List.of(), addition, List.of(10)));
    }

    @Test
    void rejectDuplicateCompletedRideOrOutOfScopeAddition() {
        AiPlanItemDtoOut completed = planner.rideActivity(input().path("rides").get(0), List.of(10), 0, 2);
        AiPlanItemDtoOut addition = planner.rideActivity(input().path("rides").get(0), List.of(10), 20, 2);
        assertThrows(ApiException.class, () -> validate(List.of(completed), addition, List.of(10)));
        assertThrows(ApiException.class, () -> validate(List.of(), addition, List.of(11)));
    }
}
