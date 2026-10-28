package com.example.sikka.Service;

import com.example.sikka.Model.Ride;
import com.example.sikka.Repository.RideRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueueAnalyticsPlanningTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RideRepository repository = mock(RideRepository.class);
    private final QueueAnalyticsSyncService sync = new QueueAnalyticsSyncService(repository, mapper);

    private JsonNode reading(boolean open, int wait, Instant observed) {
        return mapper.valueToTree(Map.of("isValid", true, "isStale", false, "isOpen", open, "waitMinutes", wait, "sourceUpdatedAt", observed.toString(), "collectedAt", observed.toString()));
    }

    @Test
    void synchronizeRealHttpResponsesAndCacheHistoryInsteadOfRefetchingItEveryPoll() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        Ride ride = new Ride();
        ride.setSourceRideId("adrena-line");
        when(repository.findAll()).thenReturn(List.of(ride));
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/api/v1/rides", exchange -> {
            requests.incrementAndGet();
            Object payload = exchange.getRequestURI().getPath().endsWith("/patterns") ? Map.of("sourceRideId", 16264, "allDays", List.of(), "weekdays", Map.of()) : List.of(Map.of("sourceRideId", 16264, "parkId", 335, "name", "Adrena-Line", "latest", reading(true, 7, Instant.now().minusSeconds(30))));
            byte[] body = mapper.writeValueAsString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(sync, "enabled", true);
            org.springframework.test.util.ReflectionTestUtils.setField(sync, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            sync.synchronizeAnalyticsReadings();
            assertEquals(16264L, ride.getQueueAnalyticsRideId());
            assertEquals(7, ride.getWaitMinutes());
            assertEquals("OPEN", ride.getStatus());
            assertNotNull(ride.getAnalyticsPatternsJson());
            assertEquals(2, requests.get());
            sync.synchronizeAnalyticsReadings();
            assertEquals(3, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void matchNumericSourceIdOnceAndKeepFreshClosedReadingsIncludingZeroWait() {
        Ride ride = new Ride();
        ride.setId(3);
        ride.setSourceRideId("falcons-flight");
        Instant now = Instant.now();
        JsonNode feed = mapper.valueToTree(List.of(Map.of("sourceRideId", 801L, "name", "Falcon's Flight", "latest", reading(false, 0, now.minusSeconds(60)))));
        sync.applyCatalogue(feed, List.of(ride), now);
        assertEquals(801L, ride.getQueueAnalyticsRideId());
        assertEquals("CLOSED", ride.getStatus());
        assertEquals(0, ride.getWaitMinutes());
        assertEquals("QUEUE_ANALYTICS_LIVE", ride.getWaitSource());
        sync.applyCatalogue(mapper.valueToTree(List.of(Map.of("sourceRideId", 802L, "name", "Falcon's Flight", "latest", reading(true, 20, now)))), List.of(ride), now);
        assertEquals(801L, ride.getQueueAnalyticsRideId());
        assertEquals("CLOSED", ride.getStatus());
    }

    @Test
    void rejectStaleFutureAndMalformedReadingsInsteadOfMarkingGamesOpen() {
        Instant now = Instant.now();
        assertFalse(sync.isFreshReading(reading(true, 10, now.minusSeconds(1200)), now));
        assertFalse(sync.isFreshReading(reading(true, 10, now.plusSeconds(60)), now));
        assertFalse(sync.isFreshReading(mapper.valueToTree(Map.of("isOpen", true, "waitMinutes", 0)), now));
        assertTrue(sync.isFreshReading(reading(true, 0, now.minusSeconds(60)), now));
    }

    @Test
    void ambiguousNamesNeverCreateAConnectionToTheWrongRide() {
        Ride ride = new Ride();
        ride.setSourceRideId("windrider");
        JsonNode feed = mapper.valueToTree(List.of(Map.of("sourceRideId", 11, "name", "Windrider"), Map.of("sourceRideId", 12, "name", "Windrider")));
        sync.applyCatalogue(feed, List.of(ride), Instant.now());
        assertNull(ride.getQueueAnalyticsRideId());
        assertEquals("UNAVAILABLE", ride.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void unknownAvailabilityIsProvisionalWhileFreshClosuresAndAdminBlocksAreExcluded() {
        Ride ride = new Ride();
        assertTrue(PlanningAvailability.canPlanRide(ride, LocalDate.now()));
        assertEquals("UNAVAILABLE", ride.getStatus());
        ride.setStatus("CLOSED");
        ride.setAvailabilitySource("QUEUE_ANALYTICS_LIVE");
        ride.setWaitObservedAt(LocalDateTime.now().minusMinutes(1));
        assertFalse(PlanningAvailability.canPlanRide(ride, LocalDate.now()));
        assertTrue(PlanningAvailability.canPlanRide(ride, LocalDate.now().plusDays(1)));
        ride.setWaitObservedAt(LocalDateTime.now().minusHours(1));
        assertTrue(PlanningAvailability.canPlanRide(ride, LocalDate.now()));
        ride.setStatus("UNAVAILABLE");
        ride.setAvailabilitySource("ADMIN");
        assertFalse(PlanningAvailability.canPlanRide(ride, LocalDate.now().plusDays(1)));
    }

    @Test
    void futureVisitUsesItsWeekdayHistoryAndFallsBackToLabeledFifteenMinutes() throws Exception {
        Ride ride = new Ride();
        ride.setId(1);
        ride.setWaitMinutes(0);
        ride.setWaitObservedAt(LocalDateTime.now().minusMinutes(1));
        ride.setWaitSource("QUEUE_ANALYTICS_LIVE");
        LocalDate date = LocalDate.now().plusDays(1);
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) hours.add(Map.of("hour", hour, "daysCount", 2, "sampleCount", 12, "averageWait", 27));
        ride.setAnalyticsPatternsJson(mapper.writeValueAsString(Map.of("weekdays", Map.of(date.getDayOfWeek().name(), hours), "allDays", hours)));
        when(repository.findRideById(1)).thenReturn(ride);
        RideService rides = new RideService(repository, null, null, null, null, mapper);
        Map<String, Object> estimated = rides.getRideSnapshotForVisit(1, date, LocalTime.of(16, 0), 120);
        assertEquals(27, estimated.get("planningWaitMinutes"));
        assertEquals("QUEUE_ANALYTICS_HISTORICAL_ESTIMATE", estimated.get("waitSource"));
        assertEquals(true, estimated.get("waitIsEstimate"));
        ride.setAnalyticsPatternsJson(null);
        estimated = rides.getRideSnapshotForVisit(1, date, LocalTime.of(16, 0), 120);
        assertEquals(15, estimated.get("planningWaitMinutes"));
        assertEquals("SIKKA_DEFAULT_ESTIMATE", estimated.get("waitSource"));
        assertEquals("UNAVAILABLE", ride.getStatus());
    }
}
