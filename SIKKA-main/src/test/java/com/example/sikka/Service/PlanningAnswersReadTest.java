package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.VisitMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanningAnswersReadTest {
    @Mock private VisitMemberRepository visitMemberRepository;
    @InjectMocks private VisitMemberService visitMemberService;

    private VisitMember member(String tripType, boolean withProfile) {
        Account organizer = new Account();
        organizer.setId(1);
        Visit visit = new Visit();
        visit.setOrganizer(organizer);
        visit.setTripType(tripType);
        VisitMember member = new VisitMember();
        member.setVisit(visit);
        member.setSelectedRides("[{\"rideId\":3,\"priority\":\"MUST_RIDE\"}]");
        member.setPreferences("{\"dislikedFeatures\":[\"WATER_RIDE\"]}");
        member.setAnswerStatus("ANSWERED");
        if (withProfile) {
            Account owner = new Account();
            owner.setId(2);
            VisitorProfile profile = new VisitorProfile();
            profile.setAccount(owner);
            member.setVisitorProfile(profile);
        }
        when(visitMemberRepository.findVisitMemberById(9)).thenReturn(member);
        return member;
    }

    @Test
    void returnSavedSelectionsForTheirOwner() {
        member("FRIENDS", true);
        var answers = visitMemberService.getMemberPreferences(2, 9);
        assertEquals("MUST_RIDE", ((JsonNode) answers.get("selectedRides")).get(0).path("priority").asString());
        assertEquals("WATER_RIDE", ((JsonNode) answers.get("dislikedFeatures")).get(0).asString());
        assertThrows(ApiException.class, () -> visitMemberService.getMemberPreferences(1, 9));
    }

    @Test
    void allowManagedFamilyAnswersButRejectAnotherAccount() {
        member("FAMILY", false);
        assertEquals("ANSWERED", visitMemberService.getMemberPreferences(1, 9).get("answerStatus"));
        assertThrows(ApiException.class, () -> visitMemberService.getMemberPreferences(2, 9));
    }

    @Test
    void rejectCorruptSavedAnswersRatherThanShowAnEmptySelection() {
        VisitMember member = member("FRIENDS", true);
        member.setSelectedRides("invalid-json");
        assertThrows(ApiException.class, () -> visitMemberService.getMemberPreferences(2, 9));
        member.setSelectedRides("[{\"rideId\":3}]");
        assertThrows(ApiException.class, () -> visitMemberService.getMemberPreferences(2, 9));
    }
}
