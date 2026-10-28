package com.example.sikka;

import com.example.sikka.Model.Account;
import com.example.sikka.Repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.datasource.url=jdbc:h2:mem:sikka_front_session;MODE=MySQL;DB_CLOSE_DELAY=-1"})
class FrontendSessionHttpTest {
    @LocalServerPort private int port;
    @Autowired private jakarta.servlet.ServletContext servletContext;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void keepBrowserSessionAndInvalidateItOnLogout() throws Exception {
        assertEquals(120, servletContext.getSessionTimeout());
        Account account = new Account();
        account.setName("Session QA");
        account.setEmail("session.qa@example.test");
        account.setPasswordHash(passwordEncoder.encode("SessionTest123!"));
        account.setRole("USER");
        account.setStatus("ACTIVE");
        account.setEmailVerified(true);
        accountRepository.save(account);
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        URI me = URI.create("http://localhost:" + port + "/api/v1/account/me");
        assertEquals(401, browser.send(HttpRequest.newBuilder(me).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        URI login = URI.create("http://localhost:" + port + "/api/v1/auth/login");
        HttpResponse<String> wrong = browser.send(HttpRequest.newBuilder(login).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("username=session.qa%40example.test&password=wrong")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, wrong.statusCode());
        HttpResponse<String> authenticated = browser.send(HttpRequest.newBuilder(login).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("username=session.qa%40example.test&password=SessionTest123%21")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, authenticated.statusCode());
        assertTrue(authenticated.headers().allValues("set-cookie").stream().anyMatch(value -> value.contains("JSESSIONID=") && value.contains("HttpOnly")));
        HttpResponse<String> current = browser.send(HttpRequest.newBuilder(me).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, current.statusCode());
        assertTrue(current.body().contains("session.qa@example.test"));
        assertFalse(current.body().contains("passwordHash"));
        URI logout = URI.create("http://localhost:" + port + "/api/v1/auth/logout");
        assertEquals(200, browser.send(HttpRequest.newBuilder(logout).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401, browser.send(HttpRequest.newBuilder(me).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test
    void publicMapPlaceFiltersDoNotExposeAdministrativeOperations() throws Exception {
        HttpClient guest = HttpClient.newHttpClient();
        assertEquals(200, guest.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ride/catalog")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        URI places = URI.create("http://localhost:" + port + "/api/v1/place/by-kind?kind=RESTAURANT");
        assertEquals(200, guest.send(HttpRequest.newBuilder(places).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        URI admin = URI.create("http://localhost:" + port + "/api/v1/place/get");
        assertEquals(401, guest.send(HttpRequest.newBuilder(admin).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        URI add = URI.create("http://localhost:" + port + "/api/v1/place/add");
        assertEquals(401, guest.send(HttpRequest.newBuilder(add).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
