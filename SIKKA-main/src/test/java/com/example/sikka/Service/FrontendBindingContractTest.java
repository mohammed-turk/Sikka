package com.example.sikka.Service;

import com.example.sikka.DtoIn.UpdateProfileDtoIn;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.VisitMemberRepository;
import com.example.sikka.Repository.VisitorProfileRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrontendBindingContractTest {
    @Test
    void exposeOrganizerIdentityWithoutSerializingAccount() {
        Account organizer = new Account();
        organizer.setId(8);
        organizer.setPasswordHash("never-serialize-this");
        Visit visit = new Visit();
        visit.setOrganizer(organizer);
        String json = JsonMapper.builder().build().writeValueAsString(visit);
        assertEquals(8, JsonMapper.builder().build().readTree(json).path("organizerAccountId").asInt());
        assertFalse(json.contains("password"));
        assertFalse(json.contains("never-serialize-this"));
    }

    @Test
    void preserveUnexposedHealthDataWhenUpdatingBasicProfile() {
        VisitorProfileRepository profiles = mock(VisitorProfileRepository.class);
        VisitorProfile profile = new VisitorProfile();
        profile.setHealthData("existing-private-data");
        when(profiles.findVisitorProfileByAccount_Id(8)).thenReturn(profile);
        VisitorProfileService service = new VisitorProfileService(profiles, mock(AccountRepository.class), mock(VisitMemberRepository.class));
        UpdateProfileDtoIn dto = new UpdateProfileDtoIn();
        dto.setName("Updated name");
        service.updateByAccount(8, dto);
        assertEquals("Updated name", profile.getName());
        assertEquals("existing-private-data", profile.getHealthData());
        verify(profiles).save(profile);
    }
    @Test
    void paymentResponseIdentifiesItsVisitWithoutExposingTheVisitGraph() {
        Visit visit = new Visit();
        visit.setId(17);
        com.example.sikka.Model.PaymentOrder order = new com.example.sikka.Model.PaymentOrder();
        order.setVisit(visit);
        var data = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(order));
        assertEquals(17, data.path("visitId").asInt());
        assertFalse(data.has("visit"));
    }

}
