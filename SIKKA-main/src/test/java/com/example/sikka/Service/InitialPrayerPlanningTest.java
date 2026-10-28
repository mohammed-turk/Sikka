package com.example.sikka.Service;

import com.example.sikka.DtoOut.AiPlanDtoOut;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitialPrayerPlanningTest {
    @Test
    void expectedArrivalReservesVerifiedPrayerBeforeSelectingRides() {
        JsonMapper mapper = JsonMapper.builder().build();
        VisitPlanService service = mock(VisitPlanService.class, CALLS_REAL_METHODS);
        PrayerTimeService prayer = mock(PrayerTimeService.class);
        ReflectionTestUtils.setField(service, "serverItineraryService", new ServerItineraryService());
        ReflectionTestUtils.setField(service, "arrivalPrayerScheduleService", new ArrivalPrayerScheduleService());
        ReflectionTestUtils.setField(service, "prayerTimeService", prayer);
        ReflectionTestUtils.setField(service, "participantJsonMapper", mapper);
        ReflectionTestUtils.setField(service, "parkLatitude", 24.5866431);
        ReflectionTestUtils.setField(service, "parkLongitude", 46.3310353);
        LocalDate date = LocalDate.of(2026, 10, 28);
        when(prayer.getPrayerTimes(eq(date), anyDouble(), anyDouble())).thenReturn(Map.of("Fajr", date.atTime(5, 0), "Dhuhr", date.atTime(12, 0), "Asr", date.atTime(16, 15), "Maghrib", date.atTime(17, 30), "Isha", date.atTime(19, 0)));
        ObjectNode input = ReflectionTestUtils.invokeMethod(new ServerItineraryServiceTest(), "input", 35, false);
        input.put("visitDate", date.toString());
        input.put("expectedArrivalTime", "16:00");
        input.put("includePrayer", true);
        input.set("places", mapper.valueToTree(List.of(Map.of("id", 1, "kind", "PRAYER_ROOM", "status", "OPEN", "nodeKey", "PLACE:1"))));
        List<Map<String, Object>> routes = new ArrayList<>();
        for (String from : List.of("ENTRANCE", "RIDE:1", "RIDE:2", "RIDE:3", "PLACE:1")) for (String to : List.of("ENTRANCE", "RIDE:1", "RIDE:2", "RIDE:3", "PLACE:1")) routes.add(Map.of("fromNodeKey", from, "toNodeKey", to, "minutes", from.equals(to) ? 0 : 2));
        ((ObjectNode) input.path("walking")).set("routes", mapper.valueToTree(routes));
        AiPlanDtoOut result = ReflectionTestUtils.invokeMethod(service, "selectInitialPlanWithExpectedPrayer", input);
        assertTrue(result.getFeasible());
        assertTrue(result.getActivities().size() < 3);
        input.set("prayerEvents", mapper.valueToTree(List.of(Map.of("relativePrayerMinute", 15, "activityMinutes", 15))));
        assertNotNull(new ArrivalPrayerScheduleService().scheduleApprovedActivitiesWithPrayer(input, result.getActivities()));
        assertEquals(35, input.path("durationMinutes").asInt());
    }
}
