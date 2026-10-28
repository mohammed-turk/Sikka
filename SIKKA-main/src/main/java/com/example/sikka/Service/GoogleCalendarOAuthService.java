package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.*;
import com.example.sikka.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoogleCalendarOAuthService {
    private final GoogleCalendarOAuthStateRepository stateRepository;
    private final GoogleCalendarConnectionRepository connectionRepository;
    private final GoogleCalendarEventRepository eventRepository;
    private final AccountRepository accountRepository;
    private final VisitRepository visitRepository;
    private final VisitMemberRepository visitMemberRepository;
    private final VisitPlanRepository visitPlanRepository;
    private final RestClient client = RestClient.create();
    private final SecureRandom random = new SecureRandom();
    private static final DateTimeFormatter GOOGLE_EVENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Value("${sikka.google.client-id:}")
    private String clientId;
    @Value("${sikka.google.client-secret:}")
    private String clientSecret;
    @Value("${sikka.google.redirect-uri:http://localhost:8080/api/v1/google-calendar/oauth/callback}")
    private String redirectUri;
    @Value("${sikka.google.encryption-key:}")
    private String encryptionKey;

    private void configured() {
        if (clientId.isBlank() || clientSecret.isBlank() || encryptionKey.isBlank()) {
            throw new ApiException("Google Calendar OAuth is not configured");
        }
        if (Base64.getDecoder().decode(encryptionKey).length != 32) {
            throw new ApiException("Calendar encryption key must contain 32 bytes");
        }
    }

    public String authorizationUrl(Integer accountId) {
        configured();
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Account account = accountRepository.findAccountById(accountId);
        if (account == null) throw new ApiException("Account not found");
        GoogleCalendarOAuthState record = new GoogleCalendarOAuthState();
        record.setStateHash(hashState(state));
        record.setAccount(account);
        record.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        stateRepository.save(record);
        String scope = "https://www.googleapis.com/auth/calendar.events";
        return "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=" + enc(clientId) + "&redirect_uri=" + enc(redirectUri) + "&scope=" + enc(scope) + "&access_type=offline&prompt=consent&state=" + enc(state);
    }

    public void callback(String state, String code) {
        configured();
        if (state == null || state.isBlank()) throw new ApiException("Missing Google Calendar authorization state");
        String stateHash = hashState(state);
        GoogleCalendarOAuthState pending = stateRepository.findById(stateHash).orElseThrow(() -> new ApiException("Invalid or expired Google Calendar authorization state"));
        if (stateRepository.consume(stateHash, LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new ApiException("Google Calendar authorization state has expired or already been used");
        }
        Integer accountId = pending.getAccount().getId();
        if (code == null || code.isBlank()) {
            throw new ApiException("Google authorization code is missing");
        }
        Map<?, ?> tokens = client.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("code=" + enc(code) + "&client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret) + "&redirect_uri=" + enc(redirectUri) + "&grant_type=authorization_code")
                .retrieve().body(Map.class);
        Object refreshToken = tokens == null ? null : tokens.get("refresh_token");
        if (!(refreshToken instanceof String token) || token.isBlank()) {
            throw new ApiException("Google did not return a refresh token");
        }
        GoogleCalendarConnection connection = connectionRepository.findById(accountId).orElseGet(GoogleCalendarConnection::new);
        Account account = accountRepository.findAccountById(accountId);
        if (account == null) throw new ApiException("Account not found");
        connection.setAccount(account);
        connection.setEncryptedRefreshToken(encrypt(token));
        connectionRepository.save(connection);
    }

    public boolean connected(Integer accountId) {
        return connectionRepository.existsById(accountId);
    }

    @Transactional
    public void disconnect(Integer accountId) {
        eventRepository.deleteAllByAccount_Id(accountId);
        connectionRepository.deleteById(accountId);
    }

    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public List<Integer> getVisitCalendarAccounts(Integer visitId, boolean cancellation) {
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null) return List.of();
        Set<Integer> accounts = new TreeSet<>();
        if (visit.getOrganizer() != null) accounts.add(visit.getOrganizer().getId());
        for (VisitMember member : visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING")) {
            if (member.getVisitorProfile() != null) accounts.add(member.getVisitorProfile().getAccount().getId());
        }
        if (cancellation) for (GoogleCalendarEvent event : eventRepository.findAllByVisit_Id(visitId)) accounts.add(event.getAccount().getId());
        return new ArrayList<>(accounts);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public String syncVisit(Integer accountId, Integer visitId) {
        configured();
        GoogleCalendarConnection connection = connectionRepository.findById(accountId).orElseThrow(() -> new ApiException("Connect Google Calendar first"));
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null) throw new ApiException("Visit not found");
        if (visit.getVisitDate() == null) throw new ApiException("Visit date is required");
        boolean organizer = visit.getOrganizer() != null && accountId.equals(visit.getOrganizer().getId());
        VisitMember participant = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId);
        boolean member = participant != null && "GOING".equals(participant.getRsvpStatus());
        if (!organizer && !member) throw new ApiException("You are not a confirmed member of this visit");
        if ("CANCELLED".equals(visit.getStatus())) throw new ApiException("Cancelled visits cannot be synced");

        VisitPlan plan = visitPlanRepository.findAllByVisit_IdAndPlanKind(visitId, "GROUP").stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && p.getPlanApproval() != null && "APPROVED".equals(p.getPlanApproval().getStatus()))
                .max(Comparator.comparing(VisitPlan::getCreatedAt)).orElseThrow(() -> new ApiException("An approved group plan is required"));

        List<PlanItem> items = plan.getPlanItems().stream().filter(Objects::nonNull).filter(item -> calendarItemBelongsToMember(item, participant))
                .sorted(Comparator.comparing(PlanItem::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        LocalTime arrival = visit.getPlannedStartTime() != null ? visit.getPlannedStartTime() : visit.getExpectedArrivalTime();
        LocalDateTime start = arrival != null ? visit.getVisitDate().atTime(arrival) : items.stream().map(PlanItem::getStartsAt).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime end = visit.getPlannedEndTime() != null ? visit.getVisitDate().atTime(visit.getPlannedEndTime()) : items.stream().map(PlanItem::getEndsAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        if ("SPLIT".equals(visit.getTogetherMode())) {
            start = items.stream().map(PlanItem::getStartsAt).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(start);
            end = items.stream().map(PlanItem::getEndsAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(end);
        }
        if (start == null || end == null) throw new ApiException("Visit start and end times are required");
        if (!end.isAfter(start) && visit.getPlannedEndTime() != null) end = end.plusDays(1);
        if (!end.isAfter(start)) throw new ApiException("Visit end must be after start");
        String description = "جدول زيارتك من سكة | Sikka\n" + items.stream()
                .filter(p -> p.getStartsAt() != null)
                .map(p -> p.getStartsAt().toLocalTime() + " - " + (p.getTitle() == null ? p.getActivityKind() : p.getTitle()))
                .collect(Collectors.joining("\n"));
        Map<String, Object> event = Map.of(
                "summary", visit.getName() == null ? "زيارة سكة" : visit.getName(),
                "description", description,
                "start", Map.of("dateTime", start.atZone(ZoneId.of("Asia/Riyadh")).format(GOOGLE_EVENT_TIME), "timeZone", "Asia/Riyadh"),
                "end", Map.of("dateTime", end.atZone(ZoneId.of("Asia/Riyadh")).format(GOOGLE_EVENT_TIME), "timeZone", "Asia/Riyadh")
        );
        String token = accessToken(decrypt(connection.getEncryptedRefreshToken()));
        Optional<GoogleCalendarEvent> existing = eventRepository.findByAccount_IdAndVisit_Id(accountId, visitId);
        Map<?, ?> response;
        boolean createEvent = existing.isEmpty();
        response = null;
        if (existing.isPresent()) {
            try {
                response = client.patch().uri("https://www.googleapis.com/calendar/v3/calendars/primary/events/" + enc(existing.get().getGoogleEventId()))
                        .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(event).retrieve().body(Map.class);
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound removedRemotely) {
                createEvent = true;
            }
        }
        if (createEvent) {
            response = client.post().uri("https://www.googleapis.com/calendar/v3/calendars/primary/events")
                    .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(event).retrieve().body(Map.class);
            if (response == null || !(response.get("id") instanceof String eventId)) throw new ApiException("Google event was not created");
            GoogleCalendarEvent record = existing.orElseGet(GoogleCalendarEvent::new);
            record.setAccount(connection.getAccount());
            record.setVisit(visit);
            record.setGoogleEventId(eventId);
            eventRepository.save(record);
        }
        return response != null && response.get("htmlLink") instanceof String link ? link : "Synced";
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void removeCancelledVisit(Integer accountId, Integer visitId) {
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null) throw new ApiException("Visit not found");
        boolean organizer = visit.getOrganizer() != null && accountId.equals(visit.getOrganizer().getId());
        boolean member = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId) != null;
        Optional<GoogleCalendarEvent> existing = eventRepository.findByAccount_IdAndVisit_Id(accountId, visitId);
        if (!organizer && !member && existing.isEmpty()) throw new ApiException("You are not a member of this visit");
        if (!"CANCELLED".equals(visit.getStatus())) throw new ApiException("Only cancelled visits can be removed this way");
        if (existing.isEmpty()) return;
        GoogleCalendarConnection connection = connectionRepository.findById(accountId).orElseThrow(() -> new ApiException("Connect Google Calendar first"));
        configured();
        String token = accessToken(decrypt(connection.getEncryptedRefreshToken()));
        try {
            client.delete().uri("https://www.googleapis.com/calendar/v3/calendars/primary/events/" + enc(existing.get().getGoogleEventId()))
                    .headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound alreadyDeleted) {
            // The event is already absent remotely.
        }
        eventRepository.delete(existing.get());
    }

    boolean calendarItemBelongsToMember(PlanItem item, VisitMember member) {
        if (member == null || item.getParticipantVisitMemberIds() == null) return false;
        try {
            Integer[] ids = tools.jackson.databind.json.JsonMapper.builder().build().readValue(item.getParticipantVisitMemberIds(), Integer[].class);
            return Arrays.asList(ids).contains(member.getId());
        } catch (Exception invalidParticipants) {
            throw new ApiException("Saved calendar activity participants are invalid");
        }
    }

    private String accessToken(String refreshToken) {
        Map<?, ?> response = client.post().uri("https://oauth2.googleapis.com/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret) + "&refresh_token=" + enc(refreshToken) + "&grant_type=refresh_token")
                .retrieve().body(Map.class);
        if (response == null || !(response.get("access_token") instanceof String token)) throw new ApiException("Google token refresh failed");
        return token;
    }

    private String encrypt(String token) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(encryptionKey), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(encrypted, 0, all, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt Google credentials", e);
        }
    }

    private String decrypt(String data) {
        try {
            byte[] all = Base64.getDecoder().decode(data);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(encryptionKey), "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(all, 0, 12)));
            return new String(cipher.doFinal(Arrays.copyOfRange(all, 12, all.length)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt Google credentials", e);
        }
    }

    private String hashState(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
