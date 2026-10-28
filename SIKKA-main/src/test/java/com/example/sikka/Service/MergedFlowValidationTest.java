package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.SelectedRideDtoIn;
import com.example.sikka.DtoIn.SelectedRidesDtoIn;
import com.example.sikka.DtoIn.UpdateRideWaitDtoIn;
import com.example.sikka.Model.*;
import com.example.sikka.Repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MergedFlowValidationTest {

    @Test
    void rejectDuplicateSelectedRidesWithoutSaving() {
        VisitMemberRepository members = mock(VisitMemberRepository.class);
        VisitRepository visits = mock(VisitRepository.class);
        RideService rides = mock(RideService.class);
        Visit visit = new Visit();
        visit.setId(2);
        visit.setStatus("PLANNING");
        VisitMember member = new VisitMember();
        member.setVisit(visit);
        when(visits.findVisitByIdForUpdate(2)).thenReturn(visit);
        when(members.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(2, 1)).thenReturn(member);
        SelectedRideDtoIn ride = new SelectedRideDtoIn();
        ride.setRideId(3);
        ride.setPriority("PREFERRED");
        SelectedRidesDtoIn dto = new SelectedRidesDtoIn();
        dto.setRides(List.of(ride, ride));
        VisitMemberService service = new VisitMemberService(members, visits, mock(com.example.sikka.Repository.VisitMealBreakRepository.class), mock(PaymentOrderService.class), mock(VisitorProfileRepository.class), mock(VisitGroupService.class), rides, mock(com.example.sikka.Repository.FamilyMemberRepository.class), mock(com.example.sikka.Repository.VisitPlanRepository.class), mock(com.example.sikka.Repository.VisitRecordRepository.class), mock(N8nNotificationService.class), mock(NotificationEventService.class), mock(AccountRepository.class));
        assertThrows(ApiException.class, () -> service.updateSelectedRides(1, 2, dto));
        verify(members, never()).save(any());
        verifyNoInteractions(rides);
    }

    @Test
    void rejectSummaryForNonMember() {
        VisitMemberRepository members = mock(VisitMemberRepository.class);
        VisitRepository visits = mock(VisitRepository.class);
        when(visits.findVisitById(2)).thenReturn(new Visit());
        VisitMemberService service = new VisitMemberService(members, visits, mock(com.example.sikka.Repository.VisitMealBreakRepository.class), mock(PaymentOrderService.class), mock(VisitorProfileRepository.class), mock(VisitGroupService.class), mock(RideService.class), mock(com.example.sikka.Repository.FamilyMemberRepository.class), mock(com.example.sikka.Repository.VisitPlanRepository.class), mock(com.example.sikka.Repository.VisitRecordRepository.class), mock(N8nNotificationService.class), mock(NotificationEventService.class), mock(AccountRepository.class));
        assertThrows(ApiException.class, () -> service.getSummary(1, 2));
        verify(members, never()).findAllByVisit_Id(any());
    }

    @Test
    void rejectCheckInOutsideVisitDate() {
        VisitMemberRepository members = mock(VisitMemberRepository.class);
        Visit visit = new Visit();
        visit.setStatus("IN_PROGRESS");
        visit.setVisitDate(LocalDate.now(ZoneId.of("Asia/Riyadh")).plusDays(1));
        VisitMember member = new VisitMember();
        member.setVisit(visit);
        member.setRsvpStatus("GOING");
        when(members.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(2, 1)).thenReturn(member);
        VisitMemberService service = new VisitMemberService(members, mock(VisitRepository.class), mock(com.example.sikka.Repository.VisitMealBreakRepository.class), mock(PaymentOrderService.class), mock(VisitorProfileRepository.class), mock(VisitGroupService.class), mock(RideService.class), mock(com.example.sikka.Repository.FamilyMemberRepository.class), mock(com.example.sikka.Repository.VisitPlanRepository.class), mock(com.example.sikka.Repository.VisitRecordRepository.class), mock(N8nNotificationService.class), mock(NotificationEventService.class), mock(AccountRepository.class));
        assertThrows(ApiException.class, () -> service.checkIn(1, 2));
        verify(members, never()).save(any());
    }

    @Test
    void rejectOlderWaitObservation() {
        RideRepository rides = mock(RideRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Account admin = new Account();
        admin.setStatus("ACTIVE");
        admin.setRole("ADMIN");
        when(accounts.findAccountById(1)).thenReturn(admin);
        Ride ride = new Ride();
        ride.setWaitObservedAt(LocalDateTime.now());
        when(rides.findRideById(3)).thenReturn(ride);
        UpdateRideWaitDtoIn dto = new UpdateRideWaitDtoIn();
        dto.setWaitObservedAt(LocalDateTime.now().minusHours(1));
        dto.setWaitMinutes(10);
        dto.setWaitSource("ADMIN");
        RideService service = new RideService(rides, mock(com.example.sikka.Repository.VisitMemberRepository.class), mock(VisitorProfileRepository.class), accounts, mock(com.example.sikka.Service.PrivacyConsentService.class), tools.jackson.databind.json.JsonMapper.builder().build());
        assertThrows(ApiException.class, () -> service.updateRideWait(1, 3, dto));
        verify(rides, never()).save(any());
    }
}
