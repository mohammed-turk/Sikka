package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.PlanApprovalDtoIn;
import com.example.sikka.Model.*;
import com.example.sikka.Repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanApprovalAvailabilityTest {
    @Test
    void provisionalRideCanBeSubmittedAndApprovedButAdminClosureBlocksBoth() {
        Account account = new Account();
        account.setId(1);
        account.setStatus("ACTIVE");
        Visit visit = new Visit();
        visit.setId(1);
        visit.setOrganizer(account);
        visit.setVisitDate(LocalDate.now());
        visit.setStatus("PLANNING");
        VisitPlan plan = new VisitPlan();
        plan.setId(1);
        plan.setVisit(visit);
        plan.setPlanKind("GROUP");
        plan.setTimingMode("RELATIVE");
        plan.setIsActive(false);
        Ride ride = new Ride();
        ride.setStatus("UNAVAILABLE");
        PlanItem item = new PlanItem();
        item.setVisitPlan(plan);
        item.setRide(ride);
        item.setActivityKind("RIDE");
        item.setRelativeStartMinutes(0);
        item.setRelativeEndMinutes(23);
        plan.setPlanItems(Set.of(item));
        PlanApprovalRepository approvals = mock(PlanApprovalRepository.class);
        VisitPlanRepository plans = mock(VisitPlanRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        VisitRepository visits = mock(VisitRepository.class);
        VisitService visitService = mock(VisitService.class);
        PlanApprovalService service = new PlanApprovalService(mock(NotificationEventService.class), mock(org.springframework.context.ApplicationEventPublisher.class), approvals, plans, accounts, visitService, mock(VisitPlanService.class), visits);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", mock(jakarta.persistence.EntityManager.class));
        when(accounts.findAccountById(1)).thenReturn(account);
        when(plans.findVisitPlanByIdForUpdate(1)).thenReturn(plan);
        when(plans.findVisitPlanById(1)).thenReturn(plan);
        when(visits.findVisitByIdForUpdate(1)).thenReturn(visit);
        service.createPlanApproval(1, 1);
        PlanApproval approval = plan.getPlanApproval();
        assertEquals("PENDING", approval.getStatus());
        when(approvals.findPlanApprovalByVisitPlan_Id(1)).thenReturn(approval);
        ride.setStatus("CLOSED");
        ride.setAvailabilitySource("ADMIN");
        assertThrows(ApiException.class, () -> service.respondToPlanApproval(1, new PlanApprovalDtoIn(1, null), 1));
        assertFalse(plan.getIsActive());
        assertEquals("PENDING", approval.getStatus());
        ride.setStatus("UNAVAILABLE");
        ride.setAvailabilitySource(null);
        service.respondToPlanApproval(1, new PlanApprovalDtoIn(1, null), 1);
        assertEquals("APPROVED", approval.getStatus());
        assertTrue(plan.getIsActive());
        verify(visitService).changeStatus(1, "READY");
        plan.setIsActive(false);
        ride.setStatus("CLOSED");
        ride.setAvailabilitySource("ADMIN");
        assertThrows(ApiException.class, () -> service.createPlanApproval(1, 1));
    }
}
