package com.example.sikka.Service;

import com.example.sikka.DtoOut.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArrivalPrayerScheduleTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ArrivalPrayerScheduleService scheduler = new ArrivalPrayerScheduleService();

    private ObjectNode input() {
        List<Map<String, Object>> routes = new ArrayList<>();
        for (String from : List.of("ENTRANCE", "RIDE:1", "RIDE:2", "PLACE:1")) {
            for (String to : List.of("ENTRANCE", "RIDE:1", "RIDE:2", "PLACE:1")) {
                routes.add(Map.of("fromNodeKey", from, "toNodeKey", to, "minutes", from.equals(to) ? 0 : 2));
            }
        }
        ObjectNode input = mapper.createObjectNode();
        input.put("durationMinutes", 240);
        input.put("allowSplit", false);
        input.set("fixedActivities", mapper.valueToTree(List.of()));
        input.set("members", mapper.valueToTree(List.of(Map.of("visitMemberId", 1))));
        input.set("rides", mapper.valueToTree(List.of(Map.of("id", 1, "nodeKey", "RIDE:1", "status", "OPEN", "planningWaitMinutes", 10), Map.of("id", 2, "nodeKey", "RIDE:2", "status", "OPEN", "planningWaitMinutes", 10))));
        input.set("places", mapper.valueToTree(List.of(Map.of("id", 1, "nodeKey", "PLACE:1", "kind", "PRAYER_ROOM", "status", "OPEN"))));
        input.set("prayerEvents", mapper.valueToTree(List.of(Map.of("relativePrayerMinute", 25), Map.of("relativePrayerMinute", 130))));
        input.set("walking", mapper.valueToTree(Map.of("entranceNodeKey", "ENTRANCE", "routes", routes)));
        return input;
    }

    private AiPlanItemDtoOut ride(int id, int start) {
        AiPlanItemDtoOut ride = new AiPlanItemDtoOut();
        ride.setActivityKind("RIDE");
        ride.setRideId(id);
        ride.setRelativeStartMinutes(start);
        ride.setRelativeEndMinutes(start + 22);
        ride.setWalkingMinutes(2);
        ride.setWaitingMinutes(10);
        ride.setActivityMinutes(10);
        ride.setParticipantVisitMemberIds(List.of(1));
        ride.setSubgroupKey("all");
        return ride;
    }

    @Test
    void insertEveryPrayerBetweenApprovedRidesAndUseExactlyFifteenMinutes() {
        AiPlanDtoOut plan = scheduler.scheduleApprovedActivitiesWithPrayer(input(), List.of(ride(1, 0), ride(2, 22)));
        assertNotNull(plan);
        assertEquals(List.of(1, 2), plan.getActivities().stream().filter(a -> a.getRideId() != null).map(AiPlanItemDtoOut::getRideId).toList());
        List<AiPlanItemDtoOut> prayers = plan.getActivities().stream().filter(a -> "PRAYER".equals(a.getBreakType())).toList();
        assertEquals(List.of(25, 130), prayers.stream().map(a -> a.getRelativeStartMinutes() + a.getWalkingMinutes()).toList());
        for (AiPlanItemDtoOut activity : plan.getActivities()) {
            assertEquals(activity.getWalkingMinutes() + activity.getWaitingMinutes() + activity.getActivityMinutes(), activity.getRelativeEndMinutes() - activity.getRelativeStartMinutes());
        }
        for (AiPlanItemDtoOut prayer : prayers) {
            assertEquals(15, prayer.getActivityMinutes());
            assertEquals(List.of(1), prayer.getParticipantVisitMemberIds());
        }
        assertEquals(40, plan.getActivities().get(2).getRelativeStartMinutes());
    }

    @Test
    void preserveFoodBreakAndRejectMissingRoutesOrInsufficientDuration() {
        AiPlanItemDtoOut meal = ride(2, 22);
        meal.setRideId(null);
        meal.setActivityKind("BREAK");
        meal.setBreakType("MEAL");
        meal.setActivityMinutes(60);
        meal.setWaitingMinutes(0);
        AiPlanDtoOut plan = scheduler.scheduleApprovedActivitiesWithPrayer(input(), List.of(ride(1, 0), meal, ride(2, 82)));
        assertNotNull(plan);
        assertEquals(1, plan.getActivities().stream().filter(a -> "MEAL".equals(a.getBreakType()) && a.getActivityMinutes() == 60).count());
        ObjectNode shortVisit = input();
        shortVisit.put("durationMinutes", 50);
        assertNull(scheduler.scheduleApprovedActivitiesWithPrayer(shortVisit, List.of(ride(1, 0), ride(2, 22))));
        ObjectNode missing = input();
        ((ObjectNode) missing.path("walking")).set("routes", mapper.valueToTree(List.of()));
        assertNull(scheduler.scheduleApprovedActivitiesWithPrayer(missing, List.of(ride(1, 0))));
    }
    @Test
    void changedWalkAndQueueRecalculateEndTimeFromTrustedValues() {
        ObjectNode input = input();
        input.set("prayerEvents", mapper.valueToTree(List.of()));
        input.set("rides", mapper.valueToTree(List.of(Map.of("id", 4, "nodeKey", "RIDE:4", "status", "OPEN", "planningWaitMinutes", 15))));
        ((ObjectNode) input.path("walking")).set("routes", mapper.valueToTree(List.of(Map.of("fromNodeKey", "ENTRANCE", "toNodeKey", "RIDE:4", "minutes", 6))));
        AiPlanItemDtoOut original = ride(4, 0);
        original.setRelativeEndMinutes(24);
        original.setActivityMinutes(8);
        AiPlanDtoOut result = scheduler.scheduleApprovedActivitiesWithPrayer(input, List.of(original));
        assertNotNull(result);
        assertEquals(29, result.getActivities().get(0).getRelativeEndMinutes());
        assertEquals(24, original.getRelativeEndMinutes());
    }

    @Test
    void resolvedMealAndPrayersFitWithoutAiOrChangingApprovedRideOrder() {
        ObjectNode input = input();
        Map<String, Object> meal = new LinkedHashMap<>(Map.of("breakType", "MEAL", "timingPreference", "MIDDLE", "durationMinutes", 60, "earliestStartMinutes", 0, "latestStartMinutes", 180, "participantVisitMemberIds", List.of(1)));
        meal.put("restaurantId", null);
        input.set("mealPolicy", mapper.valueToTree(Map.of("breaks", List.of(meal))));
        AiPlanDtoOut result = scheduler.scheduleArrivalWithMealsAndPrayer(input, List.of(ride(1, 0), ride(2, 22)), true);
        assertNotNull(result);
        assertEquals(List.of(1, 2), result.getActivities().stream().filter(activity -> activity.getRideId() != null).map(AiPlanItemDtoOut::getRideId).toList());
        assertEquals(2, result.getActivities().stream().filter(activity -> "PRAYER".equals(activity.getBreakType())).count());
        assertEquals(1, result.getActivities().stream().filter(activity -> "MEAL".equals(activity.getBreakType()) && activity.getActivityMinutes() == 60).count());
        for (AiPlanItemDtoOut activity : result.getActivities()) assertEquals(activity.getWalkingMinutes() + activity.getWaitingMinutes() + activity.getActivityMinutes(), activity.getRelativeEndMinutes() - activity.getRelativeStartMinutes());
        input.put("durationMinutes", 70);
        assertNull(scheduler.scheduleArrivalWithMealsAndPrayer(input, List.of(ride(1, 0), ride(2, 22)), true));
    }

}
