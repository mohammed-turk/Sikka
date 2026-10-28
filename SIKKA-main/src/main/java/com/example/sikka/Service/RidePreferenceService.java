package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoOut.RidePreferenceDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Ride;
import com.example.sikka.Model.RidePreference;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.RidePreferenceRepository;
import com.example.sikka.Repository.RideRepository;
import com.example.sikka.Repository.VisitorProfileRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.sikka.Model.VisitMember;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class RidePreferenceService {

    private final RidePreferenceRepository ridePreferenceRepository;
    private final VisitorProfileRepository visitorProfileRepository;
    private final AccountRepository accountRepository;
    private final RideRepository rideRepository;
    private final VisitRecordService visitRecordService;

    @Transactional(readOnly = true)
    public List<RidePreferenceDtoOut> getMyRidePreferences(Integer accountId){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        VisitorProfile visitorProfile = visitorProfileRepository.findVisitorProfileByAccountId(accountId);

        if (visitorProfile == null){
            throw new ApiException("Visitor profile not found");
        }

        List<RidePreference> preferences = ridePreferenceRepository.findRidePreferencesByVisitorProfileId(visitorProfile.getId());

        List<RidePreferenceDtoOut> result = new ArrayList<>();

        for (RidePreference preference : preferences){
            result.add(new RidePreferenceDtoOut( preference.getId(), preference.getRide().getId(), preference.getRide().getName(), preference.getPreference() ));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public RidePreferenceDtoOut getRidePreferenceById(Integer accountId, Integer preferenceId){
        RidePreference preference = ridePreferenceRepository.findRidePreferenceById(preferenceId);

        if (preference == null){
            throw new ApiException("Ride preference not found");
        }

        if (!preference.getVisitorProfile().getAccount().getId().equals(accountId)){
            throw new ApiException("You cannot access another user's preference");
        }

        return new RidePreferenceDtoOut( preference.getId(), preference.getRide().getId(), preference.getRide().getName(), preference.getPreference() );
    }

    @Transactional
    public void addRidePreference(Integer accountId, Integer rideId, RidePreference ridePreference){
        if (ridePreference == null || ridePreference.getPreference() == null || !ridePreference.getPreference().matches("^(REPEAT|AVOID|NEUTRAL)$")) {
            throw new ApiException("Preference must be REPEAT, AVOID or NEUTRAL");
        }
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        VisitorProfile visitorProfile = visitorProfileRepository.findVisitorProfileByAccountId(accountId);

        if (visitorProfile == null){
            throw new ApiException("Visitor profile not found");
        }

        Ride ride = rideRepository.findRideById(rideId);

        if (ride == null){
            throw new ApiException("Ride not found");
        }

        if (ridePreferenceRepository.existsByVisitorProfileIdAndRideId(visitorProfile.getId(), rideId)){
            throw new ApiException("A preference already exists for this ride");
        }

        boolean completedRide = false;

        for (VisitMember visitMember : visitorProfile.getVisitMembers()){
            if (visitRecordService.hasCompletedRide(visitMember.getId(), rideId)){
                completedRide = true;
                break;
            }
        }

        if (!completedRide){
            throw new ApiException("You must complete this ride before setting a preference");
        }

        RidePreference newPreference = new RidePreference();
        newPreference.setPreference(ridePreference.getPreference());
        newPreference.setVisitorProfile(visitorProfile);
        newPreference.setRide(ride);

        ridePreferenceRepository.save(newPreference);
    }

    @Transactional
    public void updateRidePreference(Integer accountId, Integer preferenceId, RidePreference ridePreference){
        if (ridePreference == null || ridePreference.getPreference() == null || !ridePreference.getPreference().matches("^(REPEAT|AVOID|NEUTRAL)$")) {
            throw new ApiException("Preference must be REPEAT, AVOID or NEUTRAL");
        }
        RidePreference oldPreference = ridePreferenceRepository.findRidePreferenceById(preferenceId);

        if (oldPreference == null){
            throw new ApiException("Ride preference not found");
        }

        if (!oldPreference.getVisitorProfile().getAccount().getId().equals(accountId)){
            throw new ApiException("You cannot update another user's preference");
        }

        boolean completedRide = false;

        for (VisitMember visitMember : oldPreference.getVisitorProfile().getVisitMembers()){
            if (visitRecordService.hasCompletedRide(visitMember.getId(), oldPreference.getRide().getId())){
                completedRide = true;
                break;
            }
        }

        if (!completedRide){
            throw new ApiException("You must complete this ride before updating a preference");
        }

        oldPreference.setPreference(ridePreference.getPreference());

        ridePreferenceRepository.save(oldPreference);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteRidePreference(Integer accountId, Integer preferenceId){
        RidePreference preference = ridePreferenceRepository.findRidePreferenceById(preferenceId);

        if (preference == null){
            throw new ApiException("Ride preference not found");
        }

        if (!preference.getVisitorProfile().getAccount().getId().equals(accountId)){
            throw new ApiException("You cannot delete another user's preference");
        }

        ridePreferenceRepository.delete(preference);
    }

    public List<RidePreference> getPreferences(Integer profileId){
        VisitorProfile visitorProfile = visitorProfileRepository.findVisitorProfileById(profileId);

        if (visitorProfile == null){
            throw new ApiException("Visitor profile not found");
        }

        return ridePreferenceRepository.findRidePreferencesByVisitorProfileId(profileId);
    }
}
