package com.example.sikka.Service;

import com.example.sikka.Model.Ride;
import com.example.sikka.Repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueAnalyticsSyncService {
    private final RideRepository rideRepository;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    @Value("${sikka.queue-analytics.enabled:false}")
    private boolean enabled;
    @Value("${sikka.queue-analytics.url:https://queueanalytics-production.up.railway.app}")
    private String baseUrl;

    @Scheduled(fixedDelayString = "${sikka.queue-analytics.interval-ms:300000}", initialDelayString = "${sikka.queue-analytics.initial-delay-ms:5000}")
    public void synchronizeAnalyticsReadings() {
        if (!enabled) return;
        try {
            JsonNode catalogue = fetchJson("/api/v1/rides");
            List<Ride> rides = rideRepository.findAll();
            applyCatalogue(catalogue, rides, Instant.now());
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Riyadh"));
            for (Ride ride : rides) {
                if (ride.getQueueAnalyticsRideId() == null || ride.getAnalyticsPatternsFetchedAt() != null && ride.getAnalyticsPatternsFetchedAt().isAfter(LocalDateTime.now().minusHours(24))) continue;
                JsonNode patterns = fetchJson("/api/v1/rides/" + ride.getQueueAnalyticsRideId() + "/patterns?from=" + today.minusDays(60) + "&to=" + today);
                if (patterns.path("sourceRideId").asLong() != ride.getQueueAnalyticsRideId() || !patterns.path("allDays").isArray() || !patterns.path("weekdays").isObject()) throw new IllegalArgumentException("Invalid analytics historical patterns");
                ride.setAnalyticsPatternsJson(jsonMapper.writeValueAsString(patterns));
                ride.setAnalyticsPatternsFetchedAt(LocalDateTime.now());
                rideRepository.save(ride);
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Queue Analytics synchronization unavailable; retaining observations and labeled planning estimates ({})", exception.getClass().getSimpleName());
        }
    }

    private JsonNode fetchJson(String path) throws Exception {
        URI uri = URI.create(baseUrl.replaceAll("/+$", "") + path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().length() > 2000000) throw new IllegalStateException("Analytics response unavailable or too large");
        return jsonMapper.readTree(response.body());
    }

    public void applyCatalogue(JsonNode catalogue, List<Ride> rides, Instant now) {
        if (catalogue == null || !catalogue.isArray() || catalogue.isEmpty()) throw new IllegalArgumentException("Analytics catalogue must contain rides");
        Map<Long, JsonNode> byId = new HashMap<>();
        Map<String, List<JsonNode>> byName = new HashMap<>();
        for (JsonNode row : catalogue) {
            if (row.has("parkId") && row.path("parkId").asInt() != 335) throw new IllegalArgumentException("Analytics park identity mismatch");
            if (!row.path("sourceRideId").isIntegralNumber() || !row.path("sourceRideId").canConvertToLong() || row.path("sourceRideId").asLong() <= 0 || row.path("name").asString("").isBlank()) throw new IllegalArgumentException("Invalid analytics ride identity");
            if (byId.put(row.path("sourceRideId").asLong(), row) != null) throw new IllegalArgumentException("Duplicate analytics ride ID");
            byName.computeIfAbsent(normalizedName(row.path("name").asString()), key -> new ArrayList<>()).add(row);
        }
        Set<Long> usedIds = new HashSet<>();
        for (Ride ride : rides) if (ride.getQueueAnalyticsRideId() != null && !usedIds.add(ride.getQueueAnalyticsRideId())) throw new IllegalArgumentException("Duplicate stored analytics ride mapping");
        for (Ride ride : rides) {
            JsonNode row = ride.getQueueAnalyticsRideId() == null ? null : byId.get(ride.getQueueAnalyticsRideId());
            if (ride.getQueueAnalyticsRideId() == null) {
                String key = normalizedName(ride.getSourceRideId() == null ? ride.getName() : ride.getSourceRideId());
                List<JsonNode> matches = byName.getOrDefault(key, List.of());
                long localMatches = rides.stream().filter(other -> normalizedName(other.getSourceRideId() == null ? other.getName() : other.getSourceRideId()).equals(key)).count();
                if (matches.size() != 1 || localMatches != 1 || usedIds.contains(matches.get(0).path("sourceRideId").asLong())) continue;
                row = matches.get(0);
                ride.setQueueAnalyticsRideId(row.path("sourceRideId").asLong());
                usedIds.add(ride.getQueueAnalyticsRideId());
            }
            if (row == null) continue;
            JsonNode latest = row.path("latest");
            if (isFreshReading(latest, now)) {
                Instant observed = Instant.parse(latest.path("sourceUpdatedAt").asString());
                LocalDateTime observedLocal = LocalDateTime.ofInstant(observed, ZoneId.systemDefault());
                if (ride.getWaitObservedAt() == null || !observedLocal.isBefore(ride.getWaitObservedAt())) {
                    ride.setWaitMinutes(latest.path("waitMinutes").asInt());
                    ride.setWaitObservedAt(observedLocal);
                    ride.setWaitSource("QUEUE_ANALYTICS_LIVE");
                    if (!"ADMIN".equals(ride.getAvailabilitySource()) || "OPEN".equals(ride.getStatus())) {
                        ride.setStatus(latest.path("isOpen").asBoolean() ? "OPEN" : "CLOSED");
                        ride.setAvailabilitySource("QUEUE_ANALYTICS_LIVE");
                    }
                }
            }
            rideRepository.save(ride);
        }
    }

    public boolean isFreshReading(JsonNode reading, Instant now) {
        if (!reading.path("isValid").isBoolean() || !reading.path("isValid").asBoolean() || !reading.path("isStale").isBoolean() || reading.path("isStale").asBoolean() || !reading.path("isOpen").isBoolean() || !reading.path("waitMinutes").isIntegralNumber() || !reading.path("waitMinutes").canConvertToInt() || reading.path("waitMinutes").asInt() < 0 || reading.path("waitMinutes").asLong() > 1440) return false;
        try {
            Instant observed = Instant.parse(reading.path("sourceUpdatedAt").asString());
            Instant collected = Instant.parse(reading.path("collectedAt").asString());
            return !observed.isBefore(now.minusSeconds(600)) && !collected.isBefore(now.minusSeconds(600)) && !observed.isAfter(now.plusSeconds(30)) && !collected.isAfter(now.plusSeconds(30));
        } catch (Exception exception) {
            return false;
        }
    }

    private String normalizedName(String value) {
        return value == null ? "" : Normalizer.normalize(value.replace("&", "and"), Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
