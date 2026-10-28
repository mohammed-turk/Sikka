package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.PlanItem;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitPlan;
import com.example.sikka.Repository.VisitMemberRepository;
import com.example.sikka.Repository.VisitPlanRepository;
import com.example.sikka.Repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final VisitRepository visitRepository;
    private final VisitMemberRepository visitMemberRepository;
    private final VisitPlanRepository visitPlanRepository;
    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter GOOGLE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Transactional(readOnly = true)
    public String createManualCalendarLink(Integer accountId, Integer visitId) {
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        boolean organizer = visit.getOrganizer() != null && accountId.equals(visit.getOrganizer().getId());
        boolean member = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId) != null;
        if (!organizer && !member) {
            throw new ApiException("You are not a member of this visit");
        }
        if ("CANCELLED".equals(visit.getStatus())) {
            throw new ApiException("Cancelled visits cannot be added to the calendar");
        }

        VisitPlan plan = visitPlanRepository.findAllByVisit_IdAndPlanKind(visitId, "GROUP").stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && p.getPlanApproval() != null && "APPROVED".equals(p.getPlanApproval().getStatus()))
                .max(Comparator.comparing(VisitPlan::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow(() -> new ApiException("An approved group plan is required"));

        List<PlanItem> items = plan.getPlanItems().stream().filter(Objects::nonNull).sorted(Comparator.comparing(PlanItem::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PlanItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        if (visit.getVisitDate() == null) {
            throw new ApiException("Visit date is required");
        }

        LocalTime startTime = visit.getPlannedStartTime() != null ? visit.getPlannedStartTime() : visit.getExpectedArrivalTime();
        LocalDateTime start = startTime != null ? visit.getVisitDate().atTime(startTime) : items.stream().map(PlanItem::getStartsAt).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime end = visit.getPlannedEndTime() != null ? visit.getVisitDate().atTime(visit.getPlannedEndTime()) : items.stream().map(PlanItem::getEndsAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);

        if (start == null || end == null) {
            throw new ApiException("Visit schedule must have start and end times");
        }
        if (!end.isAfter(start)) {
            if (visit.getPlannedEndTime() != null) {
                end = end.plusDays(1);
            } else {
                throw new ApiException("Visit end time must be after its start");
            }
        }

        String title = visit.getName() == null || visit.getName().isBlank() ? "زيارة سكة إلى Six Flags Qiddiya" : visit.getName();
        StringBuilder description = new StringBuilder("جدول زيارتك من سكة | Sikka");
        for (PlanItem item : items) {
            if (item.getStartsAt() != null) {
                String itemTitle = item.getTitle() == null || item.getTitle().isBlank() ? item.getActivityKind() : item.getTitle();
                description.append("\n").append(item.getStartsAt().format(DISPLAY_TIME)).append(" - ").append(itemTitle);
            }
        }

        String dates = start.atZone(RIYADH).withZoneSameInstant(UTC).format(GOOGLE_DATE) + "/" + end.atZone(RIYADH).withZoneSameInstant(UTC).format(GOOGLE_DATE);
        return "https://calendar.google.com/calendar/render?action=TEMPLATE&text=" + encode(title) + "&dates=" + encode(dates) + "&details=" + encode(description.toString()) + "&ctz=Asia%2FRiyadh";
    }


    @Transactional(readOnly = true)
    public String createAppleCalendarFile(Integer accountId, Integer visitId) {
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        boolean organizer = visit.getOrganizer() != null && accountId.equals(visit.getOrganizer().getId());
        boolean member = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId) != null;
        if (!organizer && !member) {
            throw new ApiException("You are not a member of this visit");
        }
        if ("CANCELLED".equals(visit.getStatus())) {
            throw new ApiException("Cancelled visits cannot be added to the calendar");
        }

        VisitPlan plan = visitPlanRepository.findAllByVisit_IdAndPlanKind(visitId, "GROUP").stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && p.getPlanApproval() != null && "APPROVED".equals(p.getPlanApproval().getStatus()))
                .max(Comparator.comparing(VisitPlan::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow(() -> new ApiException("An approved group plan is required"));

        List<PlanItem> items = plan.getPlanItems().stream().filter(Objects::nonNull).sorted(Comparator.comparing(PlanItem::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PlanItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        if (visit.getVisitDate() == null) {
            throw new ApiException("Visit date is required");
        }

        LocalTime startTime = visit.getPlannedStartTime() != null ? visit.getPlannedStartTime() : visit.getExpectedArrivalTime();
        LocalDateTime start = startTime != null ? visit.getVisitDate().atTime(startTime) : items.stream().map(PlanItem::getStartsAt).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime end = visit.getPlannedEndTime() != null ? visit.getVisitDate().atTime(visit.getPlannedEndTime()) : items.stream().map(PlanItem::getEndsAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        if (start == null || end == null) {
            throw new ApiException("Visit schedule must have start and end times");
        }
        if (!end.isAfter(start)) {
            if (visit.getPlannedEndTime() != null) {
                end = end.plusDays(1);
            } else {
                throw new ApiException("Visit end time must be after its start");
            }
        }

        String title = visit.getName() == null || visit.getName().isBlank() ? "زيارة سكة إلى Six Flags Qiddiya" : visit.getName();
        StringBuilder description = new StringBuilder("جدول زيارتك من سكة | Sikka");
        for (PlanItem item : items) {
            if (item.getStartsAt() != null) {
                String itemTitle = item.getTitle() == null || item.getTitle().isBlank() ? item.getActivityKind() : item.getTitle();
                description.append("\n").append(item.getStartsAt().format(DISPLAY_TIME)).append(" - ").append(itemTitle);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//Sikka//Visit Calendar//AR");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        lines.add("BEGIN:VEVENT");
        lines.add("UID:sikka-visit-" + visit.getId() + "@sikka.app");
        lines.add("DTSTAMP:" + DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(UTC).format(Instant.now()));
        lines.add("DTSTART:" + start.atZone(RIYADH).withZoneSameInstant(UTC).format(GOOGLE_DATE));
        lines.add("DTEND:" + end.atZone(RIYADH).withZoneSameInstant(UTC).format(GOOGLE_DATE));
        lines.add("SUMMARY:" + escapeIcs(title));
        lines.add("DESCRIPTION:" + escapeIcs(description.toString()));
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");

        StringBuilder calendar = new StringBuilder();
        for (String line : lines) {
            calendar.append(foldIcsLine(line)).append("\r\n");
        }
        return calendar.toString();
    }

    private String escapeIcs(String value) {
        return value.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,");
    }

    private String foldIcsLine(String value) {
        StringBuilder result = new StringBuilder();
        int octets = 0;
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > 75) {
                result.append("\r\n ");
                octets = 1;
            }
            result.append(character);
            octets += size;
            i += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
