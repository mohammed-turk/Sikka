package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.RegisterDtoIn;
import com.example.sikka.DtoIn.VisitDtoIn;
import com.example.sikka.Model.*;
import com.example.sikka.Repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceValidationReviewTest {

    @Test
    void rejectLegacyThrillBeforeSavingRideOrAccount() {
        RideRepository rides = mock(RideRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        VisitorProfileRepository profiles = mock(VisitorProfileRepository.class);
        for (String value : new String[]{"MEDIUM", "HIGH"}) {
            Ride ride = new Ride();
            ride.setThrillLevel(value);
            assertThrows(ApiException.class, () -> new RideService(rides, mock(com.example.sikka.Repository.VisitMemberRepository.class), profiles, accounts, mock(com.example.sikka.Service.PrivacyConsentService.class), tools.jackson.databind.json.JsonMapper.builder().build()).addRide(ride));
            RegisterDtoIn registration = new RegisterDtoIn();
            registration.setThrillPreference(value);
            assertThrows(ApiException.class, () -> new AccountService(accounts, profiles, mock(org.springframework.security.crypto.password.PasswordEncoder.class), mock(VerificationService.class)).register(registration));
        }
        verifyNoInteractions(rides, accounts, profiles);
    }

    @Test
    void rejectNullRsvpBeforeQueryingMembership() {
        VisitMemberRepository members = mock(VisitMemberRepository.class);
        VisitMemberService service = new VisitMemberService(members, mock(VisitRepository.class), mock(com.example.sikka.Repository.VisitMealBreakRepository.class), mock(PaymentOrderService.class), mock(VisitorProfileRepository.class), mock(VisitGroupService.class), mock(RideService.class), mock(com.example.sikka.Repository.FamilyMemberRepository.class), mock(com.example.sikka.Repository.VisitPlanRepository.class), mock(com.example.sikka.Repository.VisitRecordRepository.class), mock(N8nNotificationService.class), mock(NotificationEventService.class), mock(AccountRepository.class));
        assertThrows(ApiException.class, () -> service.updateRsvp(1, 2, 0));
        verifyNoInteractions(members);
    }

    @Test
    void rejectUnknownPlaceKindBeforeSaving() {
        PlaceRepository places = mock(PlaceRepository.class);
        Place place = new Place();
        place.setKind("HOTEL");
        assertThrows(ApiException.class, () -> new PlaceService(places, mock(com.example.sikka.Service.MapService.class), mock(com.example.sikka.Repository.VisitRepository.class)).addPlace(place));
        verifyNoInteractions(places);
    }

    @Test
    void rejectInvalidConsentBeforeDatabaseAccess() {
        PrivacyConsentRepository consents = mock(PrivacyConsentRepository.class);
        VisitorProfileRepository profiles = mock(VisitorProfileRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        PrivacyConsent consent = new PrivacyConsent();
        consent.setConsentKind("HEALTH");
        assertThrows(ApiException.class, () -> new PrivacyConsentService(consents, profiles, accounts).addPrivacyConsent(1, consent));
        verifyNoInteractions(consents, profiles, accounts);
    }

    @Test
    void legacyVisitCreationAddsOrganizerAsGoingMember() {
        VisitRepository visits = mock(VisitRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        VisitorProfileRepository profiles = mock(VisitorProfileRepository.class);
        VisitMemberService members = mock(VisitMemberService.class);
        Account organizer = new Account();
        organizer.setId(1);
        organizer.setStatus("ACTIVE");
        VisitorProfile profile = new VisitorProfile();
        when(accounts.findAccountById(1)).thenReturn(organizer);
        when(profiles.findVisitorProfileByAccount_Id(1)).thenReturn(profile);
        VisitDtoIn dto = new VisitDtoIn();
        dto.setOrganizerId(1);
        dto.setVisitDate(LocalDate.now().plusDays(1));
        dto.setMaxParticipants(3);
        VisitService service = new VisitService(mock(NotificationEventService.class), mock(VisitMealBreakService.class), visits, accounts, mock(VisitGroupRepository.class), mock(com.example.sikka.Repository.PlanItemRepository.class), mock(VisitMemberRepository.class), profiles, members, mock(VisitGroupService.class), mock(com.example.sikka.Service.PaymentOrderService.class), mock(com.example.sikka.Repository.VisitPlanRepository.class), mock(com.example.sikka.Repository.PlanApprovalRepository.class), mock(com.example.sikka.Repository.VisitMealBreakRepository.class), mock(com.example.sikka.Repository.PlanHistoryRepository.class), mock(com.example.sikka.Service.VisitPlanService.class), mock(org.springframework.context.ApplicationEventPublisher.class));
        service.add(dto);
        verify(members).addOrganizer(argThat(visit -> visit.getOrganizer() == organizer && "DRAFT".equals(visit.getStatus())), eq(profile));
    }

    @Test
    void rejectVisitUpdateAfterRosterLockWithoutSaving() {
        VisitRepository visits = mock(VisitRepository.class);
        Visit visit = new Visit();
        visit.setStatus("PLANNING");
        visit.setMembersLocked(true);
        when(visits.findVisitByIdForUpdate(2)).thenReturn(visit);
        VisitService service = new VisitService(mock(NotificationEventService.class), mock(VisitMealBreakService.class), visits, mock(AccountRepository.class), mock(VisitGroupRepository.class), mock(com.example.sikka.Repository.PlanItemRepository.class), mock(VisitMemberRepository.class), mock(VisitorProfileRepository.class), mock(VisitMemberService.class), mock(VisitGroupService.class), mock(com.example.sikka.Service.PaymentOrderService.class), mock(com.example.sikka.Repository.VisitPlanRepository.class), mock(com.example.sikka.Repository.PlanApprovalRepository.class), mock(com.example.sikka.Repository.VisitMealBreakRepository.class), mock(com.example.sikka.Repository.PlanHistoryRepository.class), mock(com.example.sikka.Service.VisitPlanService.class), mock(org.springframework.context.ApplicationEventPublisher.class));
        assertThrows(ApiException.class, () -> service.update(2, new VisitDtoIn()));
        verify(visits, never()).save(any());
    }

    @Test
    void rejectInactiveBuyerBeforeOpeningPaymentAttempt() {
        AccountRepository accounts = mock(AccountRepository.class);
        PaymentOrderRepository payments = mock(PaymentOrderRepository.class);
        VisitRepository visits = mock(VisitRepository.class);
        Account account = new Account();
        account.setStatus("SUSPENDED");
        when(accounts.findAccountById(1)).thenReturn(account);
        assertThrows(ApiException.class, () -> new PaymentOrderService(mock(jakarta.persistence.EntityManager.class), payments, accounts, visits, mock(com.example.sikka.Repository.PlanHistoryRepository.class), mock(MoyasarService.class)).addPaymentOrder(1, 2));
        verifyNoInteractions(payments, visits);
    }
}
