package com.example.sikka;

import com.example.sikka.Model.Account;
import com.example.sikka.Repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.datasource.url=jdbc:h2:mem:sikka-http;MODE=MySQL;DB_CLOSE_DELAY=-1")
class TeamFlowsHttpTest {
    @LocalServerPort private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void allowAnonymousParkExplorationAndRequireLoginForPlans() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> map = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/map/get/data")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, map.statusCode());
        assertTrue(map.body().contains("SIKKA_IMAGE_ROUTE_V1"));
        HttpResponse<String> rides = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ride/get")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, rides.statusCode());
        HttpResponse<String> history = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/plan-history/my-history")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, history.statusCode());
    }

    @Test
    void useAuthenticatedAccountForPlanHistoryAndRejectUserMapImports() throws Exception {
        String email = "principal-" + java.util.UUID.randomUUID() + "@example.com";
        String password = java.util.UUID.randomUUID().toString();
        Account account = new Account();
        account.setName("Principal review");
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole("USER");
        account.setStatus("ACTIVE");
        accountRepository.save(account);
        String authorization = "Basic " + Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> history = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/plan-history/my-history")).header("Authorization", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, history.statusCode());
        assertEquals("[]", history.body());
        HttpResponse<String> mapImport = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/map/import/rides/" + account.getId())).header("Authorization", authorization).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, mapImport.statusCode());
    }
}
