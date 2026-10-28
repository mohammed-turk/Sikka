package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
@Slf4j
public class MoyasarService {

    @Value("${moyasar.secret-key:}")
    private String secretKey;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final JsonMapper mapper = JsonMapper.builder().build();

    // fetches the payment from Moyasar itself; we never trust what the frontend says about a payment
    public JsonNode fetchPayment(String paymentId) {
        if (secretKey.isBlank())
            throw new ApiException("payment provider is not configured");
        if (paymentId == null || !paymentId.matches("^[A-Za-z0-9_-]{1,100}$"))
            throw new ApiException("invalid payment id");

        try {
            // Moyasar uses Basic auth: secret key as the username, empty password
            String auth = Base64.getEncoder()
                    .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.moyasar.com/v1/payments/" + paymentId))
                    .header("Authorization", "Basic " + auth)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Moyasar fetch payment failed ({}): {}", response.statusCode(), response.body());
                throw new ApiException("payment not found");
            }
            return mapper.readTree(response.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not reach Moyasar: {}", e.getMessage());
            throw new ApiException("could not reach the payment provider");
        }
    }
}