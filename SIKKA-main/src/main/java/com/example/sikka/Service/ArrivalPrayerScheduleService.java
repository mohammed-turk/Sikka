package com.example.sikka.Service;

import com.example.sikka.DtoOut.AiPlanDtoOut;
import com.example.sikka.DtoOut.AiPlanItemDtoOut;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.*;

@Service
public class ArrivalPrayerScheduleService {
    private record PrayerRoom(int id, String node, int walk) {}

    public AiPlanDtoOut scheduleArrivalWithMealsAndPrayer(JsonNode input, List<AiPlanItemDtoOut> approved, boolean replaceFood) {
        if (!replaceFood) return scheduleApprovedActivitiesWithPrayer(input, approved);
        List<JsonNode> meals = new ArrayList<>();
        input.path("mealPolicy").path("breaks").forEach(meals::add);
        return insertFoodBreaks(input, approved, meals, 0, new int[]{0});
    }

    private AiPlanDtoOut insertFoodBreaks(JsonNode input, List<AiPlanItemDtoOut> activities, List<JsonNode> meals, int index, int[] attempts) {
        if (attempts[0] >= 4096) return null;
        if (index == meals.size()) {
            attempts[0]++;
            List<AiPlanItemDtoOut> ordered = new ArrayList<>();
            for (AiPlanItemDtoOut activity : activities) {
                AiPlanItemDtoOut copy = copyActivity(activity);
                copy.setRelativeStartMinutes(ordered.size());
                copy.setRelativeEndMinutes(ordered.size() + 1);
                ordered.add(copy);
            }
            return scheduleApprovedActivitiesWithPrayer(input, ordered);
        }
        JsonNode meal = meals.get(index);
        String timing = meal.path("timingPreference").asString();
        int first = "END".equals(timing) ? activities.size() : "START".equals(timing) ? 0 : 1;
        int last = "START".equals(timing) ? 0 : "END".equals(timing) ? activities.size() : activities.size() - 1;
        for (int position = first; position <= last; position++) {
            AiPlanItemDtoOut food = new AiPlanItemDtoOut();
            food.setActivityKind("BREAK");
            food.setBreakType(meal.path("breakType").asString());
            food.setPlaceId(meal.path("restaurantId").isNull() ? null : meal.path("restaurantId").asInt());
            food.setActivityMinutes(meal.path("durationMinutes").asInt());
            List<Integer> participants = new ArrayList<>();
            meal.path("participantVisitMemberIds").forEach(member -> participants.add(member.asInt()));
            food.setParticipantVisitMemberIds(participants);
            food.setSubgroupKey(activities.isEmpty() ? "all" : activities.get(0).getSubgroupKey());
            List<AiPlanItemDtoOut> order = new ArrayList<>(activities);
            order.add(position, food);
            AiPlanDtoOut candidate = insertFoodBreaks(input, order, meals, index + 1, attempts);
            if (candidate != null) return candidate;
        }
        return null;
    }

