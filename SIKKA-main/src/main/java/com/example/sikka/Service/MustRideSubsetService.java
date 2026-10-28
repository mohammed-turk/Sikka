package com.example.sikka.Service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.*;

@Service
public class MustRideSubsetService {

    // Conservative server-side ride packing hint. Actual schedules are validated separately.
    public Map<String, Object> buildFeasibilityHint(JsonNode input) {
        int duration = input.path("durationMinutes").asInt(0);
        int prayerReserve = input.path("includePrayer").asBoolean() ? 15 : 0;
        String entrance = input.path("walking").path("entranceNodeKey").asText("");
        Map<String, Integer> routes = new HashMap<>();
        for (JsonNode route : input.path("walking").path("routes")) {
            if (route.path("minutes").isIntegralNumber()) routes.put(route.path("fromNodeKey").asText() + ">" + route.path("toNodeKey").asText(), route.path("minutes").asInt());
        }
        Map<Integer, JsonNode> catalog = new HashMap<>();
        for (JsonNode ride : input.path("rides")) catalog.put(ride.path("id").asInt(), ride);
        Map<Integer, List<Integer>> choices = new LinkedHashMap<>();
        List<Integer> all = new ArrayList<>();
        for (JsonNode member : input.path("members")) all.add(member.path("visitMemberId").asInt());
        boolean shared = !input.path("allowSplit").asBoolean() && !"SOLO".equals(input.path("tripType").asText());
        if (shared) {
            List<Integer> rideIds = new ArrayList<>();
            input.path("sharedSelectedRideIds").forEach(ride -> rideIds.add(ride.asInt()));
            for (Integer memberId : all) choices.put(memberId, rideIds);
        } else {
            for (JsonNode member : input.path("members")) {
                List<Integer> requested = new ArrayList<>();
                member.path("selectedRideIds").forEach(ride -> requested.add(ride.asInt()));
                choices.put(member.path("visitMemberId").asInt(), requested);
            }
        }
        Map<Integer, List<Integer>> packed = new LinkedHashMap<>();
        Map<Integer, Integer> lowerBoundMinutes = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : choices.entrySet()) {
            int memberId = entry.getKey();
            int reserved = prayerReserve;
            for (JsonNode meal : input.path("mealPolicy").path("breaks")) {
                for (JsonNode participant : meal.path("participantVisitMemberIds")) {
                    if (participant.asInt() == memberId) {
                        reserved += meal.path("durationMinutes").asInt();
                        break;
                    }
                }
            }
            int budget = Math.max(0, duration - reserved);
            int used = 0;
            String currentNode = entrance;
            Set<Integer> unselected = new LinkedHashSet<>(entry.getValue());
            List<Integer> selected = new ArrayList<>();
            while (!unselected.isEmpty()) {
                Integer bestId = null;
                int bestCost = Integer.MAX_VALUE;
                for (Integer rideId : unselected) {
                    JsonNode ride = catalog.get(rideId);
                    if (ride == null || !"OPEN".equals(ride.path("status").asText()) || !ride.path("planningWaitMinutes").isIntegralNumber() || !ride.path("planningActivityMinutes").isIntegralNumber()) continue;
                    String eligibility = input.path("eligibilityByRideAndMember").path(String.valueOf(rideId)).path(String.valueOf(memberId)).asText("");
                    if (!Set.of("ELIGIBLE", "UNKNOWN").contains(eligibility)) continue;
                    if ("AVOID".equals(input.path("ridePreferencesByRideAndMember").path(String.valueOf(rideId)).path(String.valueOf(memberId)).asText())) continue;
                    String nextNode = ride.path("nodeKey").asText("");
                    Integer walk = currentNode.equals(nextNode) ? 0 : routes.get(currentNode + ">" + nextNode);
                    if (walk == null) continue;
                    long total = (long) walk + ride.path("planningWaitMinutes").asInt() + ride.path("planningActivityMinutes").asInt();
                    if (total <= Integer.MAX_VALUE && total + used <= budget && total < bestCost) {
                        bestCost = (int) total;
                        bestId = rideId;
                    }
                }
                if (bestId == null) break;
                selected.add(bestId);
                unselected.remove(bestId);
                used += bestCost;
                currentNode = catalog.get(bestId).path("nodeKey").asText();
            }
            packed.put(memberId, selected);
            lowerBoundMinutes.put(memberId, used);
        }
        return Map.of("strategy", "CONSERVATIVE_SERVER_PACKING_HINT", "notAConfirmedSchedule", true, "chosenRideIdsByVisitMemberId", packed, "estimatedRideAndTravelMinutesByVisitMemberId", lowerBoundMinutes, "prayerReservedMinutes", prayerReserve);
    }
}
