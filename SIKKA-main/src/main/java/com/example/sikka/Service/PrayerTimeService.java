package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrayerTimeService {

    @Value("${aladhan.calculation-method:4}")
    private int calculationMethod;

    @Value("${aladhan.timeout-seconds:15}")
    private long timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public Map<String, LocalDateTime> getPrayerTimes(LocalDate visitDate, Double latitude, Double longitude) {

        if (visitDate == null) {
            throw new ApiException("Visit date is required for prayer times");
        }

        if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new ApiException("A valid park latitude is required");
        }

        if (longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new ApiException("A valid park longitude is required");
        }

        if (timeoutSeconds <= 0 || calculationMethod < 0 || calculationMethod == 99) {
            throw new ApiException("Prayer time settings are invalid or require unsupported custom parameters");
        }

        String requestedDate = visitDate.format(dateFormatter);
        String url = "https://api.aladhan.com/v1/timings/" + requestedDate + "?latitude=" + latitude + "&longitude=" + longitude + "&method=" + calculationMethod + "&school=0&timezonestring=Asia%2FRiyadh&iso8601=false";

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds)).header("Accept", "application/json").GET().build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 429) {
                throw new ApiException("Prayer time service rate limit reached; try again later");
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException("Prayer time service failed with HTTP status " + response.statusCode());
            }

            JsonNode result = jsonMapper.readTree(response.body());

            if (result == null || result.path("code").asInt() != 200) {
                throw new ApiException("Prayer time service returned an unsuccessful response");
            }

            JsonNode data = result.path("data");

            if (!requestedDate.equals(data.path("date").path("gregorian").path("date").asString())) {
                throw new ApiException("Prayer time response does not match the requested date");
            }

            if (!"Asia/Riyadh".equals(data.path("meta").path("timezone").asString())) {
                throw new ApiException("Prayer time response has an unexpected timezone");
            }

            if (data.path("meta").path("method").path("id").asInt(-1) != calculationMethod) {
                throw new ApiException("Prayer time response uses an unexpected calculation method");
            }

            JsonNode timings = data.path("timings");

            if (!timings.isObject()) {
                throw new ApiException("Prayer times are missing from the response");
            }

            Map<String, LocalDateTime> prayerTimes = new LinkedHashMap<>();
            List<String> prayerNames = List.of("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha");
            LocalDateTime previousTime = null;

            for (String prayerName : prayerNames) {
                String value = timings.path(prayerName).asString();

                if (value == null || !value.matches("^\\d{2}:\\d{2}(\\s+\\([^)]*\\))?$")) {
                    throw new ApiException("Prayer time is missing or invalid for " + prayerName);
                }

                LocalTime prayerTime = LocalTime.parse(value.substring(0, 5));
                LocalDateTime prayerDateTime = visitDate.atTime(prayerTime);

                if (previousTime != null && !prayerDateTime.isAfter(previousTime)) {
                    throw new ApiException("Prayer times are not in a valid chronological order");
                }

                prayerTimes.put(prayerName, prayerDateTime);
                previousTime = prayerDateTime;
            }

            return prayerTimes;

        } catch (HttpTimeoutException exception) {
            throw new ApiException("Prayer time service timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException("Prayer time request was interrupted");
        } catch (JacksonException exception) {
            throw new ApiException("Unable to read the prayer time response");
        } catch (DateTimeParseException exception) {
            throw new ApiException("Prayer time service returned an invalid time");
        } catch (IOException exception) {
            throw new ApiException("Unable to communicate with the prayer time service");
        }
    }
}