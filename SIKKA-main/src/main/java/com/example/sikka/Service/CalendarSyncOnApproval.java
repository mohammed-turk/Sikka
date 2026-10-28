package com.example.sikka.Service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CalendarSyncOnApproval {
    private static final Logger log = LoggerFactory.getLogger(CalendarSyncOnApproval.class);
    private final GoogleCalendarOAuthService calendarService;

    public record ApprovedVisitChanged(Integer accountId, Integer visitId) {}
    public record CancelledVisitChanged(Integer accountId, Integer visitId) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void removeCancelled(CancelledVisitChanged event) {
        try {
            dispatchCancellation(event);
        } catch (RuntimeException exception) {
            log.warn("Could not load cancellation calendar recipients for visit {}; cancellation remains saved", event.visitId(), exception);
        }
    }

    private void dispatchCancellation(CancelledVisitChanged event) {
        for (Integer accountId : calendarService.getVisitCalendarAccounts(event.visitId(), true)) {
            try {
                if (calendarService.connected(accountId)) calendarService.removeCancelledVisit(accountId, event.visitId());
            } catch (RuntimeException exception) {
                log.warn("Calendar cancellation failed for account {} and visit {}; cancellation remains saved", accountId, event.visitId(), exception);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sync(ApprovedVisitChanged event) {
        try {
            dispatchSync(event);
        } catch (RuntimeException exception) {
            log.warn("Could not load calendar recipients for visit {}; plan remains saved", event.visitId(), exception);
        }
    }

    private void dispatchSync(ApprovedVisitChanged event) {
        for (Integer accountId : calendarService.getVisitCalendarAccounts(event.visitId(), false)) {
            try {
                if (calendarService.connected(accountId)) calendarService.syncVisit(accountId, event.visitId());
            } catch (RuntimeException exception) {
                log.warn("Calendar sync failed for account {} and visit {}; plan remains saved", accountId, event.visitId(), exception);
            }
        }
    }
}
