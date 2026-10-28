package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.CompleteActivityDtoIn;
import com.example.sikka.DtoIn.RateActivityDtoIn;
import com.example.sikka.DtoIn.UpdateActivityLocationDtoIn;
import com.example.sikka.DtoOut.ActivitySessionDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.ActivitySession;
import com.example.sikka.Model.PlanItem;
import com.example.sikka.Model.PrivacyConsent;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitPlan;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.ActivitySessionRepository;
import com.example.sikka.Repository.PlanItemRepository;
import com.example.sikka.Repository.PrivacyConsentRepository;
import com.example.sikka.Repository.VisitMemberRepository;
import com.example.sikka.Repository.VisitPlanRepository;
import com.example.sikka.Repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivitySessionService {

    private final ActivitySessionRepository activitySessionRepository;
    private final VisitPlanRepository visitPlanRepository;
    private final ActivityTimingService activityTimingService;
    private final AccountRepository accountRepository;
    private final VisitRepository visitRepository;
    private final VisitMemberRepository visitMemberRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitPlanService visitPlanService;
    private final PrivacyConsentRepository privacyConsentRepository;
    private final JsonMapper participantJsonMapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Value("${sikka.tracking.max-accuracy-meters:50}")
    private double maxLocationAccuracyMeters;

    @Value("${sikka.tracking.max-age-seconds:120}")
    private long maxLocationAgeSeconds;

    @Value("${sikka.tracking.future-tolerance-seconds:15}")
    private long locationFutureToleranceSeconds;

    @Transactional(readOnly = true)
    public List<ActivitySessionDtoOut> getMyActivityHistory(Integer accountId, Integer visitId) {
        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        Visit visit = visitRepository.findVisitById(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        boolean isOrganizer = visit.getOrganizer().getId().equals(accountId);
        List<VisitMember> members = visitMemberRepository.findAllByVisit_Id(visitId);
        List<VisitMember> allowedMembers = new ArrayList<>();

        for (VisitMember member : members) {
            if (member.getVisitorProfile() != null) {
                if (member.getVisitorProfile().getAccount().getId().equals(accountId)) {
                    allowedMembers.add(member);
                }

                continue;
            }

            if (isOrganizer && (member.getFamilyMember() != null && member.getFamilyMember().getOwnerAccount().getId().equals(accountId) || member.getFamilyMember() == null && member.getSnapshotAt() != null && "FAMILY".equals(member.getVisit().getTripType()))) {
                allowedMembers.add(member);
            }
        }

        if (allowedMembers.isEmpty() && !isOrganizer) {
            throw new ApiException("You are not a member of this visit");
        }

        List<ActivitySession> sessions = new ArrayList<>();

        for (VisitMember member : allowedMembers) {
            sessions.addAll(activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(member.getId()));
        }

        sessions.sort(Comparator.comparing(ActivitySession::getId).reversed());
        List<ActivitySessionDtoOut> results = new ArrayList<>();

        for (ActivitySession session : sessions) {
            results.add(convertActivitySessionToDtoOut(session));
        }

        return results;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<ActivitySessionDtoOut> completeActivity(Integer accountId, Integer activitySessionId, CompleteActivityDtoIn dto, List<Integer> selectedMemberIds) {
        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        if (dto == null || dto.getRequestKey() == null || dto.getRequestKey().isBlank() || dto.getRequestKey().length() > 80) {
            throw new ApiException("A request key between 1 and 80 characters is required");
        }

        ActivitySession reference = activitySessionRepository.findActivitySessionById(activitySessionId);

        if (reference == null) {
            throw new ApiException("Activity session not found");
        }

        Visit lockedVisit = visitRepository.findVisitByIdForUpdate(reference.getVisitMember().getVisit().getId());
        VisitPlan lockedPlan = visitPlanRepository.findVisitPlanByIdForUpdate(reference.getPlanItem().getVisitPlan().getId());

        if (lockedVisit == null || lockedPlan == null) {
            throw new ApiException("The execution visit or plan is unavailable");
        }

        entityManager.refresh(lockedVisit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(lockedPlan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (selectedMemberIds != null && (!"FAMILY".equals(lockedVisit.getTripType()) || !lockedVisit.getOrganizer().getId().equals(accountId))) {
            throw new ApiException("Only the organizer can complete activities for a family group");
        }

        List<Integer> participantIdsToComplete = selectedMemberIds == null ? new ArrayList<>(List.of(reference.getVisitMember().getId())) : new ArrayList<>(selectedMemberIds);

        if (participantIdsToComplete.isEmpty() || participantIdsToComplete.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(participantIdsToComplete).size() != participantIdsToComplete.size()) {
            throw new ApiException("Select unique positive participant IDs");
        }

        PlanItem planItem = reference.getPlanItem();
        entityManager.refresh(planItem, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!planItem.getVisitPlan().getVisit().getId().equals(lockedVisit.getId())) {
            throw new ApiException("The activity does not belong to this visit");
        }

        if ("RIDE".equals(planItem.getActivityKind())) {
            throw new ApiException("Use visit records to complete rides for the selected participants");
        }

        HashSet<Integer> activityParticipants = parseActivityParticipantIds(planItem.getParticipantVisitMemberIds());
        participantIdsToComplete.sort(Integer::compareTo);

        List<ActivitySessionDtoOut> results = new ArrayList<>();
        LocalDateTime completedAt = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));
        boolean changed = false;

        for (Integer selectedMemberId : participantIdsToComplete) {
            VisitMember member = activitySessionRepository.findVisitMemberByIdForActivityUpdate(selectedMemberId);

            if (member == null) {
                throw new ApiException("Visit member not found");
            }

            if (!member.getVisit().getId().equals(lockedVisit.getId()) || !activityParticipants.contains(member.getId())) {
                throw new ApiException("The member is not a participant in this activity");
            }

            boolean isOwnActivity = member.getVisitorProfile() != null && member.getVisitorProfile().getAccount().getId().equals(accountId);
            boolean isManagedFamilyActivity = lockedVisit.getOrganizer().getId().equals(accountId) && member.getVisitorProfile() == null && (member.getFamilyMember() != null && member.getFamilyMember().getOwnerAccount().getId().equals(accountId) || member.getFamilyMember() == null && member.getSnapshotAt() != null && "FAMILY".equals(lockedVisit.getTripType()));

            if (!isOwnActivity && !isManagedFamilyActivity) {
                throw new ApiException("You cannot complete this member's activity");
            }

            List<ActivitySession> matchingSessions = activitySessionRepository.findAllByVisitMember_IdAndPlanItem_IdOrderByIdAsc(member.getId(), planItem.getId());

            if (matchingSessions.size() != 1) {
                throw new ApiException("Each selected participant must have exactly one session for this activity");
            }

            ActivitySession session = activitySessionRepository.findActivitySessionByIdForUpdate(matchingSessions.get(0).getId());

            if (session == null) {
                throw new ApiException("Activity session not found");
            }

            entityManager.refresh(session, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

            if (!session.getVisitMember().getId().equals(member.getId()) || !session.getPlanItem().getId().equals(planItem.getId())) {
                throw new ApiException("The activity session no longer matches the selected participant and activity");
            }

            if ("COMPLETED".equals(session.getStatus())) {
                results.add(convertActivitySessionToDtoOut(session));
                continue;
            }

            if ("SKIPPED".equals(session.getStatus())) {
                throw new ApiException("A skipped activity cannot be completed");
            }

            if (!"IN_PROGRESS".equals(lockedVisit.getStatus())) {
                throw new ApiException("Activities can only be completed during the visit");
            }

            if (!"GOING".equals(member.getRsvpStatus())) {
                throw new ApiException("Only GOING members can complete activities");
            }

            if (!Boolean.TRUE.equals(lockedPlan.getIsActive())) {
                throw new ApiException("The activity must belong to an active plan");
            }

            if (!"ACTIVE".equals(session.getStatus())) {
                throw new ApiException("The activity must be active before it can be completed");
            }

            session.setStatus("COMPLETED");
            session.setCompletedAt(completedAt);
            activitySessionRepository.save(session);

            results.add(convertActivitySessionToDtoOut(session));
            changed = true;
        }

        if (changed) {
            activityTimingService.advanceRemainingActivitiesAfterCompletion(lockedVisit, planItem, completedAt, this::parseActivityParticipantIds);
        }

        return results;
    }

    @Transactional
    public ActivitySessionDtoOut rateActivity(Integer accountId, Integer activitySessionId, RateActivityDtoIn rateActivityDtoIn) {
        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        if (rateActivityDtoIn == null || rateActivityDtoIn.getRating() == null || rateActivityDtoIn.getRating() < 1 || rateActivityDtoIn.getRating() > 5) {
            throw new ApiException("Rating must be between 1 and 5");
        }

        if (rateActivityDtoIn.getNote() != null && rateActivityDtoIn.getNote().length() > 1000) {
            throw new ApiException("Note must not exceed 1000 characters");
        }

        ActivitySession session = activitySessionRepository.findActivitySessionByIdForUpdate(activitySessionId);

        if (session == null) {
            throw new ApiException("Activity session not found");
        }

        VisitMember member = session.getVisitMember();
        Visit visit = member.getVisit();

        boolean isOwnActivity = member.getVisitorProfile() != null && member.getVisitorProfile().getAccount().getId().equals(accountId);
        boolean isManagedFamilyActivity = visit.getOrganizer().getId().equals(accountId) && member.getVisitorProfile() == null && (member.getFamilyMember() != null && member.getFamilyMember().getOwnerAccount().getId().equals(accountId) || member.getFamilyMember() == null && member.getSnapshotAt() != null && "FAMILY".equals(member.getVisit().getTripType()));

        if (!isOwnActivity && !isManagedFamilyActivity) {
            throw new ApiException("You cannot rate this member's activity");
        }

        if (!session.getPlanItem().getVisitPlan().getVisit().getId().equals(visit.getId())) {
            throw new ApiException("The activity does not belong to this visit");
        }

        if ("RIDE".equals(session.getPlanItem().getActivityKind())) {
            throw new ApiException("Use visit records to rate rides");
        }

        if ("PRAYER".equals(session.getPlanItem().getBreakType()) || !Boolean.TRUE.equals(session.getPlanItem().getIsRateable())) {
            throw new ApiException("This activity cannot be rated");
        }

        if (!"COMPLETED".equals(session.getStatus())) {
            throw new ApiException("Only completed activities can be rated");
        }

        session.setRating(rateActivityDtoIn.getRating());
        session.setReviewNote(rateActivityDtoIn.getNote());
        activitySessionRepository.save(session);

        return convertActivitySessionToDtoOut(session);
    }

    @Transactional(readOnly = true)
    public ActivitySessionDtoOut getMyCurrentActivity(Integer accountId, Integer visitId) {
        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        Visit visit = visitRepository.findVisitById(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        VisitMember member = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId);

        if (member == null) {
            throw new ApiException("You are not a member of this visit");
        }

        if (!"GOING".equals(member.getRsvpStatus())) {
            throw new ApiException("Only GOING members can view their current activity");
        }

        if (!"IN_PROGRESS".equals(visit.getStatus())) {
            throw new ApiException("The visit must be in progress");
        }

        List<ActivitySession> sessions = activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(member.getId());
        ActivitySession currentSession = null;

        for (ActivitySession session : sessions) {
            if (!"WAITING".equals(session.getStatus()) && !"ACTIVE".equals(session.getStatus())) {
                continue;
            }

            if (!session.getPlanItem().getVisitPlan().getVisit().getId().equals(visitId)) {
                throw new ApiException("The activity does not belong to this visit");
            }

            if (!Boolean.TRUE.equals(session.getPlanItem().getVisitPlan().getIsActive())) {
                continue;
            }

            if (currentSession != null) {
                throw new ApiException("Multiple current activity sessions were found for this member");
            }

            currentSession = session;
        }

        if (currentSession == null) {
            return null;
        }

        return convertActivitySessionToDtoOut(currentSession);
    }

    private ActivitySessionDtoOut convertActivitySessionToDtoOut(ActivitySession session) {
        Integer rideId = session.getRide() == null ? null : session.getRide().getId();
        Integer placeId = session.getPlace() == null ? null : session.getPlace().getId();
        boolean isRide = "RIDE".equals(session.getPlanItem().getActivityKind());
        boolean isPrayer = "PRAYER".equals(session.getPlanItem().getBreakType());
        boolean isRateable = !isRide && !isPrayer && Boolean.TRUE.equals(session.getPlanItem().getIsRateable());

        return new ActivitySessionDtoOut(session.getId(), session.getVisitMember().getId(), session.getPlanItem().getId(), rideId, placeId, session.getStatus(), session.getTrackingMode(), session.getEnteredAreaAt(), session.getQueueStartedAt(), session.getCompletedAt(), session.getExpectedWaitMinutes(), session.getExpectedRideMinutes(), session.getBufferMinutes(), session.getLastLocationAt(), session.getDelayReason(), session.getDelayRespondedAt(), isRateable, session.getRating(), session.getReviewNote());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<ActivitySessionDtoOut> startManualActivity(Integer accountId, Integer visitMemberId, Integer planItemId, List<Integer> selectedMemberIds) {
        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        PlanItem startReference = planItemRepository.findPlanItemById(planItemId);

        if (startReference == null) {
            throw new ApiException("Plan item not found");
        }

        Visit lockedVisit = visitRepository.findVisitByIdForUpdate(startReference.getVisitPlan().getVisit().getId());
        VisitPlan lockedPlan = visitPlanRepository.findVisitPlanByIdForUpdate(startReference.getVisitPlan().getId());

        if (lockedVisit == null || lockedPlan == null) {
            throw new ApiException("The execution visit or plan is unavailable");
        }

        entityManager.refresh(startReference, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(lockedVisit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(lockedPlan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (selectedMemberIds == null && (visitMemberId == null || visitMemberId <= 0)) {
            throw new ApiException("A positive participant ID is required");
        }

        List<Integer> participantIdsToStart = selectedMemberIds == null ? new ArrayList<>(List.of(visitMemberId)) : new ArrayList<>(selectedMemberIds);

        if (participantIdsToStart.isEmpty() || participantIdsToStart.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(participantIdsToStart).size() != participantIdsToStart.size()) {
            throw new ApiException("Select unique positive participant IDs");
        }

        if (selectedMemberIds != null && (!"FAMILY".equals(lockedVisit.getTripType()) || !lockedVisit.getOrganizer().getId().equals(accountId))) {
            throw new ApiException("Only the organizer can start activities for a family group");
        }

        participantIdsToStart.sort(Integer::compareTo);
        List<ActivitySessionDtoOut> results = new ArrayList<>();

        for (Integer selectedMemberId : participantIdsToStart) {
            VisitMember member = activitySessionRepository.findVisitMemberByIdForActivityUpdate(selectedMemberId);

            if (member == null) {
                throw new ApiException("Visit member not found");
            }

            Visit visit = member.getVisit();

            boolean isOwnActivity = member.getVisitorProfile() != null && member.getVisitorProfile().getAccount().getId().equals(accountId);
            boolean isManagedFamilyActivity = visit.getOrganizer().getId().equals(accountId) && member.getVisitorProfile() == null && (member.getFamilyMember() != null && member.getFamilyMember().getOwnerAccount().getId().equals(accountId) || member.getFamilyMember() == null && member.getSnapshotAt() != null && "FAMILY".equals(member.getVisit().getTripType()));

            if (!isOwnActivity && !isManagedFamilyActivity) {
                throw new ApiException("You cannot start this member's activity");
            }

            if (!"IN_PROGRESS".equals(visit.getStatus())) {
                throw new ApiException("Activities can only be started during the visit");
            }

            if (!"GOING".equals(member.getRsvpStatus())) {
                throw new ApiException("Only GOING members can start activities");
            }

            PlanItem planItem = visitPlanService.getCanonicalExecutionPlanItem(accountId, planItemId);

            if (!planItem.getVisitPlan().getVisit().getId().equals(visit.getId())) {
                throw new ApiException("The activity does not belong to this visit");
            }

            if (!Boolean.TRUE.equals(planItem.getVisitPlan().getIsActive())) {
                throw new ApiException("The activity must belong to an active plan");
            }

            if ("INDIVIDUAL".equals(planItem.getVisitPlan().getPlanKind())) {
                if (planItem.getVisitPlan().getOwnerVisitMember() == null || !planItem.getVisitPlan().getOwnerVisitMember().getId().equals(member.getId())) {
                    throw new ApiException("The individual plan does not belong to this member");
                }
            }

            HashSet<Integer> participantIds = parseActivityParticipantIds(planItem.getParticipantVisitMemberIds());

            if (!participantIds.contains(member.getId())) {
                throw new ApiException("The member is not a participant in this activity");
            }

            if (planItem.getStartsAt() == null || planItem.getEndsAt() == null || !planItem.getEndsAt().isAfter(planItem.getStartsAt())) {
                throw new ApiException("The activity must have valid scheduled times");
            }

            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));

            List<ActivitySession> matchingSessions = activitySessionRepository.findAllByVisitMember_IdAndPlanItem_IdOrderByIdAsc(member.getId(), planItem.getId());

            if (matchingSessions.size() > 1) {
                throw new ApiException("Duplicate activity sessions were found for this member and activity");
            }

            ActivitySession session = matchingSessions.isEmpty() ? null : matchingSessions.get(0);

            if (session != null && ("COMPLETED".equals(session.getStatus()) || "SKIPPED".equals(session.getStatus()))) {
                throw new ApiException("This activity has already been completed or skipped");
            }

            List<ActivitySession> memberSessions = activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(member.getId());

            for (ActivitySession otherSession : memberSessions) {
                if (session != null && otherSession.getId().equals(session.getId())) {
                    continue;
                }

                if ("WAITING".equals(otherSession.getStatus()) || "ACTIVE".equals(otherSession.getStatus())) {
                    throw new ApiException("Finish the current activity before starting another activity");
                }
            }

            if (session == null) {
                session = new ActivitySession();
                session.setVisitMember(member);
                session.setPlanItem(planItem);
                session.setRide(planItem.getRide());
                session.setPlace(planItem.getPlace());
                session.setExpectedWaitMinutes(planItem.getWaitingMinutes());
                session.setExpectedRideMinutes("RIDE".equals(planItem.getActivityKind()) ? planItem.getRide().getRideDurationMinutes() : null);
                session.setBufferMinutes("RIDE".equals(planItem.getActivityKind()) ? planItem.getRide().getEntryExitBufferMinutes() : 0);
            }

            session.setAreaCandidateSince(null);
            session.setAreaCandidateReadings(0);
            session.setAreaCandidateDefinition(null);
            session.setTrackingMode("MANUAL");
            session.setStatus("ACTIVE");
            activitySessionRepository.save(session);

            results.add(convertActivitySessionToDtoOut(session));
        }

        return results;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ActivitySessionDtoOut startGpsActivity(Integer accountId, Integer visitMemberId, Integer planItemId) {
        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        PlanItem startReference = planItemRepository.findPlanItemById(planItemId);

        if (startReference == null) {
            throw new ApiException("Plan item not found");
        }

        Visit lockedVisit = visitRepository.findVisitByIdForUpdate(startReference.getVisitPlan().getVisit().getId());
        VisitPlan lockedPlan = visitPlanRepository.findVisitPlanByIdForUpdate(startReference.getVisitPlan().getId());

        if (lockedVisit == null || lockedPlan == null) {
            throw new ApiException("The execution visit or plan is unavailable");
        }

        entityManager.refresh(startReference, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(lockedVisit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(lockedPlan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        VisitMember member = activitySessionRepository.findVisitMemberByIdForActivityUpdate(visitMemberId);

        if (member == null) {
            throw new ApiException("Visit member not found");
        }

        Visit visit = member.getVisit();

        boolean isOwnActivity = member.getVisitorProfile() != null && member.getVisitorProfile().getAccount().getId().equals(accountId);
        boolean isManagedFamilyActivity = visit.getOrganizer().getId().equals(accountId) && member.getVisitorProfile() == null && (member.getFamilyMember() != null && member.getFamilyMember().getOwnerAccount().getId().equals(accountId) || member.getFamilyMember() == null && member.getSnapshotAt() != null && "FAMILY".equals(member.getVisit().getTripType()));

        if (!isOwnActivity && !isManagedFamilyActivity) {
            throw new ApiException("You cannot start this member's activity");
        }

        if (!"IN_PROGRESS".equals(visit.getStatus())) {
            throw new ApiException("Activities can only be started during the visit");
        }

        if (!"GOING".equals(member.getRsvpStatus())) {
            throw new ApiException("Only GOING members can start activities");
        }

        PlanItem planItem = visitPlanService.getCanonicalExecutionPlanItem(accountId, planItemId);

        if (!planItem.getVisitPlan().getVisit().getId().equals(visit.getId())) {
            throw new ApiException("The activity does not belong to this visit");
        }

        if (!Boolean.TRUE.equals(planItem.getVisitPlan().getIsActive())) {
            throw new ApiException("The activity must belong to an active plan");
        }

        if ("INDIVIDUAL".equals(planItem.getVisitPlan().getPlanKind())) {
            if (planItem.getVisitPlan().getOwnerVisitMember() == null || !planItem.getVisitPlan().getOwnerVisitMember().getId().equals(member.getId())) {
                throw new ApiException("The individual plan does not belong to this member");
            }
        }

        HashSet<Integer> participantIds = parseActivityParticipantIds(planItem.getParticipantVisitMemberIds());

        if (!participantIds.contains(member.getId())) {
            throw new ApiException("The member is not a participant in this activity");
        }

        if (planItem.getStartsAt() == null || planItem.getEndsAt() == null || !planItem.getEndsAt().isAfter(planItem.getStartsAt())) {
            throw new ApiException("The activity must have valid scheduled times");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));

        List<ActivitySession> matchingSessions = activitySessionRepository.findAllByVisitMember_IdAndPlanItem_IdOrderByIdAsc(member.getId(), planItem.getId());

        if (matchingSessions.size() > 1) {
            throw new ApiException("Duplicate activity sessions were found for this member and activity");
        }

        ActivitySession session = matchingSessions.isEmpty() ? null : matchingSessions.get(0);

        if (session != null && ("COMPLETED".equals(session.getStatus()) || "SKIPPED".equals(session.getStatus()))) {
            throw new ApiException("This activity has already been completed or skipped");
        }

        List<ActivitySession> memberSessions = activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(member.getId());

        for (ActivitySession otherSession : memberSessions) {
            if (session != null && otherSession.getId().equals(session.getId())) {
                continue;
            }

            if ("WAITING".equals(otherSession.getStatus()) || "ACTIVE".equals(otherSession.getStatus())) {
                throw new ApiException("Finish the current activity before starting another activity");
            }
        }

        if (member.getVisitorProfile() == null || !member.getVisitorProfile().getAccount().getId().equals(accountId)) {
            throw new ApiException("GPS tracking requires the member's own location sharing consent");
        }

        PrivacyConsent consent = privacyConsentRepository.findTopByVisitorProfileIdAndConsentKindOrderByCreatedAtDescIdDesc(member.getVisitorProfile().getId(), "LOCATION_SHARING");

        if (consent == null || !Boolean.TRUE.equals(consent.getGranted()) || consent.getRevokedAt() != null) {
            throw new ApiException("Active location sharing consent is required");
        }

        if (!"RIDE".equals(planItem.getActivityKind()) || planItem.getRide() == null || planItem.getRide().getTrackingArea() == null || planItem.getRide().getTrackingArea().isBlank()) {
            throw new ApiException("A verified GPS tracking area is required for this ride; use manual tracking instead");
        }

        String areaJson = planItem.getRide().getTrackingArea();

        if (areaJson == null || areaJson.isBlank()) {
            throw new ApiException("The ride has no verified tracking area; switch to manual tracking");
        }

        JsonNode area;

        try {
            area = participantJsonMapper.readTree(areaJson);
        } catch (JacksonException exception) {
            throw new ApiException("The ride tracking area must contain valid JSON");
        }

        if (area == null || !area.isObject() || !"CIRCLE".equals(area.path("type").asString()) || !area.path("latitude").isNumber() || !area.path("longitude").isNumber() || !area.path("radiusMeters").isNumber() || !area.path("verifiedAt").isString()) {
            throw new ApiException("The ride needs a verified circular GPS area with latitude, longitude, radiusMeters and verifiedAt");
        }

        double latitude = area.path("latitude").asDouble();
        double longitude = area.path("longitude").asDouble();
        double radius = area.path("radiusMeters").asDouble();

        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90 || !Double.isFinite(longitude) || longitude < -180 || longitude > 180 || !Double.isFinite(radius) || radius <= 0) {
            throw new ApiException("The ride tracking area has invalid coordinates or radius");
        }

        Instant verifiedAt;

        try {
            verifiedAt = java.time.OffsetDateTime.parse(area.path("verifiedAt").asString()).toInstant();
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ApiException("Tracking area verification requires an ISO timestamp with an offset");
        }

        if (verifiedAt.isAfter(Instant.now())) {
            throw new ApiException("Tracking area verification cannot be in the future");
        }

        if (session != null && ("ACTIVE".equals(session.getStatus()) || session.getEnteredAreaAt() != null || session.getQueueStartedAt() != null)) {
            throw new ApiException("The activity has already started; its tracking mode cannot be reset");
        }

        if (session != null && "GPS".equals(session.getTrackingMode())) {
            return convertActivitySessionToDtoOut(session);
        }

        if (session == null) {
            session = new ActivitySession();
            session.setVisitMember(member);
            session.setPlanItem(planItem);
            session.setRide(planItem.getRide());
            session.setPlace(planItem.getPlace());
            session.setExpectedWaitMinutes(planItem.getWaitingMinutes());
            session.setExpectedRideMinutes("RIDE".equals(planItem.getActivityKind()) ? planItem.getRide().getRideDurationMinutes() : null);
            session.setBufferMinutes("RIDE".equals(planItem.getActivityKind()) ? planItem.getRide().getEntryExitBufferMinutes() : 0);
        }

        session.setTrackingMode("GPS");
        session.setStatus("WAITING");
        activitySessionRepository.save(session);

        return convertActivitySessionToDtoOut(session);
    }

    private HashSet<Integer> parseActivityParticipantIds(String participantVisitMemberIds) {
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

    @Transactional
    public ActivitySessionDtoOut updateActivityLocation(Integer accountId, Integer visitId, UpdateActivityLocationDtoIn dto) {
        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        if (dto == null || dto.getLatitude() == null || dto.getLongitude() == null || dto.getAccuracyMeters() == null || dto.getCapturedAt() == null) {
            throw new ApiException("Latitude, longitude, accuracy and capture time are required");
        }

        if (!Double.isFinite(dto.getLatitude()) || dto.getLatitude() < -90 || dto.getLatitude() > 90) {
            throw new ApiException("Latitude must be between -90 and 90");
        }

        if (!Double.isFinite(dto.getLongitude()) || dto.getLongitude() < -180 || dto.getLongitude() > 180) {
            throw new ApiException("Longitude must be between -180 and 180");
        }

        if (!Double.isFinite(dto.getAccuracyMeters()) || dto.getAccuracyMeters() < 0) {
            throw new ApiException("Location accuracy must be a finite number of zero or greater");
        }

        if (!Double.isFinite(maxLocationAccuracyMeters) || maxLocationAccuracyMeters <= 0 || maxLocationAgeSeconds <= 0 || locationFutureToleranceSeconds < 0) {
            throw new ApiException("Location tracking settings are invalid");
        }

        if (dto.getAccuracyMeters() > maxLocationAccuracyMeters) {
            throw new ApiException("Location accuracy is too low; send a more accurate reading");
        }

        Instant now = Instant.now();
        Instant capturedAt = dto.getCapturedAt().toInstant();

        if (capturedAt.isBefore(now.minusSeconds(maxLocationAgeSeconds))) {
            throw new ApiException("Location reading is too old");
        }

        if (capturedAt.isAfter(now.plusSeconds(locationFutureToleranceSeconds))) {
            throw new ApiException("Location capture time is in the future");
        }

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        entityManager.refresh(visit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!"IN_PROGRESS".equals(visit.getStatus())) {
            throw new ApiException("Location updates require a visit in progress");
        }

        VisitMember member = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId);

        if (member == null) {
            throw new ApiException("You are not a member of this visit");
        }

        if (!"GOING".equals(member.getRsvpStatus())) {
            throw new ApiException("Only GOING members can update their location");
        }

        PrivacyConsent consent = privacyConsentRepository.findTopByVisitorProfileIdAndConsentKindOrderByCreatedAtDescIdDesc(member.getVisitorProfile().getId(), "LOCATION_SHARING");

        if (consent == null || !Boolean.TRUE.equals(consent.getGranted()) || consent.getRevokedAt() != null) {
            throw new ApiException("Active location sharing consent is required");
        }

        ActivitySessionDtoOut currentActivity = getMyCurrentActivity(accountId, visitId);

        if (currentActivity == null) {
            throw new ApiException("No current activity session found");
        }

        ActivitySession reference = activitySessionRepository.findActivitySessionById(currentActivity.getId());

        if (reference == null) {
            throw new ApiException("Activity session not found");
        }

        VisitPlan lockedPlan = visitPlanRepository.findVisitPlanByIdForUpdate(reference.getPlanItem().getVisitPlan().getId());

        if (lockedPlan == null) {
            throw new ApiException("The execution plan is unavailable");
        }

        entityManager.refresh(lockedPlan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        VisitMember lockedMember = activitySessionRepository.findVisitMemberByIdForActivityUpdate(member.getId());

        if (lockedMember == null) {
            throw new ApiException("Visit member not found");
        }

        ActivitySession session = activitySessionRepository.findActivitySessionByIdForUpdate(currentActivity.getId());

        if (session == null) {
            throw new ApiException("Activity session not found");
        }

        entityManager.refresh(session, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!session.getVisitMember().getId().equals(member.getId())) {
            throw new ApiException("You can only update your own activity location");
        }

        if (!"WAITING".equals(session.getStatus()) && !"ACTIVE".equals(session.getStatus())) {
            throw new ApiException("Location can only be updated for a waiting or active session");
        }

        PlanItem planItem = session.getPlanItem();

        if (!planItem.getVisitPlan().getVisit().getId().equals(visitId)) {
            throw new ApiException("Activity session does not belong to this visit");
        }

        if (!Boolean.TRUE.equals(planItem.getVisitPlan().getIsActive())) {
            throw new ApiException("Activity session does not belong to an active plan");
        }

        LocalDateTime locationTime = LocalDateTime.ofInstant(capturedAt, ZoneId.of("Asia/Riyadh"));

        if (session.getLastLocationAt() != null && !locationTime.isAfter(session.getLastLocationAt())) {
            throw new ApiException("Location reading must be newer than the last accepted reading");
        }

        if ("GPS".equals(session.getTrackingMode()) && session.getEnteredAreaAt() == null) {
            String areaJson = planItem.getRide() == null ? null : planItem.getRide().getTrackingArea();

            if (areaJson == null || areaJson.isBlank()) {
                throw new ApiException("The ride has no verified tracking area; switch to manual tracking");
            }

            JsonNode area;

            try {
                area = participantJsonMapper.readTree(areaJson);
            } catch (JacksonException exception) {
                throw new ApiException("The ride tracking area must contain valid JSON");
            }

            if (area == null || !area.isObject() || !"CIRCLE".equals(area.path("type").asString()) || !area.path("latitude").isNumber() || !area.path("longitude").isNumber() || !area.path("radiusMeters").isNumber() || !area.path("verifiedAt").isString()) {
                throw new ApiException("The ride needs a verified circular GPS area with latitude, longitude, radiusMeters and verifiedAt");
            }

            double latitude = area.path("latitude").asDouble();
            double longitude = area.path("longitude").asDouble();
            double radius = area.path("radiusMeters").asDouble();

            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90 || !Double.isFinite(longitude) || longitude < -180 || longitude > 180 || !Double.isFinite(radius) || radius <= 0) {
                throw new ApiException("The ride tracking area has invalid coordinates or radius");
            }

            Instant verifiedAt;

            try {
                verifiedAt = java.time.OffsetDateTime.parse(area.path("verifiedAt").asString()).toInstant();
            } catch (java.time.format.DateTimeParseException exception) {
                throw new ApiException("Tracking area verification requires an ISO timestamp with an offset");
            }

            if (verifiedAt.isAfter(now)) {
                throw new ApiException("Tracking area verification cannot be in the future");
            }

            double latitudeDelta = Math.toRadians(dto.getLatitude() - latitude);
            double longitudeDelta = Math.toRadians(dto.getLongitude() - longitude);
            double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2) + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(dto.getLatitude())) * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
            double distanceMeters = 6371000.0 * 2 * Math.asin(Math.sqrt(Math.max(0.0, Math.min(1.0, haversine))));
            boolean inside = distanceMeters + dto.getAccuracyMeters() <= radius;
            boolean interrupted = session.getLastLocationAt() == null || java.time.Duration.between(session.getLastLocationAt(), locationTime).getSeconds() > 60 || !areaJson.equals(session.getAreaCandidateDefinition());

            if (!inside || interrupted) {
                session.setAreaCandidateSince(null);
                session.setAreaCandidateReadings(0);
                session.setAreaCandidateDefinition(null);
            }

            if (inside) {
                if (session.getAreaCandidateSince() == null) {
                    session.setAreaCandidateSince(locationTime);
                    session.setAreaCandidateDefinition(areaJson);
                }

                int readings = session.getAreaCandidateReadings() == null ? 0 : session.getAreaCandidateReadings();
                session.setAreaCandidateReadings(Math.min(3, readings + 1));

                if (session.getAreaCandidateReadings() >= 3 && !locationTime.isBefore(session.getAreaCandidateSince().plusSeconds(30))) {
                    session.setEnteredAreaAt(locationTime);
                    session.setQueueStartedAt(locationTime);
                    session.setStatus("ACTIVE");
                    session.setAreaCandidateSince(null);
                    session.setAreaCandidateReadings(0);
                    session.setAreaCandidateDefinition(null);
                }
            }
        }

        session.setLastLatitude(dto.getLatitude());
        session.setLastLongitude(dto.getLongitude());
        session.setLastAccuracyMeters(dto.getAccuracyMeters());
        session.setLastLocationAt(locationTime);
        activitySessionRepository.save(session);

        return convertActivitySessionToDtoOut(session);
    }
}