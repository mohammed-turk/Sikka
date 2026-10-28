package com.example.sikka.Controller;

import com.example.sikka.Model.Account;
import com.example.sikka.Service.GoogleCalendarService;
import com.example.sikka.Service.GoogleCalendarOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/google-calendar")
public class GoogleCalendarController {

    private final GoogleCalendarService googleCalendarService;
    private final GoogleCalendarOAuthService googleCalendarOAuthService;

    @GetMapping("/manual/{visitId}")
    public ResponseEntity<?> getManualCalendarLink(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.status(200).body(Map.of("url", googleCalendarService.createManualCalendarLink(account.getId(), visitId)));
    }
    @GetMapping("/apple/{visitId}")
    public ResponseEntity<?> downloadAppleCalendar(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        String file = googleCalendarService.createAppleCalendarFile(account.getId(), visitId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sikka-visit-" + visitId + ".ics\"").contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8")).body(file);
    }
    @GetMapping("/oauth/connect")
    public ResponseEntity<?> connect(@AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(Map.of("url", googleCalendarOAuthService.authorizationUrl(account.getId())));
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<?> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", "Google Calendar access was not granted"));
        googleCalendarOAuthService.callback(state, code);
        return ResponseEntity.ok(Map.of("message", "Google Calendar connected"));
    }

    @GetMapping("/oauth/status")
    public ResponseEntity<?> status(@AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(Map.of("connected", googleCalendarOAuthService.connected(account.getId())));
    }

    @PostMapping("/sync/{visitId}")
    public ResponseEntity<?> sync(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        return ResponseEntity.ok(Map.of("url", googleCalendarOAuthService.syncVisit(account.getId(), visitId)));
    }

    @DeleteMapping("/sync/cancelled/{visitId}")
    public ResponseEntity<?> removeCancelledVisit(@AuthenticationPrincipal Account account, @PathVariable Integer visitId) {
        googleCalendarOAuthService.removeCancelledVisit(account.getId(), visitId);
        return ResponseEntity.ok(Map.of("message", "Cancelled visit removed from Google Calendar"));
    }

    @DeleteMapping("/oauth/disconnect")
    public ResponseEntity<?> disconnect(@AuthenticationPrincipal Account account) {
        googleCalendarOAuthService.disconnect(account.getId());
        return ResponseEntity.ok(Map.of("message", "Google Calendar disconnected"));
    }
}