    public AiPlanDtoOut scheduleApprovedActivitiesWithPrayer(JsonNode input, List<AiPlanItemDtoOut> approved) {
        if (approved.isEmpty() || !input.path("fixedActivities").isEmpty() || input.path("allowSplit").asBoolean()) {
            return null;
        }
        Set<Integer> members = new TreeSet<>();
        for (JsonNode member : input.path("members")) {
            members.add(member.path("visitMemberId").asInt());
        }
        for (AiPlanItemDtoOut activity : approved) {
            if (!members.equals(new TreeSet<>(activity.getParticipantVisitMemberIds()))) {
                return null;
            }
        }
        Map<String, Integer> routes = new HashMap<>();
        for (JsonNode route : input.path("walking").path("routes")) {
            routes.put(route.path("fromNodeKey").asString() + ">" + route.path("toNodeKey").asString(), route.path("minutes").asInt());
        }
        List<JsonNode> events = new ArrayList<>();
        input.path("prayerEvents").forEach(events::add);
        events.sort(Comparator.comparingInt(event -> event.path("relativePrayerMinute").asInt()));
        List<AiPlanItemDtoOut> order = new ArrayList<>(approved);
        order.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes).thenComparing(AiPlanItemDtoOut::getRelativeEndMinutes));
        List<AiPlanItemDtoOut> result = new ArrayList<>();
        String node = input.path("walking").path("entranceNodeKey").asString();
        int minute = 0;
        int eventIndex = 0;
        int activityIndex = 0;
        int deadline = input.path("durationMinutes").asInt();
        while (activityIndex < order.size() || eventIndex < events.size()) {
            AiPlanItemDtoOut original = activityIndex < order.size() ? order.get(activityIndex) : null;
            JsonNode resource = original == null ? null : findResource(input, original);
            if (original != null && (original.getRideId() != null || original.getPlaceId() != null) && resource == null) {
                return null;
            }
            String destination = resource == null ? node : resource.path("nodeKey").asString();
            Integer walk = node.equals(destination) ? Integer.valueOf(0) : routes.get(node + ">" + destination);
            if (walk == null) {
                return null;
            }
            int wait = original == null ? 0 : original.getRideId() == null ? original.getWaitingMinutes() : resource.path("planningWaitMinutes").asInt();
            int end = original == null ? minute : minute + walk + wait + original.getActivityMinutes();
            int prayerMinute = eventIndex < events.size() ? events.get(eventIndex).path("relativePrayerMinute").asInt() : Integer.MAX_VALUE;
            PrayerRoom followingRoom = eventIndex < events.size() && original != null ? findReachablePrayerRoom(input, routes, destination, end, prayerMinute) : null;
            if (eventIndex < events.size() && (original == null || followingRoom == null)) {
                PrayerRoom room = findReachablePrayerRoom(input, routes, node, minute, prayerMinute);
                if (room == null || prayerMinute + 15 > deadline) {
                    return null;
                }
                AiPlanItemDtoOut prayer = copyActivity(order.get(0));
                prayer.setActivityKind("BREAK");
                prayer.setRideId(null);
                prayer.setPlaceId(room.id());
                prayer.setBreakType("PRAYER");
                prayer.setRelativeStartMinutes(prayerMinute - room.walk());
                prayer.setRelativeEndMinutes(prayerMinute + 15);
                prayer.setWalkingMinutes(room.walk());
                prayer.setWaitingMinutes(0);
                prayer.setActivityMinutes(15);
                result.add(prayer);
                minute = prayerMinute + 15;
                node = room.node();
                eventIndex++;
                continue;
            }
            if (original != null) {
                if (end > deadline) {
                    return null;
                }
                AiPlanItemDtoOut timed = copyActivity(original);
                timed.setRelativeStartMinutes(minute);
                timed.setRelativeEndMinutes(end);
                timed.setWalkingMinutes(walk);
                timed.setWaitingMinutes(wait);
                result.add(timed);
                minute = end;
                node = destination;
                activityIndex++;
            }
        }
        AiPlanDtoOut plan = new AiPlanDtoOut();
        if (!foodPolicyMatches(input, result)) return null;
        plan.setFeasible(true);
        plan.setActivities(result);
        plan.setWarnings(new ArrayList<>());
        return plan;
    }

    private boolean foodPolicyMatches(JsonNode input, List<AiPlanItemDtoOut> activities) {
        if (!input.path("mealPolicy").has("breaks")) return true;
        Set<AiPlanItemDtoOut> matched = Collections.newSetFromMap(new IdentityHashMap<>());
        for (JsonNode definition : input.path("mealPolicy").path("breaks")) {
            Set<Integer> participants = new HashSet<>();
            definition.path("participantVisitMemberIds").forEach(member -> participants.add(member.asInt()));
            boolean found = false;
            for (int index = 0; index < activities.size(); index++) {
                AiPlanItemDtoOut activity = activities.get(index);
                if (matched.contains(activity) || !definition.path("breakType").asString().equals(activity.getBreakType()) || activity.getActivityMinutes() != definition.path("durationMinutes").asInt() || !participants.equals(new HashSet<>(activity.getParticipantVisitMemberIds())) || !Objects.equals(activity.getPlaceId(), definition.path("restaurantId").isNull() ? null : definition.path("restaurantId").asInt())) continue;
                if (activity.getRelativeStartMinutes() < definition.path("earliestStartMinutes").asInt() || activity.getRelativeStartMinutes() > definition.path("latestStartMinutes").asInt()) continue;
                boolean before = activities.subList(0, index).stream().anyMatch(item -> !isFood(item));
                boolean after = activities.subList(index + 1, activities.size()).stream().anyMatch(item -> !isFood(item));
                String timing = definition.path("timingPreference").asString();
                if ("START".equals(timing) && before || "END".equals(timing) && after || "MIDDLE".equals(timing) && (!before || !after)) continue;
                matched.add(activity);
                found = true;
                break;
            }
            if (!found) return false;
        }
        return matched.size() == activities.stream().filter(this::isFood).count();
    }

    private boolean isFood(AiPlanItemDtoOut activity) {
        return "MEAL".equals(activity.getBreakType()) || "SNACK".equals(activity.getBreakType());
    }

    private JsonNode findResource(JsonNode input, AiPlanItemDtoOut activity) {
        String type = activity.getRideId() != null ? "rides" : "places";
        Integer id = activity.getRideId() != null ? activity.getRideId() : activity.getPlaceId();
        if (id == null) {
            return null;
        }
        for (JsonNode resource : input.path(type)) {
            if (resource.path("id").asInt() == id && "OPEN".equals(resource.path("status").asString())) {
                return resource;
            }
        }
        return null;
    }

    private PrayerRoom findReachablePrayerRoom(JsonNode input, Map<String, Integer> routes, String node, int available, int prayerMinute) {
        PrayerRoom best = null;
        for (JsonNode place : input.path("places")) {
            if (!"PRAYER_ROOM".equals(place.path("kind").asString()) || !"OPEN".equals(place.path("status").asString())) {
                continue;
            }
            String destination = place.path("nodeKey").asString();
            Integer walk = node.equals(destination) ? Integer.valueOf(0) : routes.get(node + ">" + destination);
            if (walk != null && walk >= 0 && available + walk <= prayerMinute && (best == null || walk < best.walk())) {
                best = new PrayerRoom(place.path("id").asInt(), destination, walk);
            }
        }
        return best;
    }

    private AiPlanItemDtoOut copyActivity(AiPlanItemDtoOut original) {
        AiPlanItemDtoOut copy = new AiPlanItemDtoOut();
        copy.setActivityKind(original.getActivityKind());
        copy.setRideId(original.getRideId());
        copy.setPlaceId(original.getPlaceId());
        copy.setBreakType(original.getBreakType());
        copy.setParticipantVisitMemberIds(new ArrayList<>(original.getParticipantVisitMemberIds()));
        copy.setSubgroupKey(original.getSubgroupKey());
        copy.setActivityMinutes(original.getActivityMinutes());
        copy.setWaitingMinutes(original.getWaitingMinutes() == null ? 0 : original.getWaitingMinutes());
        copy.setWalkingMinutes(original.getWalkingMinutes() == null ? 0 : original.getWalkingMinutes());
        return copy;
    }
}
