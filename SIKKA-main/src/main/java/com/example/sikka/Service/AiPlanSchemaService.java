package com.example.sikka.Service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiPlanSchemaService {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public JsonNode getPlanOutputSchema() {

        Map<String, Object> activityProperties = new LinkedHashMap<>();

        activityProperties.put("activityKind", Map.of("type", "string", "enum", List.of("RIDE", "PLACE", "BREAK", "MEETING_POINT")));
        activityProperties.put("rideId", Map.of("type", List.of("integer", "null"), "minimum", 1));
        activityProperties.put("placeId", Map.of("type", List.of("integer", "null"), "minimum", 1));
        activityProperties.put("relativeStartMinutes", Map.of("type", "integer", "minimum", 0));
        activityProperties.put("relativeEndMinutes", Map.of("type", "integer", "minimum", 1));
        activityProperties.put("walkingMinutes", Map.of("type", "integer", "minimum", 0));
        activityProperties.put("waitingMinutes", Map.of("type", "integer", "minimum", 0));
        activityProperties.put("activityMinutes", Map.of("type", "integer", "minimum", 1));
        activityProperties.put("breakType", Map.of("type", List.of("string", "null"), "maxLength", 20));
        activityProperties.put("participantVisitMemberIds", Map.of("type", "array", "minItems", 1, "items", Map.of("type", "integer", "minimum", 1)));
        activityProperties.put("subgroupKey", Map.of("type", "string", "minLength", 1, "maxLength", 50));

        List<String> requiredActivityFields = List.copyOf(activityProperties.keySet());

        Map<String, Object> activitySchema = Map.of("type", "object", "properties", activityProperties, "required", requiredActivityFields, "additionalProperties", false);

        Map<String, Object> planProperties = new LinkedHashMap<>();

        Map<String, Object> excludedProperties = new LinkedHashMap<>();
        excludedProperties.put("rideId", Map.of("type", "integer", "minimum", 1));
        excludedProperties.put("rideName", Map.of("type", "string"));
        excludedProperties.put("visitMemberId", Map.of("type", "integer", "minimum", 1));
        excludedProperties.put("priority", Map.of("type", "string", "enum", List.of("MUST_RIDE", "PREFERRED", "OPTIONAL")));
        excludedProperties.put("reasonCode", Map.of("type", "string", "enum", List.of("INSUFFICIENT_TIME", "CLOSED", "NOT_ELIGIBLE", "MISSING_WAIT_DATA", "MISSING_ROUTE", "SCHEDULE_CONFLICT", "GOAL_CONFLICT", "PREFERENCE_CONFLICT")));
        excludedProperties.put("reasonMessage", Map.of("type", "string"));
        Map<String, Object> excludedSchema = Map.of("type", "object", "properties", excludedProperties, "required", List.copyOf(excludedProperties.keySet()), "additionalProperties", false);

        planProperties.put("excludedRides", Map.of("type", "array", "items", excludedSchema));
        planProperties.put("feasible", Map.of("type", "boolean"));
        planProperties.put("activities", Map.of("type", "array", "items", activitySchema));
        planProperties.put("warnings", Map.of("type", "array", "items", Map.of("type", "string", "minLength", 1, "maxLength", 500)));
        planProperties.put("infeasibilityReason", Map.of("type", List.of("string", "null"), "maxLength", 1000));

        Map<String, Object> planSchema = Map.of("type", "object", "properties", planProperties, "required", List.copyOf(planProperties.keySet()), "additionalProperties", false);

        return jsonMapper.valueToTree(planSchema);
    }
}