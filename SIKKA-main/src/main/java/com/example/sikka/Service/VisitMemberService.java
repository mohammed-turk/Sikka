package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.JoinDtoIn;
import com.example.sikka.DtoIn.ReviewDtoIn;
import com.example.sikka.DtoIn.VisitMemberDtoIn;
import com.example.sikka.DtoOut.VisitMemberDtoOut;
import com.example.sikka.DtoOut.VisitMembersSummaryDtoOut;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitGroup;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.VisitMemberRepository;
import com.example.sikka.Repository.VisitRepository;
import com.example.sikka.Repository.VisitorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

import com.example.sikka.DtoIn.SelectedRideDtoIn;
import com.example.sikka.DtoIn.SelectedRidesDtoIn;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class VisitMemberService {

    private final VisitMemberRepository visitMemberRepository;
    private final VisitRepository visitRepository;
    private final com.example.sikka.Repository.VisitMealBreakRepository visitMealBreakRepository;
    private final PaymentOrderService paymentOrderService;
    private final VisitorProfileRepository visitorProfileRepository;
    private final VisitGroupService visitGroupService;
    private final RideService rideService;
    private final com.example.sikka.Repository.FamilyMemberRepository familyMemberRepository;
    private final com.example.sikka.Repository.VisitPlanRepository visitPlanRepository;
    private final com.example.sikka.Repository.VisitRecordRepository visitRecordRepository;
    private final N8nNotificationService n8nNotificationService;
    private final NotificationEventService notificationEventService;
    private final com.example.sikka.Repository.AccountRepository accountRepository;
    // ---------- CRUD ----------

    public List<VisitMember> get() {
        return visitMemberRepository.findAll();
    }

    @Transactional
    public void add(VisitMemberDtoIn dto) {
        Visit visit = visitRepository.findVisitById(dto.getVisitId());
        if (visit == null)
            throw new ApiException("visit not found");

        VisitorProfile profile = visitorProfileRepository.findVisitorProfileById(dto.getVisitorProfileId());
        if (profile == null)
            throw new ApiException("visitor profile not found");

        requirePaidRepeatedEdit(visit);
        createMember(visit, profile);
    }

    @Transactional
    public void delete(Integer id) {
        VisitMember member = visitMemberRepository.findVisitMemberById(id);
        if (member == null)
            throw new ApiException("member not found");

        visitRepository.findVisitByIdForUpdate(member.getVisit().getId());
        checkOpenForChanges(member.getVisit());
        requirePaidRepeatedEdit(member.getVisit());
        if (isOrganizerMember(member, member.getVisit().getOrganizer().getId()))
            throw new ApiException("organizer cannot be removed");
        validateMemberDeletion(member);
        detachMember(member);
        visitMemberRepository.delete(member);
    }

    // ---------- Flow 1 extras ----------

    // called by VisitService.create, the organizer is always a GOING member
    public VisitMember addOrganizer(Visit visit, VisitorProfile profile) {
        VisitMember member = new VisitMember();
        member.setVisit(visit);
        member.setVisitorProfile(profile);
        copySnapshot(member, profile);
        member.setRsvpStatus("GOING");
        member.setInvitedAt(LocalDateTime.now());
        return visitMemberRepository.save(member);
    }

    // visitor joins by invite code, no organizer approval
    @Transactional
    public void join(Integer accountId, JoinDtoIn dto) {
        com.example.sikka.Model.Account account = accountRepository.findAccountById(accountId);
        if (account == null || !Boolean.TRUE.equals(account.getEmailVerified()))
            throw new ApiException("verify your account first");

        VisitorProfile profile = visitorProfileRepository.findVisitorProfileByAccount_Id(accountId);
        if (profile == null)
            throw new ApiException("create your visitor profile first");

        VisitGroup group = visitGroupService.getGroupByInviteCode(dto.getCode());
        if (!Boolean.TRUE.equals(group.getInvitationsEnabled()))
            throw new ApiException("invitations are disabled for this group");

        Visit visit = group.getVisit();
        if (visit == null)
            throw new ApiException("group has no visit yet");

        createMember(visit, profile);
    }

    @Transactional
    public void updateRsvp(Integer accountId, Integer visitId, int statusInt) {
        if (statusInt < 1 || statusInt > 3) {
            throw new ApiException("Attendance action must be 1, 2 or 3");
        }

        String rsvp = statusInt == 1 ? "GOING"
                : statusInt == 2 ? "MAYBE" : "NOT_GOING";

        VisitMember member = getMemberLocked(accountId, visitId);
        checkOpenForChanges(member.getVisit());

        if (rsvp.equals(member.getRsvpStatus())) {
            return;
        }

        boolean firstResponse = "INVITED".equals(member.getRsvpStatus());

        member.setRsvpStatus(rsvp);
        visitMemberRepository.save(member);
        if ("GOING".equals(rsvp)) copyOrganizerPlanningChoices(member);

        notificationEventService.notifyMemberRsvp(member, firstResponse);
    }

    @Transactional
    public void updateSelectedRides(Integer accountId, Integer visitId, SelectedRidesDtoIn dto) {
        VisitMember member = getMemberLocked(accountId, visitId);
        checkOpenForChanges(member.getVisit());
        requirePaidRepeatedEdit(member.getVisit());

        if (dto == null || dto.getRides() == null) {
            throw new ApiException("Selected rides list is required");
        }
        for (SelectedRideDtoIn selectedRide : dto.getRides()) {
            if (selectedRide == null || selectedRide.getRideId() == null || selectedRide.getRideId() <= 0 || selectedRide.getPriority() == null || !selectedRide.getPriority().matches("^(MUST_RIDE|PREFERRED|OPTIONAL)$")) {
                throw new ApiException("Every selected ride requires a positive ride ID and a valid priority");
            }
        }
        List<Integer> rideIds = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (SelectedRideDtoIn r : dto.getRides()) {
            if (!seen.add(r.getRideId()))
                throw new ApiException("ride " + r.getRideId() + " is selected more than once");
            rideIds.add(r.getRideId());
        }
        if (!rideIds.isEmpty() && !rideService.validateRideIds(rideIds))
            throw new ApiException("one or more selected rides do not exist");

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < dto.getRides().size(); i++) {
            SelectedRideDtoIn r = dto.getRides().get(i);
            if (i > 0)
                json.append(",");
            json.append("{\"rideId\":").append(r.getRideId()) .append(",\"priority\":\"").append(r.getPriority()).append("\"}");
        }
        json.append("]");

        member.setSelectedRides(json.toString());
        member.setAnswerStatus("ANSWERED");
        visitMemberRepository.save(member);
    }

    @Transactional
    public void leave(Integer accountId, Integer visitId) {
        VisitMember member = getMemberLocked(accountId, visitId);

        if (member.getVisit().getOrganizer().getId().equals(accountId))
            throw new ApiException("organizer cannot leave, cancel the visit instead");
        checkOpenForChanges(member.getVisit());
        requirePaidRepeatedEdit(member.getVisit());

        validateMemberDeletion(member);
        detachMember(member);
        visitMemberRepository.delete(member);
    }

    public void checkIn(Integer accountId, Integer visitId) {
        VisitMember member = getMember(accountId, visitId);

        if (!member.getVisit().getStatus().equals("IN_PROGRESS"))
            throw new ApiException("visit is not in progress");
        if (!member.getVisit().getVisitDate().equals(LocalDate.now(ZoneId.of("Asia/Riyadh"))))
            throw new ApiException("check-in is only allowed on the visit date");
        if (!"GOING".equals(member.getRsvpStatus()))
            throw new ApiException("only going members can check in");
        if (member.getCheckedInAt() != null)
            throw new ApiException("already checked in");

        member.setCheckedInAt(LocalDateTime.now());
        visitMemberRepository.save(member);
    }

    public void review(Integer accountId, Integer visitId, ReviewDtoIn dto) {
        VisitMember member = getMember(accountId, visitId);

        if (!member.getVisit().getStatus().equals("COMPLETED"))
            throw new ApiException("visit is not completed yet");
        if (!"GOING".equals(member.getRsvpStatus()))
            throw new ApiException("only going members can review");

        if (dto == null || (dto.getRating() == null && dto.getReview() == null && dto.getSikkaRating() == null && dto.getSikkaReview() == null)) {
            throw new ApiException("Send at least one rating or review");
        }
        if (dto.getRating() != null && (dto.getRating() < 1 || dto.getRating() > 5)) {
            throw new ApiException("Visit rating must be between 1 and 5");
        }
        if (dto.getRating() != null)
            member.setVisitRating(dto.getRating());
        if (dto.getReview() != null)
            member.setVisitReview(dto.getReview());
        if (dto.getSikkaRating() != null)
            member.setSikkaRating(dto.getSikkaRating());
        if (dto.getSikkaReview() != null)
            member.setSikkaReview(dto.getSikkaReview());
        visitMemberRepository.save(member);
    }



    // organizer removes a member (a friend or a family member)
    @Transactional
    public void remove(Integer organizerId, Integer visitId, Integer memberId) {
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        if (visit == null)
            throw new ApiException("visit not found");
        if (!visit.getOrganizer().getId().equals(organizerId))
            throw new ApiException("only the organizer can remove members");

        VisitMember member = visitMemberRepository.findVisitMemberById(memberId);
        if (member == null || !member.getVisit().getId().equals(visitId))
            throw new ApiException("member not found in this visit");
        if (isOrganizerMember(member, organizerId))
            throw new ApiException("organizer cannot be removed");

        checkOpenForChanges(visit);
        requirePaidRepeatedEdit(visit);
        validateMemberDeletion(member);
        detachMember(member);
        visitMemberRepository.delete(member);
    }

    public VisitMembersSummaryDtoOut getSummary(Integer accountId, Integer visitId) {
        Visit summaryVisit = visitRepository.findVisitById(visitId);
        if (summaryVisit == null)
            throw new ApiException("visit not found");
        getMember(accountId, visitId); // only members of the visit can see the list

        List<VisitMemberDtoOut> list = new ArrayList<>();
        int going = 0;
        for (VisitMember m : visitMemberRepository.findAllByVisit_Id(visitId)) {
            Integer familyId = m.getFamilyMember() == null ? null : m.getFamilyMember().getId();
            if (familyId == null && m.getVisitorProfile() != null) {
                com.example.sikka.Model.FamilyMember linked = familyMemberRepository.findFamilyMemberByOwnerAccount_IdAndLinkedVisitorProfile_Id(summaryVisit.getOrganizer().getId(), m.getVisitorProfile().getId());
                if (linked != null) familyId = linked.getId();
            }
            list.add(new VisitMemberDtoOut(m.getId(), m.getVisitorProfile() == null ? null : m.getVisitorProfile().getId(), m.getName(), m.getRsvpStatus(), m.getCheckedInAt(), familyId));
            if ("GOING".equals(m.getRsvpStatus()))
                going++;
        }
        return new VisitMembersSummaryDtoOut(list.size(), going, list);
    }

    // ---------- contract methods for the other flows ----------

    public List<VisitMember> getGoingMembers(Integer visitId) {
        return visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING");
    }

    public VisitMember getVisitMember(Integer memberId) {
        VisitMember member = visitMemberRepository.findVisitMemberById(memberId);
        if (member == null)
            throw new ApiException("member not found");
        return member;
    }

    // account ids of the members who have an account (family members have none)
    public List<Integer> getMemberAccountIds(Integer visitId) {
        List<Integer> ids = new ArrayList<>();
        for (VisitMember m : visitMemberRepository.findAllByVisit_Id(visitId))
            if (m.getVisitorProfile() != null)
                ids.add(m.getVisitorProfile().getAccount().getId());
        return ids;
    }

    // ---------- helpers ----------

    private void validateMemberDeletion(VisitMember member) {
        if (visitPlanRepository.existsByOwnerVisitMember_Id(member.getId()) || visitRecordRepository.existsByVisitMember_Id(member.getId())) {
            throw new ApiException("Cannot remove a member linked to plans or ride records");
        }
    }


    // locks the visit row first, then returns the member (same lock used by plan generation)
    private VisitMember getMemberLocked(Integer accountId, Integer visitId) {
        if (visitRepository.findVisitByIdForUpdate(visitId) == null)
            throw new ApiException("visit not found");
        return getMember(accountId, visitId);
    }
    private VisitMember getMember(Integer accountId, Integer visitId) {
        VisitMember member = visitMemberRepository .findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId);
        if (member == null)
            throw new ApiException("you are not a member of this visit");
        return member;
    }

    // join, rsvp, rides, leave and remove are allowed in DRAFT only (generate moves the visit to PLANNING)
    private void requirePaidRepeatedEdit(Visit visit) {
        if (visit.getReusedFromVisitId() != null && !paymentOrderService.hasPaidVisit(visit.getOrganizer().getId(), visit.getId())) {
            throw new ApiException("Pay before changing members or preferences of a repeated visit");
        }
    }

    private void checkOpenForChanges(Visit visit) {
        if (!"DRAFT".equals(visit.getStatus()) || Boolean.TRUE.equals(visit.getMembersLocked()))
            throw new ApiException("members can only be changed while the visit is in DRAFT");
    }

    // copies the profile data into the member (the planner reads this copy, not the profile)
    private void copySnapshot(VisitMember member, VisitorProfile profile) {
        member.setName(profile.getName());
        member.setBirthDate(profile.getBirthDate());
        member.setHeightCm(profile.getHeightCm());
        member.setWeightKg(profile.getWeightKg());
        member.setGender(profile.getGender());
    }

    private void createMember(Visit visit, VisitorProfile profile) {
        visit = visitRepository.findVisitByIdForUpdate(visit.getId());
        checkOpenForChanges(visit);
        requirePaidRepeatedEdit(visit);

        if (visitMemberRepository.findVisitMemberByVisitAndVisitorProfile(visit, profile) != null)
            throw new ApiException("visitor is already a member of this visit");

        if (visitMemberRepository.countByVisit(visit) >= visit.getMaxParticipants())
            throw new ApiException("visit is full");

        VisitMember member = new VisitMember();
        member.setVisit(visit);
        member.setVisitorProfile(profile);
        copySnapshot(member, profile);
        member.setRsvpStatus("INVITED");
        member.setInvitedAt(LocalDateTime.now());
        visitMemberRepository.save(member);
    }

    // true if this member is the organizer (family members have no account, so never)
    private boolean isOrganizerMember(VisitMember member, Integer organizerAccountId) {
        return member.getVisitorProfile() != null
                && member.getVisitorProfile().getAccount().getId().equals(organizerAccountId);
    }

    // removes the member from both sides of the relations before deleting it
    private void detachMember(VisitMember member) {
        if (member.getVisit().getMembers() != null) {
            member.getVisit().getMembers().remove(member);
        }
        if (member.getVisitorProfile() != null && member.getVisitorProfile().getVisitMembers() != null) {
            member.getVisitorProfile().getVisitMembers().remove(member);
        }
    }

    public void freezeMemberSnapshots(Visit visit) {
        for (VisitMember member : visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visit.getId(), "GOING")) {
            if (member.getSnapshotAt() != null) {
                continue;
            }
            if (member.getVisitorProfile() != null) {
                copySnapshot(member, member.getVisitorProfile());
            } else if (member.getFamilyMember() != null) {
                member.setName(member.getFamilyMember().getName());
                member.setBirthDate(member.getFamilyMember().getBirthDate());
                member.setHeightCm(member.getFamilyMember().getHeightCm());
                member.setWeightKg(member.getFamilyMember().getWeightKg());
                member.setGender(member.getFamilyMember().getGender());
            } else {
                throw new ApiException("Select an existing participant before generating the plan");
            }
            member.setSnapshotAt(LocalDateTime.now(ZoneId.of("Asia/Riyadh")));
            visitMemberRepository.save(member);
        }
    }


    @Transactional
    public void addFamilyMembers(Integer accountId, Integer visitId, com.example.sikka.DtoIn.AddFamilyMembersDtoIn dto) {
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        if (visit == null || !visit.getOrganizer().getId().equals(accountId) || !"FAMILY".equals(visit.getTripType())) {
            throw new ApiException("Only the organizer can add family members to a family visit");
        }
        checkOpenForChanges(visit);
        requirePaidRepeatedEdit(visit);
        if (dto == null || dto.getFamilyMemberIds() == null || dto.getFamilyMemberIds().isEmpty() || new HashSet<>(dto.getFamilyMemberIds()).size() != dto.getFamilyMemberIds().size()) {
            throw new ApiException("Select distinct saved family members");
        }
        List<VisitMember> current = visitMemberRepository.findAllByVisit_Id(visitId);
        List<com.example.sikka.Model.FamilyMember> additions = new ArrayList<>();
        for (Integer familyId : dto.getFamilyMemberIds()) {
            com.example.sikka.Model.FamilyMember family = familyMemberRepository.findFamilyMemberById(familyId);
            if (family == null || !family.getOwnerAccount().getId().equals(accountId)) throw new ApiException("Saved family member not found for this account");
            if (current.stream().noneMatch(member -> family.equals(member.getFamilyMember()) || family.getLinkedVisitorProfile() != null && family.getLinkedVisitorProfile().equals(member.getVisitorProfile()))) additions.add(family);
        }
        int required = current.size() + additions.size();
        int capacity = dto.getMaxParticipants() == null ? visit.getMaxParticipants() : dto.getMaxParticipants();
        if (capacity < visit.getMaxParticipants() || capacity < required) throw new ApiException("الحد الحالي للزيارة " + visit.getMaxParticipants() + " أشخاص والرقم يشملك أنت. لإضافة المحددين تحتاج إلى " + required + " أشخاص؛ هل تبي تعدّل العدد؟");
        if (dto.getMaxParticipants() != null) visit.setMaxParticipants(capacity);
        for (com.example.sikka.Model.FamilyMember family : additions) {
            VisitorProfile profile = family.getLinkedVisitorProfile();
            if (current.stream().anyMatch(member -> family.equals(member.getFamilyMember()) || profile != null && profile.equals(member.getVisitorProfile()))) {
                throw new ApiException("This person already participates in the visit");
            }
            VisitMember member = new VisitMember();
            member.setVisit(visit);
            member.setName(family.getName());
            member.setBirthDate(family.getBirthDate());
            member.setHeightCm(family.getHeightCm());
            member.setWeightKg(family.getWeightKg());
            member.setGender(family.getGender());
            member.setRsvpStatus("GOING");
            member.setInvitedAt(LocalDateTime.now(ZoneId.of("Asia/Riyadh")));
            if (profile == null) {
                member.setFamilyMember(family);
            } else {
                member.setVisitorProfile(profile);
                copySnapshot(member, profile);
            }
            visitMemberRepository.save(member);
            copyOrganizerPlanningChoices(member);
            current.add(member);
        }
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getMemberPreferences(Integer accountId, Integer visitMemberId) {
        VisitMember member = visitMemberRepository.findVisitMemberById(visitMemberId);
        if (member == null) throw new ApiException("Visit member not found");
        boolean own = member.getVisitorProfile() != null && member.getVisitorProfile().getAccount().getId().equals(accountId);
        boolean managed = member.getVisit().getOrganizer().getId().equals(accountId) && "FAMILY".equals(member.getVisit().getTripType()) && member.getVisitorProfile() == null;
        if (!own && !managed) throw new ApiException("You cannot view this participant's planning answers");
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        try {
            tools.jackson.databind.JsonNode selected = mapper.readTree(member.getSelectedRides() == null ? "[]" : member.getSelectedRides());
            tools.jackson.databind.JsonNode preferences = mapper.readTree(member.getPreferences() == null ? "{}" : member.getPreferences());
            tools.jackson.databind.JsonNode disliked = preferences.path("dislikedFeatures").isArray() ? preferences.path("dislikedFeatures") : mapper.readTree("[]");
            if (!selected.isArray() || !preferences.isObject()) throw new ApiException("Saved planning answers are invalid");
            for (tools.jackson.databind.JsonNode choice : selected) {
                if (!choice.isObject() || !choice.path("rideId").isIntegralNumber() || choice.path("rideId").asInt() <= 0 || !java.util.Set.of("MUST_RIDE", "PREFERRED", "OPTIONAL").contains(choice.path("priority").asString())) throw new ApiException("Saved planning answers are invalid");
            }
            return java.util.Map.of("selectedRides", selected, "dislikedFeatures", disliked, "excludedRideIds", preferences.path("excludedRideIds").isArray() ? preferences.path("excludedRideIds") : mapper.readTree("[]"), "excludedRideTypes", preferences.path("excludedRideTypes").isArray() ? preferences.path("excludedRideTypes") : mapper.readTree("[]"), "answerStatus", member.getAnswerStatus() == null ? "PENDING" : member.getAnswerStatus());
        } catch (ApiException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new ApiException("Saved planning answers are invalid");
        }
    }

    @Transactional
    public void saveMemberPreferences(Integer accountId, Integer visitMemberId, com.example.sikka.DtoIn.MemberPreferencesDtoIn dto, int statusInt) {
        if (statusInt != 1 && statusInt != 2) {
            throw new ApiException("Planning answer action must be 1 or 2");
        }
        String answerStatus = statusInt == 1 ? "ANSWERED" : "SKIPPED";

        VisitMember member = visitMemberRepository.findVisitMemberById(visitMemberId);
        if (member == null) {
            throw new ApiException("Visit member not found");
        }
        Visit visit = visitRepository.findVisitByIdForUpdate(member.getVisit().getId());
        checkOpenForChanges(visit);
        requirePaidRepeatedEdit(visit);
        boolean own = member.getVisitorProfile() != null && member.getVisitorProfile().getAccount().getId().equals(accountId);
        boolean managed = visit.getOrganizer().getId().equals(accountId) && "FAMILY".equals(visit.getTripType()) && member.getVisitorProfile() == null;
        if (!own && !managed || !"GOING".equals(member.getRsvpStatus())) {
            throw new ApiException("You cannot edit this participant's planning answers");
        }
        if (dto == null) {
            throw new ApiException("Planning answers are required");
        }
        boolean shared = !"SOLO".equals(visit.getTripType()) && !"SPLIT".equals(visit.getTogetherMode());
        if (shared && !visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the organizer chooses rides for a together visit");
        }
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        List<Integer> excluded = dto.getExcludedRideIds() == null ? List.of() : dto.getExcludedRideIds();
        List<String> excludedTypes = dto.getExcludedRideTypes() == null ? List.of() : dto.getExcludedRideTypes();
        if (excluded.stream().anyMatch(id -> id == null || id <= 0) || !rideService.validateRideIds(excluded) || excludedTypes.stream().anyMatch(type -> !java.util.Set.of("THRILL", "FAMILY", "KIDS", "WATER", "ARCADE").contains(type))) {
            throw new ApiException("Invalid excluded ride or ride type");
        }
        List<com.example.sikka.DtoIn.SelectedRideDtoIn> selections = statusInt == 2 || dto.getSelectedRides() == null ? List.of() : dto.getSelectedRides();
        List<Integer> rideIds = new ArrayList<>();
        for (com.example.sikka.DtoIn.SelectedRideDtoIn selection : selections) {
            if (selection == null || selection.getRideId() == null || selection.getRideId() <= 0 || selection.getPriority() == null || !java.util.Set.of("MUST_RIDE", "PREFERRED", "OPTIONAL").contains(selection.getPriority()) || rideIds.contains(selection.getRideId()) || excluded.contains(selection.getRideId())) {
                throw new ApiException("Every selected ride needs a distinct ID and valid priority");
            }
            rideIds.add(selection.getRideId());
        }
        if (!rideService.validateRideIds(rideIds)) {
            throw new ApiException("One or more selected rides do not exist");
        }
        member.setSelectedRides(mapper.writeValueAsString(selections));
        member.setPreferences(mapper.writeValueAsString(java.util.Map.of("dislikedFeatures", statusInt == 2 || dto.getDislikedFeatures() == null ? List.of() : dto.getDislikedFeatures(), "excludedRideIds", excluded, "excludedRideTypes", excludedTypes)));
        member.setAnswerStatus(answerStatus);
        visitMemberRepository.save(member);
        if (shared) {
            for (VisitMember participant : visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visit.getId(), "GOING")) {
                participant.setSelectedRides(member.getSelectedRides());
                participant.setPreferences(member.getPreferences());
                participant.setAnswerStatus(answerStatus);
                visitMemberRepository.save(participant);
            }
        }
    }

    private void copyOrganizerPlanningChoices(VisitMember member) {
        Visit visit = member.getVisit();
        VisitMember organizer = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visit.getId(), visit.getOrganizer().getId());
        if (organizer == null || organizer.getId().equals(member.getId())) return;
        if (!"SPLIT".equals(visit.getTogetherMode()) && !"PENDING".equals(organizer.getAnswerStatus())) {
            member.setSelectedRides(organizer.getSelectedRides());
            member.setPreferences(organizer.getPreferences());
            member.setAnswerStatus(organizer.getAnswerStatus());
            visitMemberRepository.save(member);
        }
        List<com.example.sikka.Model.VisitMealBreak> meals = visitMealBreakRepository.findVisitMealBreaksByVisitId(visit.getId());
        if (meals.stream().anyMatch(meal -> meal.getVisitMember().getId().equals(member.getId()))) return;
        for (com.example.sikka.Model.VisitMealBreak source : meals) {
            if (!source.getVisitMember().getId().equals(organizer.getId())) continue;
            com.example.sikka.Model.VisitMealBreak meal = new com.example.sikka.Model.VisitMealBreak();
            meal.setVisit(visit);
            meal.setVisitMember(member);
            meal.setBreakKey(source.getBreakKey());
            meal.setMealChoice(source.getMealChoice());
            meal.setPreferredTiming(source.getPreferredTiming());
            meal.setPlace(source.getPlace());
            visitMealBreakRepository.save(meal);
        }
    }

    @Transactional
    public void remindVisitMember(Integer accountId, Integer memberId) {
        VisitMember member = visitMemberRepository.findVisitMemberById(memberId);
        if (member == null || !member.getVisit().getOrganizer().getId().equals(accountId)) throw new ApiException("Only the organizer can remind a visit member");
        Visit visit = visitRepository.findVisitByIdForUpdate(member.getVisit().getId());
        if (!"DRAFT".equals(visit.getStatus()) || member.getVisitorProfile() == null || isOrganizerMember(member, accountId) || "NOT_GOING".equals(member.getRsvpStatus())) throw new ApiException("This member does not need a reminder");
        if ("GOING".equals(member.getRsvpStatus()) && !"PENDING".equals(member.getAnswerStatus())) throw new ApiException("This member has already answered");
        if ("GOING".equals(member.getRsvpStatus()) && !"SPLIT".equals(visit.getTogetherMode())) throw new ApiException("العضو أكد حضوره، واختيارات هذه الزيارة عند المنظّم؛ ما يحتاج تذكير اختيارات.");
        notificationEventService.notifyPlanningReminder(member);
    }

}
