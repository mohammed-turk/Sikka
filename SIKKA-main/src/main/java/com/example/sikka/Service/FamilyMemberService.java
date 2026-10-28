package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.FamilyMemberDtoIn;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.FamilyMember;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.FamilyMemberRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class FamilyMemberService {

    private final FamilyMemberRepository familyMemberRepository;
    private final AccountRepository accountRepository;
    private final com.example.sikka.Repository.VisitorProfileRepository visitorProfileRepository;
    private final com.example.sikka.Repository.VisitMemberRepository visitMemberRepository;
    private final com.example.sikka.Repository.VisitRepository visitRepository;

    public List<FamilyMember> getMyFamilyMembers(Integer accountId){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        return familyMemberRepository.findFamilyMembersByOwnerAccountId(accountId);
    }

    public FamilyMember getFamilyMemberById(Integer accountId, Integer familyMemberId){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        FamilyMember familyMember = familyMemberRepository.findFamilyMemberById(familyMemberId);

        if (familyMember == null){
            throw new ApiException("Family member not found");
        }

        if (!familyMember.getOwnerAccount().getId().equals(accountId)){
            throw new ApiException("You cannot access another user's family member");
        }

        return familyMember;
    }

    public void addFamilyMember(Integer accountId, FamilyMemberDtoIn dto){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        FamilyMember familyMember = new FamilyMember();
        familyMember.setName(dto.getName().trim());
        familyMember.setBirthDate(dto.getBirthDate());
        familyMember.setHeightCm(dto.getHeightCm());
        familyMember.setWeightKg(dto.getWeightKg());
        familyMember.setGender(dto.getGender());
        familyMember.setOwnerAccount(account);
        linkVisitorProfile(familyMember, dto.getLinkedVisitorProfileId());

        familyMemberRepository.save(familyMember);
    }

    public void updateFamilyMember(Integer accountId, Integer familyMemberId, FamilyMemberDtoIn dto){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        FamilyMember familyMember = familyMemberRepository.findFamilyMemberById(familyMemberId);

        if (familyMember == null){
            throw new ApiException("Family member not found");
        }

        if (!familyMember.getOwnerAccount().getId().equals(accountId)){
            throw new ApiException("You cannot update another user's family member");
        }

        familyMember.setName(dto.getName().trim());
        familyMember.setBirthDate(dto.getBirthDate());
        familyMember.setHeightCm(dto.getHeightCm());
        familyMember.setWeightKg(dto.getWeightKg());
        familyMember.setGender(dto.getGender());
        linkVisitorProfile(familyMember, dto.getLinkedVisitorProfileId());

        familyMemberRepository.save(familyMember);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteFamilyMember(Integer accountId, Integer familyMemberId) {
        FamilyMember familyMember = getFamilyMemberById(accountId, familyMemberId);
        List<com.example.sikka.Model.VisitMember> members = visitMemberRepository.findAllByFamilyMember_Id(familyMemberId);
        // Lock visits before detaching family records so deletion cannot race plan generation.
        for (Integer visitId : members.stream().map(member -> member.getVisit().getId()).distinct().sorted().toList()) {
            visitRepository.findVisitByIdForUpdate(visitId);
        }
        for (com.example.sikka.Model.VisitMember member : members) {
            if (member.getSnapshotAt() == null) {
                member.setName(familyMember.getName());
                member.setBirthDate(familyMember.getBirthDate());
                member.setHeightCm(familyMember.getHeightCm());
                member.setWeightKg(familyMember.getWeightKg());
                member.setGender(familyMember.getGender());
                member.setSnapshotAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Riyadh")));
            }
            member.setFamilyMember(null);
            visitMemberRepository.save(member);
        }
        familyMemberRepository.delete(familyMember);
    }


    private void linkVisitorProfile(FamilyMember familyMember, Integer profileId) {
        if (profileId == null) {
            familyMember.setLinkedVisitorProfile(null);
            return;
        }
        com.example.sikka.Model.VisitorProfile profile = visitorProfileRepository.findVisitorProfileById(profileId);
        if (profile == null || !"ACTIVE".equals(profile.getAccount().getStatus())) {
            throw new ApiException("An active linked visitor profile is required");
        }
        com.example.sikka.Model.FamilyMember duplicate = familyMemberRepository.findFamilyMemberByOwnerAccount_IdAndLinkedVisitorProfile_Id(familyMember.getOwnerAccount().getId(), profileId);
        if (duplicate != null && !duplicate.getId().equals(familyMember.getId())) {
            throw new ApiException("This profile is already linked to a saved family member");
        }
        familyMember.setLinkedVisitorProfile(profile);
    }

}