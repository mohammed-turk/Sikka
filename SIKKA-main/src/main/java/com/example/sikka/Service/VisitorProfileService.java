package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.VisitorProfileDtoIn;
import com.example.sikka.DtoOut.VisitorProfileDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.VisitorProfileRepository;
import lombok.RequiredArgsConstructor;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Repository.VisitMemberRepository;
import org.springframework.stereotype.Service;
import com.example.sikka.DtoIn.UpdateProfileDtoIn;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitorProfileService {

    private final VisitorProfileRepository visitorProfileRepository;
    private final AccountRepository accountRepository;
    private final VisitMemberRepository visitMemberRepository;

    public List<VisitorProfileDtoOut> get() {
        List<VisitorProfileDtoOut> list = new ArrayList<>();
        for (VisitorProfile p : visitorProfileRepository.findAll())
            list.add(toDtoOut(p));
        return list;
    }

    public void add(VisitorProfileDtoIn dto) {
        if (dto == null) {
            throw new ApiException("Profile details are required");
        }
        Account account = accountRepository.findAccountById(dto.getAccountId());
        if (account == null)
            throw new ApiException("account not found");
        if (account.getVisitorProfile() != null)
            throw new ApiException("account already has a profile");

        VisitorProfile profile = new VisitorProfile();
        fill(profile, dto);
        profile.setAccount(account);
        visitorProfileRepository.save(profile);
    }

    public void update(Integer id, VisitorProfileDtoIn dto) {
        VisitorProfile profile = visitorProfileRepository.findVisitorProfileById(id);
        if (profile == null)
            throw new ApiException("profile not found");

        fill(profile, dto);
        visitorProfileRepository.save(profile);
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Integer id) {
        VisitorProfile profile = visitorProfileRepository.findVisitorProfileById(id);
        if (profile == null)
            throw new ApiException("profile not found");

        if ((profile.getVisitMembers() != null && !profile.getVisitMembers().isEmpty()) || (profile.getRidePreferences() != null && !profile.getRidePreferences().isEmpty()) || (profile.getPrivacyConsents() != null && !profile.getPrivacyConsents().isEmpty())) {
            throw new ApiException("Cannot delete a profile linked to visit memberships, ride preferences or consents");
        }
        profile.getAccount().setVisitorProfile(null);
        visitorProfileRepository.delete(profile);
    }


    public VisitorProfileDtoOut getByAccount(Integer accountId) {
        VisitorProfile profile = visitorProfileRepository.findVisitorProfileByAccount_Id(accountId);
        if (profile == null)
            throw new ApiException("profile not found");
        VisitorProfileDtoOut result = toDtoOut(profile);
        result.setHealthData(profile.getHealthData());
        return result;
    }

    // the user updates his own profile, the account comes from the path (not from the body)
    public void updateByAccount(Integer accountId, UpdateProfileDtoIn dto) {
        if (dto == null) {
            throw new ApiException("Profile details are required");
        }
        VisitorProfile profile = visitorProfileRepository.findVisitorProfileByAccount_Id(accountId);
        if (profile == null)
            throw new ApiException("profile not found");

        fill(profile, new VisitorProfileDtoIn(accountId, dto.getName(), dto.getBirthDate(), dto.getHeightCm(), dto.getWeightKg(), dto.getGender(), dto.getThrillPreference(), dto.getPreferredRideTypes(), dto.getDislikedRideFeatures(), dto.getFoodPreferences(), dto.getHealthData() == null ? profile.getHealthData() : dto.getHealthData()));
        visitorProfileRepository.save(profile);
    }

    // contract method for the other flows: the full profile of an account member (healthData, preferences).
    // returns null for a family member (no account, no profile).
    // for name, birthDate, heightCm, weightKg and gender read the snapshot stored on the member:
    // VisitMemberService.getVisitMember(memberId)
    public VisitorProfile getProfileByMember(Integer memberId) {
        VisitMember member = visitMemberRepository.findVisitMemberById(memberId);
        if (member == null)
            throw new ApiException("member not found");
        return member.getVisitorProfile();
    }

    private void fill(VisitorProfile p, VisitorProfileDtoIn dto) {
        if (dto == null) {
            throw new ApiException("Profile details are required");
        }
        if (dto.getThrillPreference() != null && !dto.getThrillPreference().matches("^(LOW|MILD|EXTREME)$")) {
            throw new ApiException("Thrill preference must be LOW, MILD or EXTREME");
        }
        p.setName(dto.getName());
        p.setBirthDate(dto.getBirthDate());
        p.setHeightCm(dto.getHeightCm());
        p.setWeightKg(dto.getWeightKg());
        p.setGender(dto.getGender());
        p.setThrillPreference(dto.getThrillPreference());
        p.setPreferredRideTypes(dto.getPreferredRideTypes());
        p.setDislikedRideFeatures(dto.getDislikedRideFeatures());
        p.setFoodPreferences(dto.getFoodPreferences());
        p.setHealthData(dto.getHealthData());
    }

    private VisitorProfileDtoOut toDtoOut(VisitorProfile p) {
        return new VisitorProfileDtoOut(p.getId(), p.getAccount().getId(), p.getName(), p.getBirthDate(), p.getHeightCm(), p.getWeightKg(), p.getGender(), p.getThrillPreference(), p.getPreferredRideTypes(), p.getDislikedRideFeatures(), p.getFoodPreferences(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
