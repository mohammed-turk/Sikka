package com.example.sikka.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class N8nNotificationService {

    @Value("${sikka.notifications.n8n-url:}")
    private String webhookUrl;

    @Value("${sikka.notifications.n8n-key:}")
    private String webhookKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public boolean sendNotification(String eventKey, String type,
                                    String recipientEmail, String title,
                                    String message) {

        if (webhookUrl.isBlank() || webhookKey.isBlank()) {
            log.warn("n8n notifications are not configured");
            return false;
        }

        if (eventKey == null || eventKey.isBlank()
                || type == null || type.isBlank()
                || recipientEmail == null || recipientEmail.isBlank()
                || title == null || title.isBlank()
                || message == null || message.isBlank()) {

            log.warn("Notification contains missing required fields");
            return false;
        }

        try {
            Map<String, String> body = Map.of(
                    "eventKey", eventKey,
                    "type", type,
                    "recipientEmail", recipientEmail.trim(),
                    "title", title,
                    "message", message
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("X-SIKKA-KEY", webhookKey)
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            jsonMapper.writeValueAsString(body)
                    ))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                log.warn("n8n notification failed with HTTP status {}",
                        response.statusCode());
                return false;
            }

            JsonNode result = jsonMapper.readTree(response.body());

            if (result == null
                    || !result.path("success").isBoolean()
                    || !result.path("success").asBoolean()
                    || !eventKey.equals(result.path("eventKey").asText())) {

                log.warn("n8n returned an unexpected notification response");
                return false;
            }

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("n8n notification request was interrupted");
            return false;

        } catch (Exception e) {
            log.warn("n8n notification request failed: {}",
                    e.getClass().getSimpleName());
            return false;
        }
    }
}