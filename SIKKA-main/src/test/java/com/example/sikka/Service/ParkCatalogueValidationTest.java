package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.Place;
import com.example.sikka.Model.Ride;
import com.example.sikka.Repository.PlaceRepository;
import com.example.sikka.Repository.RideRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ParkCatalogueValidationTest {

    @Test
    void rejectCompanionThresholdWithoutRequiredCompanion() {
        RideRepository repository = mock(RideRepository.class);
        Ride ride = new Ride();
        ride.setCompanionRequiredBelowHeightCm(new BigDecimal("130"));
        assertThrows(ApiException.class, () -> new RideService(repository, mock(com.example.sikka.Repository.VisitMemberRepository.class), mock(com.example.sikka.Repository.VisitorProfileRepository.class), mock(com.example.sikka.Repository.AccountRepository.class), mock(com.example.sikka.Service.PrivacyConsentService.class), tools.jackson.databind.json.JsonMapper.builder().build()).addRide(ride));
        verifyNoInteractions(repository);
    }

    @Test
    void saveConditionalCompanionRestriction() {
        RideRepository repository = mock(RideRepository.class);
        Ride ride = new Ride();
        ride.setAdultCompanionRequired(true);
        ride.setMinHeightCm(new BigDecimal("90"));
        ride.setCompanionRequiredBelowHeightCm(new BigDecimal("130"));
        ride.setSourceUrl("https://example.com/test-fixture");
        ride.setRequirementsVerifiedAt(java.time.LocalDateTime.now().minusDays(1));
        new RideService(repository, mock(com.example.sikka.Repository.VisitMemberRepository.class), mock(com.example.sikka.Repository.VisitorProfileRepository.class), mock(com.example.sikka.Repository.AccountRepository.class), mock(com.example.sikka.Service.PrivacyConsentService.class), tools.jackson.databind.json.JsonMapper.builder().build()).addRide(ride);
        verify(repository).save(argThat(saved -> Boolean.TRUE.equals(saved.getAdultCompanionRequired()) && saved.getCompanionRequiredBelowHeightCm().compareTo(new BigDecimal("130")) == 0));
    }

    @Test
    void rejectDiningDetailsOnShop() {
        PlaceRepository repository = mock(PlaceRepository.class);
        Place place = new Place();
        place.setKind("SHOP");
        place.setCuisineTypes(Set.of("INDIAN"));
        assertThrows(ApiException.class, () -> new PlaceService(repository, mock(com.example.sikka.Service.MapService.class), mock(com.example.sikka.Repository.VisitRepository.class)).addPlace(place));
        verifyNoInteractions(repository);
    }

    @Test
    void saveCombinedRideAndPhotoShopTypes() {
        PlaceRepository repository = mock(PlaceRepository.class);
        Place place = new Place();
        place.setKind("SHOP");
        place.setShopTypes(Set.of("RIDE_STORE", "PHOTO_STORE"));
        new PlaceService(repository, mock(com.example.sikka.Service.MapService.class), mock(com.example.sikka.Repository.VisitRepository.class)).addPlace(place);
        verify(repository).save(argThat(saved -> saved.getShopTypes().containsAll(Set.of("RIDE_STORE", "PHOTO_STORE"))));
    }
}
