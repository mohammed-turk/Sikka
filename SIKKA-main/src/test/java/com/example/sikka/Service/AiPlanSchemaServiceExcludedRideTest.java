package com.example.sikka.Service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

class AiPlanSchemaServiceExcludedRideTest {
    @Test
    void planSchemaIncludesStructuredExcludedRides() {
        JsonNode schema = new AiPlanSchemaService().getPlanOutputSchema();
        JsonNode excluded = schema.path("properties").path("excludedRides");
        assertEquals("array", excluded.path("type").asString());
        JsonNode fields = excluded.path("items").path("properties");
        assertEquals("integer", fields.path("rideId").path("type").asString());
        assertEquals("integer", fields.path("visitMemberId").path("type").asString());
        assertEquals("string", fields.path("reasonCode").path("type").asString());
        assertTrue(schema.path("required").toString().contains("excludedRides"));
    }
}
