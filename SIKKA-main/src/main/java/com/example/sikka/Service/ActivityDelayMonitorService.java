package com.example.sikka.Service;

import com.example.sikka.Model.ActivitySession;
import com.example.sikka.Repository.ActivitySessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityDelayMonitorService {

    private final ActivitySessionRepository activitySessionRepository;
    private final NotificationService notificationService;

    @Value("${sikka.delay.monitor-enabled:true}")
    private boolean monitorEnabled;

    private Integer nextSessionId = 0;

    @Scheduled(fixedDelayString = "${sikka.delay.check-interval-ms:60000}", initialDelayString = "${sikka.delay.initial-delay-ms:30000}")
    public void checkCurrentActivityDelays() {
        if (!monitorEnabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));
        Integer afterId = nextSessionId;
        for (int page = 0; page < 10; page++) {
            List<ActivitySession> candidates = activitySessionRepository.findDelayCandidates(afterId, now, PageRequest.of(0, 100));
            if (candidates.isEmpty()) {
                nextSessionId = 0;
                return;
            }
            for (ActivitySession session : candidates) {
                afterId = session.getId();
                nextSessionId = afterId;
                try {
                    Integer recipientAccountId = session.getVisitMember().getVisitorProfile() == null ? session.getVisitMember().getVisit().getOrganizer().getId() : session.getVisitMember().getVisitorProfile().getAccount().getId();
                    notificationService.checkActivityDelay(recipientAccountId, session.getId());
                } catch (RuntimeException exception) {
                    log.warn("Could not check delay for activity session {}", session.getId(), exception);
                }
            }
        }
    }
}
