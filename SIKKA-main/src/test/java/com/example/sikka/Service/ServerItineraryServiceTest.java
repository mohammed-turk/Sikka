package com.example.sikka.Service;

import com.example.sikka.DtoOut.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerItineraryServiceTest {
    private final ServerItineraryService planner = new ServerItineraryService();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private ObjectNode input(int duration, boolean split) {
        List<Map<String, Object>> routes = new ArrayList<>();
        for (String from : List.of("ENTRANCE", "RIDE:1", "RIDE:2", "RIDE:3")) for (String to : List.of("ENTRANCE", "RIDE:1", "RIDE:2", "RIDE:3")) routes.add(Map.of("fromNodeKey", from, "toNodeKey", to, "minutes", from.equals(to) ? 0 : 1));
        Map<String, Object> member = Map.of("visitMemberId", 1, "ageKnown", true, "ageYears", 25, "selectedRideIds", List.of(1, 2), "selectedRides", List.of(Map.of("rideId", 1, "priority", "MUST_RIDE"), Map.of("rideId", 2, "priority", "MUST_RIDE")), "dislikedFeatures", List.of());
        List<Map<String, Object>> rides = new ArrayList<>();
        for (int id = 1; id <= 3; id++) rides.add(Map.of("id", id, "name", "Ride " + id, "status", "OPEN", "nodeKey", "RIDE:" + id, "planningWaitMinutes", 5, "planningActivityMinutes", 5, "features", List.of()));
        return (ObjectNode) mapper.valueToTree(Map.of("durationMinutes", duration, "primaryGoal", "MAX_RIDES", "tripType", "SOLO", "allowSplit", split, "members", List.of(member), "rides", rides, "walking", Map.of("entranceNodeKey", "ENTRANCE", "routes", routes), "mealPolicy", Map.of("breaks", List.of()), "eligibilityByRideAndMember", Map.of("1", Map.of("1", "UNKNOWN"), "2", Map.of("1", "UNKNOWN"), "3", Map.of("1", "UNKNOWN"))));
    }

    @Test
    void includeRequiredRidesAndFillSpareTimeWithoutRepeats() {
        AiPlanDtoOut plan = planner.selectInitialPlan(input(40, false));
        assertTrue(plan.getFeasible());
        assertEquals(3, plan.getActivities().size());
        assertTrue(plan.getExcludedRides().isEmpty());
        assertEquals(3, plan.getActivities().stream().map(AiPlanItemDtoOut::getRideId).distinct().count());
        for (AiPlanItemDtoOut activity : plan.getActivities()) assertEquals(activity.getWalkingMinutes() + activity.getWaitingMinutes() + activity.getActivityMinutes(), activity.getRelativeEndMinutes() - activity.getRelativeStartMinutes());
    }

    @Test
    void chooseRequiredSubsetAndExplainTheOtherChoice() {
        AiPlanDtoOut plan = planner.selectInitialPlan(input(15, false));
        assertEquals(1, plan.getActivities().size());
        assertTrue(Set.of(1, 2).contains(plan.getActivities().get(0).getRideId()));
        assertEquals(1, plan.getExcludedRides().size());
        assertEquals("MUST_RIDE", plan.getExcludedRides().get(0).getPriority());
    }

    @Test
    void reportInfeasibleWhenEvenOneRideCannotFit() {
        AiPlanDtoOut plan = planner.selectInitialPlan(input(5, false));
        assertFalse(plan.getFeasible());
        assertTrue(plan.getActivities().isEmpty());
        assertEquals(2, plan.getExcludedRides().size());
    }

    @Test
    void neverInventUnknownWaitOrMissingWalkingRoute() {
        ObjectNode input = input(40, false);
        ((ObjectNode) input.path("rides").get(0)).putNull("planningWaitMinutes");
        ((ObjectNode) input.path("walking")).set("routes", mapper.valueToTree(List.of()));
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertFalse(plan.getFeasible());
        assertTrue(plan.getExcludedRides().stream().anyMatch(exclusion -> "MISSING_WAIT_DATA".equals(exclusion.getReasonCode())));
    }

    @Test
    void respectAvoidanceAndClosuresBeforeSelectingOptionalRides() {
        ObjectNode input = input(40, false);
        ((ObjectNode) input.path("rides").get(0)).put("status", "CLOSED");
        input.set("ridePreferencesByRideAndMember", mapper.valueToTree(Map.of("2", Map.of("1", "AVOID"))));
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertEquals(List.of(3), plan.getActivities().stream().map(AiPlanItemDtoOut::getRideId).toList());
    }

    @Test
    void reconsiderOmittedRequiredRideAfterEarlyReleaseWithoutChangingHistory() {
        ObjectNode input = input(35, false);
        JsonNode ride = input.path("rides").get(0);
        AiPlanItemDtoOut completed = planner.rideActivity(ride, List.of(1), 0, 1);
        completed.setExecutionEndMinutes(3);
        List<AiPlanItemDtoOut> additions = planner.selectAdditionalRides(input, List.of(completed), List.of(1), 4);
        assertTrue(additions.stream().anyMatch(activity -> activity.getRideId() == 2));
        assertEquals(11, completed.getRelativeEndMinutes());
        assertTrue(additions.stream().allMatch(activity -> activity.getRelativeStartMinutes() >= 4));
        assertTrue(additions.stream().noneMatch(activity -> activity.getRideId() == 1));
    }

    @Test
    void preferLessWalkingWhenRequiredCountAndRideCountTie() {
        ObjectNode input = input(15, false);
        input.put("primaryGoal", "MIN_WALKING");
        for (JsonNode route : input.path("walking").path("routes")) if ("ENTRANCE".equals(route.path("fromNodeKey").asString()) && "RIDE:1".equals(route.path("toNodeKey").asString())) ((ObjectNode) route).put("minutes", 4);
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertEquals(2, plan.getActivities().get(0).getRideId());
    }
    @Test
    void splitFriendsWithOptionalPreferencesStillIncludeEveryParticipant() {
        ObjectNode input = input(40, true);
        input.put("tripType", "FRIENDS");
        ((tools.jackson.databind.node.ArrayNode) input.path("members")).add(mapper.valueToTree(Map.of("visitMemberId", 2, "ageKnown", true, "ageYears", 26, "selectedRides", List.of())));
        input.set("eligibilityByRideAndMember", mapper.valueToTree(Map.of("1", Map.of("1", "UNKNOWN", "2", "UNKNOWN"), "2", Map.of("1", "UNKNOWN", "2", "UNKNOWN"), "3", Map.of("1", "UNKNOWN", "2", "UNKNOWN"))));
        ((tools.jackson.databind.node.ArrayNode) input.path("rides")).remove(2);
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertTrue(plan.getFeasible());
        assertEquals(Set.of(1, 2), plan.getActivities().stream().flatMap(activity -> activity.getParticipantVisitMemberIds().stream()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(plan.getActivities().size(), plan.getActivities().stream().map(AiPlanItemDtoOut::getRideId).distinct().count());
    }

    @Test
    void splitFriendsNeverForceAnExcludedParticipantIntoARide() {
        ObjectNode input = input(40, true);
        input.put("tripType", "FRIENDS");
        ((tools.jackson.databind.node.ArrayNode) input.path("members")).add(mapper.valueToTree(Map.of("visitMemberId", 2, "ageKnown", true, "ageYears", 26, "selectedRides", List.of(), "excludedRideIds", List.of(1, 2, 3))));
        input.set("eligibilityByRideAndMember", mapper.valueToTree(Map.of("1", Map.of("1", "UNKNOWN", "2", "UNKNOWN"), "2", Map.of("1", "UNKNOWN", "2", "UNKNOWN"), "3", Map.of("1", "UNKNOWN", "2", "UNKNOWN"))));
        assertFalse(planner.selectInitialPlan(input).getFeasible());
    }

    @Test
    void planIndependentAdultGroupsConcurrently() {
        ObjectNode input = input(12, true);
        input.put("tripType", "FRIENDS");
        input.set("members", mapper.valueToTree(List.of(Map.of("visitMemberId", 1, "ageKnown", true, "ageYears", 25, "selectedRideIds", List.of(1), "selectedRides", List.of(Map.of("rideId", 1, "priority", "MUST_RIDE"))), Map.of("visitMemberId", 2, "ageKnown", true, "ageYears", 26, "selectedRideIds", List.of(2), "selectedRides", List.of(Map.of("rideId", 2, "priority", "MUST_RIDE"))))));
        input.set("eligibilityByRideAndMember", mapper.valueToTree(Map.of("1", Map.of("1", "UNKNOWN", "2", "UNKNOWN"), "2", Map.of("1", "UNKNOWN", "2", "UNKNOWN"), "3", Map.of("1", "UNKNOWN", "2", "UNKNOWN"))));
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertTrue(plan.getFeasible());
        assertEquals(2, plan.getActivities().size());
        assertTrue(plan.getActivities().stream().allMatch(activity -> activity.getRelativeStartMinutes() == 0));
        assertTrue(plan.getExcludedRides().isEmpty());
    }

    @Test
    void sharedRideIsOneActivityAndExclusionsRemainPersonal() {
        ObjectNode input = input(120, true);
        input.put("tripType", "FRIENDS");
        input.set("members", mapper.valueToTree(List.of(Map.of("visitMemberId", 1, "ageKnown", true, "ageYears", 25, "selectedRideIds", List.of(1, 2), "selectedRides", List.of(Map.of("rideId", 1, "priority", "MUST_RIDE"), Map.of("rideId", 2, "priority", "MUST_RIDE"))), Map.of("visitMemberId", 2, "ageKnown", true, "ageYears", 26, "selectedRideIds", List.of(1), "selectedRides", List.of(Map.of("rideId", 1, "priority", "MUST_RIDE")), "excludedRideIds", List.of(2)))));
        input.set("eligibilityByRideAndMember", mapper.valueToTree(Map.of("1", Map.of("1", "UNKNOWN", "2", "UNKNOWN"), "2", Map.of("1", "UNKNOWN", "2", "UNKNOWN"), "3", Map.of("1", "UNKNOWN", "2", "UNKNOWN"))));
        input.set("mealPolicy", mapper.valueToTree(Map.of("breaks", List.of(Map.of("breakKey", "shared", "breakType", "MEAL", "timingPreference", "END", "durationMinutes", 60, "earliestStartMinutes", 0, "latestStartMinutes", 60, "restaurantId", mapper.nullNode(), "participantVisitMemberIds", List.of(1, 2))))));
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertTrue(plan.getFeasible());
        assertEquals(1, plan.getActivities().stream().filter(activity -> Integer.valueOf(1).equals(activity.getRideId())).count());
        assertEquals(List.of(1, 2), plan.getActivities().stream().filter(activity -> Integer.valueOf(1).equals(activity.getRideId())).findFirst().orElseThrow().getParticipantVisitMemberIds());
        assertEquals(List.of(1), plan.getActivities().stream().filter(activity -> Integer.valueOf(2).equals(activity.getRideId())).findFirst().orElseThrow().getParticipantVisitMemberIds());
        assertEquals(List.of(1, 2), plan.getActivities().stream().filter(activity -> "MEAL".equals(activity.getBreakType())).findFirst().orElseThrow().getParticipantVisitMemberIds());
        assertEquals(plan.getActivities().stream().filter(activity -> activity.getRideId() != null).count(), plan.getActivities().stream().map(AiPlanItemDtoOut::getRideId).filter(Objects::nonNull).distinct().count());
    }

    @Test
    void excludedRideTypeIsEnforcedDuringSelection() {
        ObjectNode input = input(60, false);
        ((ObjectNode) input.path("members").get(0)).set("excludedRideTypes", mapper.valueToTree(List.of("FAMILY")));
        ((ObjectNode) input.path("rides").get(0)).put("category", "FAMILY");
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertTrue(plan.getFeasible());
        assertTrue(plan.getActivities().stream().noneMatch(activity -> Integer.valueOf(1).equals(activity.getRideId())));
        assertTrue(plan.getExcludedRides().stream().anyMatch(excluded -> excluded.getRideId() == 1));
    }

    @Test
    void reserveActualMealAndSnackDurationsAndHonorTheirPlacement() {
        ObjectNode input = input(130, false);
        Map<String, Object> meal = new LinkedHashMap<>(Map.of("breakKey", "meal", "breakType", "MEAL", "timingPreference", "START", "durationMinutes", 60, "earliestStartMinutes", 0, "latestStartMinutes", 129, "participantVisitMemberIds", List.of(1)));
        meal.put("restaurantId", null);
        Map<String, Object> snack = new LinkedHashMap<>(Map.of("breakKey", "snack", "breakType", "SNACK", "timingPreference", "END", "durationMinutes", 30, "earliestStartMinutes", 0, "latestStartMinutes", 129, "participantVisitMemberIds", List.of(1)));
        snack.put("restaurantId", null);
        ((ObjectNode) input.path("mealPolicy")).set("breaks", mapper.valueToTree(List.of(meal, snack)));
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertTrue(plan.getFeasible());
        assertEquals("MEAL", plan.getActivities().get(0).getBreakType());
        assertEquals(60, plan.getActivities().get(0).getActivityMinutes());
        assertEquals("SNACK", plan.getActivities().get(plan.getActivities().size() - 1).getBreakType());
        assertEquals(30, plan.getActivities().get(plan.getActivities().size() - 1).getActivityMinutes());
        assertTrue(plan.getActivities().get(plan.getActivities().size() - 1).getRelativeEndMinutes() <= 130);
    }

    @Test
    void leavePrayerReserveInsteadOfFillingItWithOptionalRides() {
        ObjectNode input = input(40, false);
        input.set("includePrayer", mapper.valueToTree(true));
        AiPlanDtoOut plan = planner.selectInitialPlan(input);
        assertEquals(2, plan.getActivities().size());
        assertTrue(plan.getActivities().stream().allMatch(activity -> activity.getRelativeEndMinutes() <= 25));
    }

    @Test
    void insertIntoGapBeforeFixedCommitmentWithoutChangingItsApproachWalk() {
        ObjectNode input = input(45, false);
        AiPlanItemDtoOut fixed = planner.rideActivity(input.path("rides").get(0), List.of(1), 30, 1);
        List<AiPlanItemDtoOut> additions = planner.selectAdditionalRides(input, List.of(fixed), List.of(1), 0);
        assertTrue(additions.stream().anyMatch(activity -> activity.getRideId() == 2 && activity.getRelativeEndMinutes() <= 30));
        assertEquals(30, fixed.getRelativeStartMinutes());
        assertEquals(1, fixed.getWalkingMinutes());
    }

    @Test
    void togetherFamilyExcludesAnIneligibleRequiredRideAndStillSchedulesSafeChoices() {
        ObjectNode input = input(40, false);
        input.put("tripType", "FAMILY");
        ObjectNode child = ((ObjectNode) input.path("members").get(0)).deepCopy();
        child.put("visitMemberId", 2);
        child.put("ageYears", 10);
        ((tools.jackson.databind.node.ArrayNode) input.path("members")).add(child);
        input.set("sharedSelectedRides", mapper.valueToTree(List.of(Map.of("rideId", 1, "priority", "MUST_RIDE"), Map.of("rideId", 2, "priority", "MUST_RIDE"))));
        input.set("eligibilityByRideAndMember", mapper.valueToTree(Map.of("1", Map.of("1", "ELIGIBLE", "2", "NOT_ELIGIBLE"), "2", Map.of("1", "ELIGIBLE", "2", "ELIGIBLE"), "3", Map.of("1", "ELIGIBLE", "2", "ELIGIBLE"))));
        AiPlanDtoOut result = planner.selectInitialPlan(input);
        assertTrue(result.getFeasible());
        assertTrue(result.getActivities().stream().noneMatch(activity -> Integer.valueOf(1).equals(activity.getRideId())));
        assertTrue(result.getActivities().stream().anyMatch(activity -> Integer.valueOf(2).equals(activity.getRideId()) && activity.getParticipantVisitMemberIds().equals(List.of(1, 2))));
        assertTrue(result.getExcludedRides().stream().anyMatch(ride -> Integer.valueOf(1).equals(ride.getRideId())));
    }

}
