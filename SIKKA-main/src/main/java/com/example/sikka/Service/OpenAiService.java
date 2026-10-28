package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class OpenAiService {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.timeout-seconds:90}")
    private long timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final JsonMapper jsonMapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public JsonNode generateStructuredResponse(String instructions, String inputJson, JsonNode outputSchema) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException("OpenAI API key is not configured");
        }

        if (model == null || model.isBlank() || timeoutSeconds <= 0) {
            throw new ApiException("OpenAI settings are invalid");
        }

        if (instructions == null || instructions.isBlank() || inputJson == null || inputJson.isBlank()) {
            throw new ApiException("AI instructions and input are required");
        }

        if (outputSchema == null || !outputSchema.isObject()) {
            throw new ApiException("AI output schema must be a JSON object");
        }

        try {
            JsonNode input = jsonMapper.readTree(inputJson);

            if (input == null || !input.isObject()) {
                throw new ApiException("AI input must be a JSON object");
            }

            Map<String, Object> format = Map.of("type", "json_schema", "name", "sikka_plan", "strict", true, "schema", outputSchema);
            Map<String, Object> body = Map.of("model", model, "instructions", instructions, "input", inputJson, "text", Map.of("format", format), "store", false, "max_output_tokens", 12000);

            String requestBody = jsonMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.openai.com/v1/responses")).timeout(Duration.ofSeconds(timeoutSeconds)).header("Authorization", "Bearer " + apiKey.trim()).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8)).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ApiException("OpenAI authentication or access failed; check the API key and model access");
            }

            if (response.statusCode() == 429) {
                throw new ApiException("OpenAI quota or rate limit reached; check API billing and limits");
            }

            if (response.statusCode() >= 500) {
                throw new ApiException("OpenAI is temporarily unavailable");
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException("OpenAI request failed with HTTP status " + response.statusCode());
            }

            JsonNode result = jsonMapper.readTree(response.body());

            if (result == null || !result.isObject()) {
                throw new ApiException("OpenAI returned an invalid response");
            }

            if (!"completed".equals(result.path("status").asString())) {
                throw new ApiException("OpenAI did not complete the response; no generated plan was accepted");
            }

            JsonNode output = result.path("output");

            if (!output.isArray()) {
                throw new ApiException("OpenAI response output is missing");
            }

            StringBuilder generatedText = new StringBuilder();

            for (JsonNode item : output) {

                if (!"message".equals(item.path("type").asString())) {
                    continue;
                }

                JsonNode content = item.path("content");

                if (!content.isArray()) {
                    continue;
                }

                for (JsonNode part : content) {

                    if ("refusal".equals(part.path("type").asString())) {
                        throw new ApiException("OpenAI declined to generate this response");
                    }

                    if ("output_text".equals(part.path("type").asString())) {
                        generatedText.append(part.path("text").asString());
                    }
                }
            }

            if (generatedText.toString().isBlank()) {
                throw new ApiException("OpenAI returned an empty response");
            }

            JsonNode generatedResult = jsonMapper.readTree(generatedText.toString());

            if (generatedResult == null || !generatedResult.isObject()) {
                throw new ApiException("OpenAI generated an invalid JSON object");
            }

            return generatedResult;

        } catch (HttpTimeoutException exception) {
            throw new ApiException("OpenAI request timed out; generation outcome must be checked before retrying");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException("OpenAI request was interrupted");
        } catch (JacksonException exception) {
            throw new ApiException("Unable to process AI request or response JSON");
        } catch (IOException exception) {
            throw new ApiException("Unable to communicate with OpenAI");
        }
    }
}