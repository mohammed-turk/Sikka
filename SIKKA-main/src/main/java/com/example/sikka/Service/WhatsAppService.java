package com.example.sikka.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@Slf4j
public class WhatsAppService {

    @Value("${ultramsg.instance-id:}")
    private String instanceId;

    @Value("${ultramsg.token:}")
    private String token;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // never throws: a failed message must not break the request that triggered it
    // "to" is an international number, for example 9665XXXXXXXX (any + or spaces are removed)
    public boolean sendMessage(String to, String body) {
        if (instanceId.isBlank() || token.isBlank()) {
            log.warn("WhatsApp is not configured (ULTRAMSG_INSTANCE_ID / ULTRAMSG_TOKEN)");
            return false;
        }
        try {
            String form = "token=" + encode(token)
                    + "&to=" + encode(to.replaceAll("[^0-9]", ""))
                    + "&body=" + encode(body);

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.ultramsg.com/" + instanceId + "/messages/chat"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("WhatsApp send failed ({}): {}", response.statusCode(), response.body());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp message to {}: {}", to, e.getMessage());
            return false;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}