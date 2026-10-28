package com.example.sikka.Service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class CalendarSyncOnApprovalTest {
    @Test
    void synchronizeOtherConnectedMembersEvenWhenOneAccountFails() {
        GoogleCalendarOAuthService service = mock(GoogleCalendarOAuthService.class);
        when(service.getVisitCalendarAccounts(9, false)).thenReturn(List.of(2, 3, 4));
        when(service.connected(2)).thenReturn(true);
        when(service.connected(3)).thenReturn(true);
        when(service.syncVisit(2, 9)).thenThrow(new RuntimeException("Network unavailable"));
        new CalendarSyncOnApproval(service).sync(new CalendarSyncOnApproval.ApprovedVisitChanged(2, 9));
        verify(service).syncVisit(3, 9);
        verify(service, never()).syncVisit(4, 9);
    }

    @Test
    void removeCancelledEventsForAllLinkedAccounts() {
        GoogleCalendarOAuthService service = mock(GoogleCalendarOAuthService.class);
        when(service.getVisitCalendarAccounts(9, true)).thenReturn(List.of(2, 3));
        when(service.connected(anyInt())).thenReturn(true);
        new CalendarSyncOnApproval(service).removeCancelled(new CalendarSyncOnApproval.CancelledVisitChanged(2, 9));
        verify(service).removeCancelledVisit(2, 9);
        verify(service).removeCancelledVisit(3, 9);
    }
    @Test
    void calendarDescriptionIncludesOnlyTheOwnersSplitActivities() {
        GoogleCalendarOAuthService service = mock(GoogleCalendarOAuthService.class, CALLS_REAL_METHODS);
        com.example.sikka.Model.VisitMember member = new com.example.sikka.Model.VisitMember();
        member.setId(10);
        com.example.sikka.Model.PlanItem own = new com.example.sikka.Model.PlanItem();
        own.setParticipantVisitMemberIds("[10]");
        com.example.sikka.Model.PlanItem other = new com.example.sikka.Model.PlanItem();
        other.setParticipantVisitMemberIds("[11]");
        org.junit.jupiter.api.Assertions.assertTrue(service.calendarItemBelongsToMember(own, member));
        org.junit.jupiter.api.Assertions.assertFalse(service.calendarItemBelongsToMember(other, member));
    }

}
