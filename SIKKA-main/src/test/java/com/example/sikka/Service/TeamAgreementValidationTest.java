package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.*;
import com.example.sikka.Repository.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamAgreementValidationTest {

    @Test
    void acceptOfficialThrillLevelsDuringRegistration() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String value : List.of("LOW", "MILD", "EXTREME")) {
                assertTrue(validator.validateValue(com.example.sikka.DtoIn.RegisterDtoIn.class, "thrillPreference", value).isEmpty());
                assertTrue(validator.validateValue(VisitorProfile.class, "thrillPreference", value).isEmpty());
            }
            for (String value : List.of("MEDIUM", "HIGH", "low", "MILD ", "")) {
                assertFalse(validator.validateValue(com.example.sikka.DtoIn.RegisterDtoIn.class, "thrillPreference", value).isEmpty());
                assertFalse(validator.validateValue(com.example.sikka.DtoIn.VisitorProfileDtoIn.class, "thrillPreference", value).isEmpty());
                assertFalse(validator.validateValue(VisitorProfile.class, "thrillPreference", value).isEmpty());
                assertFalse(validator.validateValue(Ride.class, "thrillLevel", value).isEmpty());
            }
            assertFalse(validator.validateValue(Visit.class, "status", "OPEN").isEmpty());
            assertFalse(validator.validateValue(PaymentOrder.class, "status", "CANCELLED").isEmpty());
            assertFalse(validator.validateValue(VisitRecord.class, "status", "QUEUING").isEmpty());
            assertFalse(validator.validateValue(Notification.class, "status", "READ").isEmpty());
        }
    }

    @Test
    void allowFilteringShops() {
        PlaceRepository repository = mock(PlaceRepository.class);
        when(repository.findPlacesByKind("SHOP")).thenReturn(List.of());
        assertTrue(new PlaceService(repository, mock(com.example.sikka.Service.MapService.class), mock(com.example.sikka.Repository.VisitRepository.class)).getPlaces("SHOP").isEmpty());
        verify(repository).findPlacesByKind("SHOP");
    }

    @Test
    void openCatalogueDoesNotDependOnQueueReadingFreshness() {
        RideRepository repository = mock(RideRepository.class);
        Ride fresh = new Ride();
        fresh.setWaitMinutes(0);
        fresh.setWaitObservedAt(LocalDateTime.now().minusMinutes(1));
        fresh.setWaitSource("ADMIN");
        Ride stale = new Ride();
        stale.setWaitMinutes(5);
        stale.setWaitObservedAt(LocalDateTime.now().minusHours(1));
        stale.setWaitSource("ADMIN");
        Ride missing = new Ride();
        fresh.setStatus("OPEN");
        stale.setStatus("OPEN");
        missing.setStatus("OPEN");
        when(repository.findRidesByStatus("OPEN")).thenReturn(List.of(fresh, stale, missing));
        assertEquals(List.of(fresh, stale, missing), new RideService(repository, mock(com.example.sikka.Repository.VisitMemberRepository.class), mock(VisitorProfileRepository.class), mock(AccountRepository.class), mock(com.example.sikka.Service.PrivacyConsentService.class), tools.jackson.databind.json.JsonMapper.builder().build()).getOpenRides());
    }

    @Test
    void completedRideLookupRequiresCompletedStatus() {
        VisitRecordRepository repository = mock(VisitRecordRepository.class);
        when(repository.existsByVisitMember_IdAndRide_IdAndStatus(1, 2, "COMPLETED")).thenReturn(true);
        assertTrue(new VisitRecordService(repository, mock(AccountRepository.class), mock(VisitRepository.class), mock(VisitMemberRepository.class), mock(com.example.sikka.Repository.ActivitySessionRepository.class), mock(com.example.sikka.Repository.PlanItemRepository.class), mock(com.example.sikka.Repository.VisitPlanRepository.class), mock(VisitPlanService.class), mock(com.example.sikka.Service.ActivityTimingService.class)).hasCompletedRide(1, 2));
    }
}
