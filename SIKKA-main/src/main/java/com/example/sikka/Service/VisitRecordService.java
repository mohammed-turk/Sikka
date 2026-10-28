package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.CreateVisitRecordDtoIn;
import com.example.sikka.DtoIn.RateVisitRecordDtoIn;
import com.example.sikka.DtoIn.UpdateVisitRecordDtoIn;
import com.example.sikka.DtoOut.VisitProgressDtoOut;
import com.example.sikka.DtoOut.VisitRecordDtoOut;
import com.example.sikka.DtoOut.VisitSummaryDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.ActivitySession;
import com.example.sikka.Model.PlanItem;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitPlan;
import com.example.sikka.Model.VisitRecord;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.ActivitySessionRepository;
import com.example.sikka.Repository.PlanItemRepository;
import com.example.sikka.Repository.VisitMemberRepository;
import com.example.sikka.Repository.VisitPlanRepository;
import com.example.sikka.Repository.VisitRecordRepository;
import com.example.sikka.Repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VisitRecordService {

    private final VisitRecordRepository visitRecordRepository;
    private final AccountRepository accountRepository;
    private final VisitRepository visitRepository;
    private final VisitMemberRepository visitMemberRepository;
    private final ActivitySessionRepository activitySessionRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitPlanRepository visitPlanRepository;
    private final JsonMapper participantJsonMapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final VisitPlanService visitPlanService;
    private final ActivityTimingService activityTimingService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<VisitRecordDtoOut> getMyVisitRecords(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);

        boolean hasAccess = visit.getOrganizer().getId().equals(accountId);

        List<VisitMember> members = visitMemberRepository.findAllByVisit_Id(visitId);
        List<VisitRecord> records = new ArrayList<>();

        for (VisitMember member : members) {

            if (isOwnMember(accountId, member) || isManagedFamilyMember(accountId, member)) {
                hasAccess = true;
                records.addAll(visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(member.getId()));
            }
        }

        if (!hasAccess) {
            throw new ApiException("You are not a member of this visit");
        }


        records.sort(Comparator.comparing(VisitRecord::getCreatedAt).thenComparing(VisitRecord::getId).reversed());

        List<VisitRecordDtoOut> results = new ArrayList<>();

        for (VisitRecord record : records) {
            Integer planItemId = record.getPlanItem() == null ? null : record.getPlanItem().getId();
            Integer activitySessionId = record.getActivitySession() == null ? null : record.getActivitySession().getId();

            results.add(new VisitRecordDtoOut(record.getId(), record.getRequestKey(), record.getVisitMember().getId(), planItemId, activitySessionId, record.getRide().getId(), record.getRide().getName(), record.getRecordedByAccount().getId(), record.getStatus(), record.getCompletedAt(), record.getSkippedAt(), record.getRating(), record.getReviewNote(), record.getCorrectedAt(), record.getCreatedAt()));
        }

        return results;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void createVisitRecords(Integer accountId, Integer visitId, CreateVisitRecordDtoIn dto, int statusInt) {

        if (statusInt != 1 && statusInt != 2) {
            throw new ApiException("Ride record action must be 1 or 2");
        }
        String status = statusInt == 1 ? "COMPLETED" : "SKIPPED";


        if (accountId == null || accountId <= 0 || visitId == null || visitId <= 0) {
            throw new ApiException("Positive account and visit IDs are required");
        }

        validateCreateVisitRecordRequest(dto);

        HashSet<Integer> selectedMemberIds = new HashSet<>(dto.getVisitMemberIds());

        Account account = visitRecordRepository.findRecordingAccountByIdForUpdate(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        VisitMember recordingMember = getRequiredAccountMember(accountId, visitId);

        if (!"GOING".equals(recordingMember.getRsvpStatus())) {
            throw new ApiException("Only GOING members can record ride activities");
        }

        ActivitySession suppliedSession = null;
        Integer planItemId = dto.getPlanItemId();

        if (dto.getActivitySessionId() != null) {

            suppliedSession = activitySessionRepository.findActivitySessionById(dto.getActivitySessionId());

            if (suppliedSession == null) {
                throw new ApiException("Activity session not found");
            }

            if (!selectedMemberIds.contains(suppliedSession.getVisitMember().getId())) {
                throw new ApiException("The activity session must belong to the selected member");
            }

            if (planItemId != null && !planItemId.equals(suppliedSession.getPlanItem().getId())) {
                throw new ApiException("Plan item and activity session do not match");
            }

            planItemId = suppliedSession.getPlanItem().getId();
        }

        PlanItem planItem = visitPlanService.getCanonicalExecutionPlanItem(accountId, planItemId);

        VisitPlan lockedPlan = visitPlanRepository.findVisitPlanByIdForUpdate(planItem.getVisitPlan().getId());

        if (lockedPlan == null) {
            throw new ApiException("The execution plan is unavailable");
        }

        entityManager.refresh(visit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(lockedPlan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(planItem, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!planItem.getVisitPlan().getVisit().getId().equals(visitId)) {
            throw new ApiException("The activity does not belong to this visit");
        }

        if (!"RIDE".equals(planItem.getActivityKind()) || planItem.getRide() == null) {
            throw new ApiException("The activity must reference a ride");
        }

        if (!planItem.getRide().getId().equals(dto.getRideId())) {
            throw new ApiException("The ride does not match the activity");
        }

        if ("INDIVIDUAL".equals(planItem.getVisitPlan().getPlanKind())) {

            VisitMember owner = planItem.getVisitPlan().getOwnerVisitMember();

            if (owner == null || !owner.getVisit().getId().equals(visitId) || selectedMemberIds.size() != 1 || !selectedMemberIds.contains(owner.getId())) {
                throw new ApiException("Individual activity records must belong to the plan owner");
            }

            if (!isOwnMember(accountId, owner)) {
                throw new ApiException("Only the plan owner can record an individual activity");
            }

        } else if (!"GROUP".equals(planItem.getVisitPlan().getPlanKind())) {
            throw new ApiException("Invalid plan kind");
        }

        HashSet<Integer> participantIds = parseRideActivityParticipantIds(planItem.getParticipantVisitMemberIds());

        if (!participantIds.containsAll(selectedMemberIds)) {
            throw new ApiException("Every selected member must participate in this activity");
        }

        boolean isRecordingParticipant = participantIds.contains(recordingMember.getId());

        List<Integer> orderedMemberIds = new ArrayList<>(selectedMemberIds);
        orderedMemberIds.sort(Integer::compareTo);

        List<VisitMember> selectedMembers = new ArrayList<>();

        for (Integer memberId : orderedMemberIds) {

            VisitMember member = activitySessionRepository.findVisitMemberByIdForActivityUpdate(memberId);

            if (member == null || !member.getVisit().getId().equals(visitId)) {
                throw new ApiException("Every selected member must belong to this visit");
            }

            if (member.getVisitorProfile() == null) {

                if (!isManagedFamilyMember(accountId, member)) {
                    throw new ApiException("Only the authorized organizer can record this family member");
                }

            } else if (!isOwnMember(accountId, member) && !isRecordingParticipant) {
                throw new ApiException("You must participate in the activity to record other account members");
            }

            selectedMembers.add(member);
        }

        List<VisitRecord> previousRecords = visitRecordRepository.findAllByRecordedByAccount_IdAndRequestKeyOrderByIdAsc(accountId, dto.getRequestKey());

        if (!previousRecords.isEmpty()) {

            validateRepeatedRideRecordRequest(previousRecords, dto, visitId, planItem.getId(), selectedMemberIds, status);

            return;
        }

        if (!"IN_PROGRESS".equals(visit.getStatus())) {
            throw new ApiException("Ride activities can only be recorded during the visit");
        }

        if (!Boolean.TRUE.equals(planItem.getVisitPlan().getIsActive())) {
            throw new ApiException("The activity must belong to an active plan");
        }

        if (!"SCHEDULED".equals(planItem.getVisitPlan().getTimingMode()) || planItem.getStartsAt() == null || planItem.getEndsAt() == null || !planItem.getEndsAt().isAfter(planItem.getStartsAt())) {
            throw new ApiException("The activity must have valid scheduled times");
        }

        LocalDateTime recordedAt = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));

        List<VisitRecord> records = new ArrayList<>();

        for (VisitMember member : selectedMembers) {

            if (!"GOING".equals(member.getRsvpStatus())) {
                throw new ApiException("Every selected member must be GOING");
            }

            VisitRecord existingRecord = visitRecordRepository.findVisitRecordByVisitMember_IdAndPlanItem_Id(member.getId(), planItem.getId());

            if (existingRecord != null) {
                throw new ApiException("This activity already has a record for a selected member; use status correction if needed");
            }

            List<ActivitySession> sessions = activitySessionRepository.findAllByVisitMember_IdAndPlanItem_IdOrderByIdAsc(member.getId(), planItem.getId());

            if (sessions.size() > 1) {
                throw new ApiException("Duplicate activity sessions were found");
            }

            ActivitySession session = null;

            if (!sessions.isEmpty()) {

                session = activitySessionRepository.findActivitySessionByIdForUpdate(sessions.get(0).getId());

                if (session == null) {
                    throw new ApiException("Activity session not found");
                }
            }

            if (suppliedSession != null && (session == null || !session.getId().equals(suppliedSession.getId()))) {
                throw new ApiException("The supplied activity session does not match the member's activity");
            }

            if (session != null) {

                validateRideSessionReference(session, member.getId(), planItem.getId(), dto.getRideId());

                if (!"WAITING".equals(session.getStatus()) && !"ACTIVE".equals(session.getStatus())) {
                    throw new ApiException("The activity session has already been completed or skipped");
                }

                session.setStatus(status);
                session.setCompletedAt("COMPLETED".equals(status) ? recordedAt : null);

                activitySessionRepository.save(session);
            }

            VisitRecord record = new VisitRecord();
            record.setRequestKey(dto.getRequestKey());
            record.setVisitMember(member);
            record.setPlanItem(planItem);
            record.setActivitySession(session);
            record.setRide(planItem.getRide());
            record.setRecordedByAccount(account);
            record.setStatus(status);
            record.setCompletedAt("COMPLETED".equals(status) ? recordedAt : null);
            record.setSkippedAt("SKIPPED".equals(status) ? recordedAt : null);

            records.add(record);
        }

        visitRecordRepository.saveAll(records);

        if ("COMPLETED".equals(status)) {
            activityTimingService.advanceRemainingActivitiesAfterCompletion(visit, planItem, recordedAt, this::parseRideActivityParticipantIds);
        }
    }

    @Transactional
    public void updateVisitRecordStatus(Integer accountId, UpdateVisitRecordDtoIn dto, int statusInt) {

        if (statusInt != 1 && statusInt != 2) {
            throw new ApiException("Ride record correction action must be 1 or 2");
        }
        String status = statusInt == 1 ? "COMPLETED" : "SKIPPED";


        validateActiveAccount(accountId);

        if (dto == null || dto.getVisitRecordId() == null || dto.getVisitRecordId() <= 0) {
            throw new ApiException("A positive visit record ID is required");
        }

        VisitRecord record = visitRecordRepository.findVisitRecordByIdForUpdate(dto.getVisitRecordId());

        if (record == null) {
            throw new ApiException("Visit record not found");
        }

        VisitMember member = record.getVisitMember();
        Visit visit = member.getVisit();

        boolean isOriginalRecorder = record.getRecordedByAccount().getId().equals(accountId);

        if (!isOwnMember(accountId, member) && !isManagedFamilyMember(accountId, member) && !isOriginalRecorder) {
            throw new ApiException("You cannot correct this ride record");
        }

        VisitMember actingMember = getRequiredAccountMember(accountId, visit.getId());

        if (!"GOING".equals(actingMember.getRsvpStatus())) {
            throw new ApiException("Only GOING members can correct ride activities");
        }

        if (!"IN_PROGRESS".equals(visit.getStatus()) && !"COMPLETED".equals(visit.getStatus())) {
            throw new ApiException("Records can only be corrected during or after a completed visit");
        }

        if (status.equals(record.getStatus())) {
            return;
        }

        LocalDateTime correctedAt = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));

        if (record.getActivitySession() != null) {

            ActivitySession session = activitySessionRepository.findActivitySessionByIdForUpdate(record.getActivitySession().getId());

            if (session == null || record.getPlanItem() == null) {
                throw new ApiException("The ride record has an invalid activity session reference");
            }

            validateRideSessionReference(session, member.getId(), record.getPlanItem().getId(), record.getRide().getId());

            session.setStatus(status);
            session.setCompletedAt("COMPLETED".equals(status) ? correctedAt : null);

            activitySessionRepository.save(session);
        }

        record.setStatus(status);
        record.setCorrectedAt(correctedAt);
        record.setCompletedAt("COMPLETED".equals(status) ? correctedAt : null);
        record.setSkippedAt("SKIPPED".equals(status) ? correctedAt : null);

        if ("SKIPPED".equals(status)) {
            record.setRating(null);
            record.setReviewNote(null);
        }

        visitRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedRide(Integer memberId, Integer rideId) {

        if (memberId == null || memberId <= 0 || rideId == null || rideId <= 0) {
            throw new ApiException("Positive member and ride IDs are required");
        }

        return visitRecordRepository.existsByVisitMember_IdAndRide_IdAndStatus(memberId, rideId, "COMPLETED");
    }

    @Transactional
    public void rateVisitRecord(Integer accountId, RateVisitRecordDtoIn dto) {

        validateActiveAccount(accountId);

        if (dto == null || dto.getVisitRecordId() == null || dto.getVisitRecordId() <= 0) {
            throw new ApiException("A positive visit record ID is required");
        }

        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new ApiException("Rating must be between 1 and 5");
        }

        VisitRecord record = visitRecordRepository.findVisitRecordByIdForUpdate(dto.getVisitRecordId());

        if (record == null) {
            throw new ApiException("Visit record not found");
        }

        VisitMember member = record.getVisitMember();

        if (!isOwnMember(accountId, member) && !isManagedFamilyMember(accountId, member)) {
            throw new ApiException("You can only rate your own ride records or managed family records");
        }

        if (!"COMPLETED".equals(record.getStatus())) {
            throw new ApiException("Only completed rides can be rated");
        }

        record.setRating(dto.getRating());
        record.setReviewNote(dto.getReviewNote());

        visitRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public VisitSummaryDtoOut getMyVisitSummary(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);
        VisitMember member = getRequiredAccountMember(accountId, visitId);

        if (!"COMPLETED".equals(visit.getStatus())) {
            throw new ApiException("The visit must be completed before viewing its summary");
        }

        List<VisitRecord> records = visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(member.getId());

        HashSet<Integer> completedRideIds = new HashSet<>();

        int completedRidesCount = 0;
        int skippedRidesCount = 0;
        int ratingsCount = 0;
        int ratingsTotal = 0;

        for (VisitRecord record : records) {

            if ("COMPLETED".equals(record.getStatus())) {

                completedRidesCount++;
                completedRideIds.add(record.getRide().getId());

                if (record.getRating() != null) {
                    ratingsCount++;
                    ratingsTotal += record.getRating();
                }

            } else if ("SKIPPED".equals(record.getStatus())) {
                skippedRidesCount++;
            }
        }

        List<ActivitySession> sessions = activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(member.getId());

        HashSet<Integer> countedOtherActivityIds = new HashSet<>();

        int completedOtherActivitiesCount = 0;
        int skippedOtherActivitiesCount = 0;
        int otherActivitiesRatingsCount = 0;
        int otherActivitiesRatingsTotal = 0;

        for (ActivitySession session : sessions) {

            PlanItem planItem = session.getPlanItem();

            if ("RIDE".equals(planItem.getActivityKind())) {
                continue;
            }

            if (!planItem.getVisitPlan().getVisit().getId().equals(visitId)) {
                throw new ApiException("An activity session does not belong to this visit");
            }

            if (!countedOtherActivityIds.add(planItem.getId())) {
                continue;
            }

            if ("COMPLETED".equals(session.getStatus())) {

                completedOtherActivitiesCount++;

                if (!"RIDE".equals(planItem.getActivityKind()) && !"PRAYER".equals(planItem.getBreakType()) && Boolean.TRUE.equals(planItem.getIsRateable()) && session.getRating() != null) {
                    otherActivitiesRatingsCount++;
                    otherActivitiesRatingsTotal += session.getRating();
                }

            } else if ("SKIPPED".equals(session.getStatus())) {
                skippedOtherActivitiesCount++;
            }
        }

        int uniqueCompletedRidesCount = completedRideIds.size();
        int repeatedRidesCount = completedRidesCount - uniqueCompletedRidesCount;

        Double averageRating = calculateAverageRating(ratingsTotal, ratingsCount);
        Double averageOtherActivitiesRating = calculateAverageRating(otherActivitiesRatingsTotal, otherActivitiesRatingsCount);

        return new VisitSummaryDtoOut(visitId, member.getId(), completedRidesCount, uniqueCompletedRidesCount, repeatedRidesCount, skippedRidesCount, ratingsCount, averageRating, completedOtherActivitiesCount, skippedOtherActivitiesCount, otherActivitiesRatingsCount, averageOtherActivitiesRating);
    }

    @Transactional(readOnly = true)
    public VisitProgressDtoOut getMyVisitProgress(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);
        getRequiredVisit(visitId);

        VisitMember member = getRequiredAccountMember(accountId, visitId);

        if (!"GOING".equals(member.getRsvpStatus())) {
            throw new ApiException("Only GOING members can view visit progress");
        }

        List<PlanItem> plannedActivities = getMemberPlannedActivities(member);

        HashSet<Integer> plannedActivityIds = new HashSet<>();
        HashSet<Integer> plannedRideIds = new HashSet<>();

        for (PlanItem planItem : plannedActivities) {

            plannedActivityIds.add(planItem.getId());

            if ("RIDE".equals(planItem.getActivityKind())) {

                if (planItem.getRide() == null) {
                    throw new ApiException("A ride activity must reference a ride");
                }

                plannedRideIds.add(planItem.getRide().getId());
            }
        }

        List<VisitRecord> records = visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(member.getId());

        HashSet<Integer> completedPlannedRideIds = new HashSet<>();
        Map<Integer, String> activityStatuses = new HashMap<>();

        for (VisitRecord record : records) {

            if ("COMPLETED".equals(record.getStatus()) && plannedRideIds.contains(record.getRide().getId())) {
                completedPlannedRideIds.add(record.getRide().getId());
            }

            if (record.getPlanItem() == null || !plannedActivityIds.contains(record.getPlanItem().getId())) {
                continue;
            }

            if (!"RIDE".equals(record.getPlanItem().getActivityKind())) {
                throw new ApiException("A ride record references a non-ride activity");
            }

            if (record.getPlanItem().getRide() == null || !record.getPlanItem().getRide().getId().equals(record.getRide().getId())) {
                throw new ApiException("A ride record does not match its activity");
            }

            activityStatuses.putIfAbsent(record.getPlanItem().getId(), record.getStatus());
        }

        List<ActivitySession> sessions = activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(member.getId());

        for (ActivitySession session : sessions) {

            PlanItem planItem = session.getPlanItem();

            if ("RIDE".equals(planItem.getActivityKind()) || !plannedActivityIds.contains(planItem.getId())) {
                continue;
            }

            activityStatuses.putIfAbsent(planItem.getId(), session.getStatus());
        }

        int completedActivitiesCount = 0;
        int skippedActivitiesCount = 0;

        for (String status : activityStatuses.values()) {

            if ("COMPLETED".equals(status)) {
                completedActivitiesCount++;
            } else if ("SKIPPED".equals(status)) {
                skippedActivitiesCount++;
            }
        }

        int plannedRidesCount = plannedRideIds.size();
        int completedPlannedRidesCount = completedPlannedRideIds.size();
        int remainingRidesCount = plannedRidesCount - completedPlannedRidesCount;

        int plannedActivitiesCount = plannedActivityIds.size();
        int remainingActivitiesCount = plannedActivitiesCount - completedActivitiesCount - skippedActivitiesCount;

        Double completionPercentage = calculateCompletionPercentage(completedPlannedRidesCount, plannedRidesCount);
        Double activityCompletionPercentage = calculateCompletionPercentage(completedActivitiesCount, plannedActivitiesCount);

        return new VisitProgressDtoOut(visitId, plannedRidesCount, completedPlannedRidesCount, remainingRidesCount, completionPercentage, plannedActivitiesCount, completedActivitiesCount, skippedActivitiesCount, remainingActivitiesCount, activityCompletionPercentage);
    }

    private Account validateActiveAccount(Integer accountId) {

        if (accountId == null || accountId <= 0) {
            throw new ApiException("A positive account ID is required");
        }

        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        return account;
    }

    private Visit getRequiredVisit(Integer visitId) {

        if (visitId == null || visitId <= 0) {
            throw new ApiException("A positive visit ID is required");
        }

        Visit visit = visitRepository.findVisitById(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        return visit;
    }

    private VisitMember getRequiredAccountMember(Integer accountId, Integer visitId) {

        VisitMember member = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId);

        if (member == null) {
            throw new ApiException("You are not a member of this visit");
        }

        return member;
    }

    private boolean isOwnMember(Integer accountId, VisitMember member) {
        return member.getVisitorProfile() != null && member.getVisitorProfile().getAccount().getId().equals(accountId);
    }

    private boolean isManagedFamilyMember(Integer accountId, VisitMember member) {
        return member.getVisit().getOrganizer().getId().equals(accountId) && member.getVisitorProfile() == null && (member.getFamilyMember() != null && member.getFamilyMember().getOwnerAccount().getId().equals(accountId) || member.getFamilyMember() == null && member.getSnapshotAt() != null && "FAMILY".equals(member.getVisit().getTripType()));
    }

    private void validateCreateVisitRecordRequest(CreateVisitRecordDtoIn dto) {

        if (dto == null) {
            throw new ApiException("Ride record details are required");
        }

        if (dto.getRequestKey() == null || dto.getRequestKey().isBlank() || dto.getRequestKey().length() > 80) {
            throw new ApiException("A request key between 1 and 80 characters is required");
        }

        if (dto.getRideId() == null || dto.getRideId() <= 0) {
            throw new ApiException("A positive ride ID is required");
        }

        if (dto.getVisitMemberIds() == null || dto.getVisitMemberIds().isEmpty()) {
            throw new ApiException("Select at least one visit member");
        }

        HashSet<Integer> selectedMemberIds = new HashSet<>();

        for (Integer memberId : dto.getVisitMemberIds()) {

            if (memberId == null || memberId <= 0) {
                throw new ApiException("Visit member IDs must be positive");
            }

            if (!selectedMemberIds.add(memberId)) {
                throw new ApiException("Visit member IDs must not be repeated");
            }
        }

        if (dto.getPlanItemId() == null && dto.getActivitySessionId() == null) {
            throw new ApiException("Plan item ID or activity session ID is required");
        }

        if (dto.getPlanItemId() != null && dto.getPlanItemId() <= 0) {
            throw new ApiException("Plan item ID must be positive");
        }

        if (dto.getActivitySessionId() != null && dto.getActivitySessionId() <= 0) {
            throw new ApiException("Activity session ID must be positive");
        }

        if (dto.getActivitySessionId() != null && selectedMemberIds.size() != 1) {
            throw new ApiException("Use plan item ID without activity session ID when recording multiple members");
        }
    }

    private void validateRepeatedRideRecordRequest(List<VisitRecord> records, CreateVisitRecordDtoIn dto, Integer visitId, Integer planItemId, HashSet<Integer> selectedMemberIds, String status) {

        HashSet<Integer> recordedMemberIds = new HashSet<>();

        for (VisitRecord record : records) {

            if (!record.getVisitMember().getVisit().getId().equals(visitId) || record.getPlanItem() == null || !record.getPlanItem().getId().equals(planItemId) || !record.getRide().getId().equals(dto.getRideId()) || !record.getStatus().equals(status)) {
                throw new ApiException("The request key was already used with different ride record details");
            }

            if (dto.getActivitySessionId() != null && (record.getActivitySession() == null || !record.getActivitySession().getId().equals(dto.getActivitySessionId()))) {
                throw new ApiException("The request key was already used with a different activity session");
            }

            if (!recordedMemberIds.add(record.getVisitMember().getId())) {
                throw new ApiException("Duplicate records were found for this request key");
            }
        }

        if (!recordedMemberIds.equals(selectedMemberIds)) {
            throw new ApiException("The request key was already used with different selected members");
        }
    }

    private void validateRideSessionReference(ActivitySession session, Integer visitMemberId, Integer planItemId, Integer rideId) {

        if (!session.getVisitMember().getId().equals(visitMemberId) || !session.getPlanItem().getId().equals(planItemId)) {
            throw new ApiException("The activity session does not match the member and activity");
        }

        if (!"RIDE".equals(session.getPlanItem().getActivityKind()) || session.getPlanItem().getRide() == null || !session.getPlanItem().getRide().getId().equals(rideId)) {
            throw new ApiException("The activity session references a different ride activity");
        }

        if (session.getRide() != null && !session.getRide().getId().equals(rideId)) {
            throw new ApiException("The activity session references a different ride");
        }
    }

    private HashSet<Integer> parseRideActivityParticipantIds(String participantVisitMemberIds) {

        if (participantVisitMemberIds == null || participantVisitMemberIds.isBlank()) {
            throw new ApiException("Activity participants are required");
        }

        JsonNode participantIdsNode;

        try {
            participantIdsNode = participantJsonMapper.readTree(participantVisitMemberIds);
        } catch (JacksonException exception) {
            throw new ApiException("Activity participants must contain valid JSON");
        }

        if (participantIdsNode == null || !participantIdsNode.isArray() || participantIdsNode.isEmpty()) {
            throw new ApiException("Activity participants must be a non-empty array");
        }

        HashSet<Integer> participantIds = new HashSet<>();

        for (JsonNode participantIdNode : participantIdsNode) {

            if (!participantIdNode.isIntegralNumber() || !participantIdNode.canConvertToInt() || participantIdNode.intValue() <= 0) {
                throw new ApiException("Activity participant IDs must be positive integers");
            }

            if (!participantIds.add(participantIdNode.intValue())) {
                throw new ApiException("Activity participant IDs must not be repeated");
            }
        }

        return participantIds;
    }

    private List<PlanItem> getMemberPlannedActivities(VisitMember member) {

        List<VisitPlan> plans = visitPlanRepository.findAllByVisit_Id(member.getVisit().getId());

        VisitPlan groupPlan = null;
        VisitPlan individualPlan = null;

        for (VisitPlan plan : plans) {

            if (!Boolean.TRUE.equals(plan.getIsActive())) {
                continue;
            }

            if ("GROUP".equals(plan.getPlanKind())) {

                if (groupPlan != null) {
                    throw new ApiException("Multiple active group plans were found");
                }

                groupPlan = plan;

            } else if ("INDIVIDUAL".equals(plan.getPlanKind()) && plan.getOwnerVisitMember() != null && plan.getOwnerVisitMember().getId().equals(member.getId())) {

                if (individualPlan != null) {
                    throw new ApiException("Multiple active individual plans were found");
                }

                individualPlan = plan;
            }
        }

        VisitPlan selectedPlan = groupPlan != null ? groupPlan : individualPlan;

        if (selectedPlan == null) {
            throw new ApiException("No active plan is available for this member");
        }

        List<PlanItem> planItems = planItemRepository.findAllByVisitPlan_Id(selectedPlan.getId());
        List<PlanItem> results = new ArrayList<>();

        HashSet<Integer> visitMemberIds = new HashSet<>();

        for (VisitMember visitMember : visitMemberRepository.findAllByVisit_Id(member.getVisit().getId())) {
            visitMemberIds.add(visitMember.getId());
        }

        for (PlanItem planItem : planItems) {

            HashSet<Integer> participantIds = parseRideActivityParticipantIds(planItem.getParticipantVisitMemberIds());

            if (!visitMemberIds.containsAll(participantIds)) {
                throw new ApiException("An activity contains participants from outside this visit");
            }

            if (participantIds.contains(member.getId())) {
                results.add(planItem);
            }
        }

        return results;
    }

    private Double calculateAverageRating(int ratingsTotal, int ratingsCount) {

        if (ratingsCount == 0) {
            return null;
        }

        return Math.round((double) ratingsTotal / ratingsCount * 100.0) / 100.0;
    }

    private Double calculateCompletionPercentage(int completedCount, int plannedCount) {

        if (plannedCount == 0) {
            return null;
        }

        return Math.round((double) completedCount / plannedCount * 10000.0) / 100.0;
    }

}