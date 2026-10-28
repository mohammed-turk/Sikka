package com.example.sikka.Service;

import com.example.sikka.Repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationReminderService {

    private final VisitRepository visitRepository;
    private final NotificationEventService notificationEventService;

    // Explicit opt-in while the team finishes notification tests.
    @Value("${sikka.notifications.reminders-enabled:false}")
    private boolean enabled;

    private Integer nextVisitId = 0;

    @Scheduled(fixedDelayString = "${sikka.notifications.reminder-interval-ms:60000}",
            initialDelayString = "${sikka.notifications.reminder-initial-delay-ms:60000}")
    public void sendDueReminders() {
        if (!enabled) {
            return;
        }
        for (int page = 0; page < 10; page++) {
            List<Integer> ids = visitRepository.findNotificationReminderVisitIds(nextVisitId, PageRequest.of(0, 100));
            if (ids.isEmpty()) {
                nextVisitId = 0;
                return;
            }
            for (Integer id : ids) {
                nextVisitId = id;
                try {
                    notificationEventService.notifyDueReminders(id);
                } catch (RuntimeException exception) {
                    log.warn("Could not create reminders for visit {}: {}", id, exception.getClass().getSimpleName());
                }
            }
        }
    }
}
