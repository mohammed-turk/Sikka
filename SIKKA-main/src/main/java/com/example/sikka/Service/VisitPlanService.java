package com.example.sikka.Service;

import com.example.sikka.DtoIn.GeneratePlanDtoIn;
import com.example.sikka.Model.RidePreference;
import com.example.sikka.Model.Notification;
import com.example.sikka.Repository.NotificationRepository;
import com.example.sikka.DtoIn.SuggestReplanDtoIn;
import com.example.sikka.DtoIn.PlanApprovalDtoIn;
import com.example.sikka.DtoIn.UpdateVisitArrivalDtoIn;
import com.example.sikka.Repository.PlanApprovalRepository;
import com.example.sikka.Repository.RidePreferenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoOut.*;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.ActivitySession;
import com.example.sikka.Model.PlanApproval;
import com.example.sikka.Model.PlanHistory;
import com.example.sikka.Model.PlanItem;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitMealBreak;
import com.example.sikka.Repository.VisitMealBreakRepository;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitPlan;
import com.example.sikka.Model.VisitRecord;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.ActivitySessionRepository;
import com.example.sikka.Repository.PlanHistoryRepository;
import com.example.sikka.Repository.PlanItemRepository;
import com.example.sikka.Repository.VisitMemberRepository;
import com.example.sikka.Repository.VisitPlanRepository;
import com.example.sikka.Repository.VisitRecordRepository;
import com.example.sikka.Repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.example.sikka.Model.Ride;
import com.example.sikka.Model.Place;
import com.example.sikka.Repository.RideRepository;
import com.example.sikka.Repository.PlaceRepository;

import java.util.Set;
import java.time.LocalDate;
import java.time.Period;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class VisitPlanService {

    private final VisitPlanRepository visitPlanRepository;
    private final ApplicationEventPublisher calendarEventPublisher;
    private final ServerItineraryService serverItineraryService;
    private final VisitMemberService visitMemberService;
    private final AccountRepository accountRepository;
    private final VisitRepository visitRepository;
    private final VisitMemberRepository visitMemberRepository;
    private final PlanItemRepository planItemRepository;
    private final ActivitySessionRepository activitySessionRepository;
    private final VisitRecordRepository visitRecordRepository;
    private final PlanHistoryRepository planHistoryRepository;
    private final JsonMapper participantJsonMapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final AiPlanService aiPlanService;
    private final PrayerTimeService prayerTimeService;
    private final ArrivalPrayerScheduleService arrivalPrayerScheduleService;
    private final RideRepository rideRepository;
    private final PlaceRepository placeRepository;
    private final PaymentOrderService paymentOrderService;
    private final RideService rideService;
    private final PlaceService placeService;
    private final VisitMealBreakService visitMealBreakService;
    private final VisitMealBreakRepository visitMealBreakRepository;
    private final RidePreferenceRepository ridePreferenceRepository;
    private final PlatformTransactionManager transactionManager;
    private final PlanApprovalRepository planApprovalRepository;
    private final NotificationEventService notificationEventService;
    private final NotificationRepository notificationRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Value("${sikka.planning.together-mode-value:TOGETHER}")
    private String togetherModeValue;

    @Value("${sikka.planning.split-allowed-mode-value:SPLIT}")
    private String splitAllowedModeValue;

    @Value("${sikka.park.latitude:${SIKKA_PARK_LATITUDE:#{null}}}")
    private Double parkLatitude;

    @Value("${sikka.park.longitude:${SIKKA_PARK_LONGITUDE:#{null}}}")
    private Double parkLongitude;

    @Value("${sikka.planning.max-live-wait-age-seconds:600}")
    private long maxLiveWaitAgeSeconds;

    @Transactional(readOnly = true)
    public List<VisitPlanDtoOut> getVisitPlans(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can view the visit plan list");
        }

        List<VisitPlan> plans = visitPlanRepository.findAllByVisit_Id(visitId);
        plans.sort(Comparator.comparing(VisitPlan::getId).reversed());

        List<VisitPlanDtoOut> results = new ArrayList<>();

        for (VisitPlan plan : plans) {

            if ("GROUP".equals(plan.getPlanKind())) {
                results.add(convertVisitPlanToDtoOut(plan));
                continue;
            }

            if (!"INDIVIDUAL".equals(plan.getPlanKind()) || isFamilyVisit(visit)) {
                continue;
            }

            VisitMember owner = plan.getOwnerVisitMember();

            if (owner != null && isOwnMember(accountId, owner)) {

                validatePlanReadAccess(accountId, plan);

                VisitPlan sourcePlan = getIndividualActivitySource(plan);
                VisitPlanDtoOut result = convertVisitPlanToDtoOut(plan);

                result.setTimingMode(sourcePlan.getTimingMode());
                result.setIsActive(sourcePlan.getIsActive());

                results.add(result);
            }
        }

        return results;
    }

    @Transactional(readOnly = true)
    public VisitPlanDetailsDtoOut getVisitPlanDetails(Integer accountId, Integer visitPlanId) {

        validateActiveAccount(accountId);

        VisitPlan plan = getRequiredVisitPlan(visitPlanId);

        validatePlanReadAccess(accountId, plan);

        VisitPlan sourcePlan = "INDIVIDUAL".equals(plan.getPlanKind()) ? getIndividualActivitySource(plan) : plan;

        if (!sourcePlan.getId().equals(plan.getId())) {
            validatePlanReadAccess(accountId, sourcePlan);
        }

        List<PlanItem> items = getSortedPlanItems(sourcePlan);
        List<PlanItemDtoOut> activityResults = new ArrayList<>();

        Integer ownerVisitMemberId = plan.getOwnerVisitMember() == null ? null : plan.getOwnerVisitMember().getId();

        Map<Integer, String> personalPriorities = new HashMap<>();

        if ("INDIVIDUAL".equals(plan.getPlanKind()) && plan.getOwnerVisitMember() != null && plan.getOwnerVisitMember().getSelectedRides() != null && !plan.getOwnerVisitMember().getSelectedRides().isBlank()) {
            JsonNode selectedRides = readGenerationJson(plan.getOwnerVisitMember().getSelectedRides());

            if (!selectedRides.isArray()) {
                throw new ApiException("Member selected rides must be an array");
            }

            for (JsonNode selection : selectedRides) {
                Integer rideId = requirePositiveInteger(selection, "rideId");
                String priority = requireText(selection, "priority");

                if (!Set.of("MUST_RIDE", "PREFERRED", "OPTIONAL").contains(priority)) {
                    throw new ApiException("Invalid member ride priority");
                }

                personalPriorities.put(rideId, priority);
            }
        }

        for (PlanItem item : items) {

            HashSet<Integer> participantIds = parseActivityParticipantIds(item.getParticipantVisitMemberIds());

            if ("INDIVIDUAL".equals(plan.getPlanKind()) && !participantIds.contains(ownerVisitMemberId)) {
                continue;
            }

            PlanItemDtoOut activityResult = convertPlanItemToDtoOut(item);

            if ("INDIVIDUAL".equals(plan.getPlanKind()) && "RIDE".equals(activityResult.getActivityKind())) {
                activityResult.setPriority(personalPriorities.getOrDefault(activityResult.getRideId(), "OPTIONAL"));
            }

            activityResults.add(activityResult);
        }

        VisitPlanDtoOut planResult = convertVisitPlanToDtoOut(plan);
        planResult.setTimingMode(sourcePlan.getTimingMode());
        planResult.setIsActive(sourcePlan.getIsActive());

        String approvalStatus = sourcePlan.getPlanApproval() == null ? null : sourcePlan.getPlanApproval().getStatus();

        List<PlanItemDtoOut> proposedResults = new ArrayList<>();
        ReplanProposalDtoOut pendingProposal = null;

        if (sourcePlan.getProposedItems() != null && canViewPendingProposal(accountId, sourcePlan)) {

            List<Integer> affectedMemberIds = getProposalAffectedMemberIds(sourcePlan);
            List<PlanItemDtoOut> proposedItems = parseProposedPlanItems(sourcePlan);

            for (PlanItemDtoOut proposedItem : proposedItems) {

                HashSet<Integer> participantIds = parseActivityParticipantIds(proposedItem.getParticipantVisitMemberIds());

                if ("INDIVIDUAL".equals(plan.getPlanKind()) && !participantIds.contains(ownerVisitMemberId)) {
                    continue;
                }

                if ("INDIVIDUAL".equals(plan.getPlanKind()) && "RIDE".equals(proposedItem.getActivityKind())) {
                    proposedItem.setPriority(personalPriorities.getOrDefault(proposedItem.getRideId(), "OPTIONAL"));
                }

                proposedResults.add(proposedItem);
            }

            PlanApproval approval = sourcePlan.getPlanApproval();

            if (approval == null || !"PENDING".equals(approval.getStatus())) {
                throw new ApiException("The pending proposal must have a pending approval");
            }

            if (!sourcePlan.getPendingScope().equals(approval.getApprovalScope())) {
                throw new ApiException("The proposal and approval scopes do not match");
            }

            Integer requestedByAccountId = approval.getRequestedByAccount() == null ? null : approval.getRequestedByAccount().getId();
            Integer requiredApproverAccountId = approval.getRequiredApproverAccount() == null ? null : approval.getRequiredApproverAccount().getId();

            pendingProposal = new ReplanProposalDtoOut(sourcePlan.getId(), approval.getId(), sourcePlan.getPendingScope(), approval.getStatus(), requestedByAccountId, requiredApproverAccountId, affectedMemberIds, readGenerationJson(sourcePlan.getProposalBaseSnapshot()).path("proposalKey").asString());
        }

        if (!canViewPendingProposal(accountId, sourcePlan)) {
            planResult.setPendingScope(null);
            planResult.setRequestedByVisitMemberId(null);
            planResult.setAffectedMemberIds(null);
        }

        List<ExcludedRideDtoOut> exclusions;
        try {
            exclusions = sourcePlan.getExcludedRides() == null ? List.of() : Arrays.asList(participantJsonMapper.readValue(sourcePlan.getExcludedRides(), ExcludedRideDtoOut[].class));
        } catch (JacksonException exception) {
            throw new ApiException("Saved excluded rides are invalid");
        }
        if ("INDIVIDUAL".equals(plan.getPlanKind())) exclusions = exclusions.stream().filter(ride -> ownerVisitMemberId.equals(ride.getVisitMemberId())).toList();
        return new VisitPlanDetailsDtoOut(planResult, approvalStatus, activityResults, proposedResults, pendingProposal, exclusions);
    }

    @Transactional
    public void createIndividualVisitPlan(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can start planning");
        }

        if (isFamilyVisit(visit)) {
            throw new ApiException("Family visits use one unified group plan");
        }

        throw new ApiException("Use the generation flow; individual plans must be derived from the generated group plan");
    }

    @Transactional
    public void createGroupVisitPlan(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can start planning");
        }

        throw new ApiException("Use the generation flow; standalone empty group plans are not supported");
    }

    @Transactional
    public void deleteVisitPlan(Integer accountId, Integer visitPlanId) {

        validateActiveAccount(accountId);

        VisitPlan existingPlan = getRequiredVisitPlan(visitPlanId);

        Visit visit = visitRepository.findVisitByIdForUpdate(existingPlan.getVisit().getId());

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        VisitPlan plan = visitPlanRepository.findVisitPlanByIdForUpdate(visitPlanId);

        if (plan == null) {
            throw new ApiException("Visit plan not found");
        }

        if (!plan.getVisit().getId().equals(visit.getId())) {
            throw new ApiException("The plan does not belong to this visit");
        }

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can delete plans");
        }

        if (!"PLANNING".equals(visit.getStatus())) {
            throw new ApiException("Plans cannot be deleted in the current visit status");
        }

        if (Boolean.TRUE.equals(plan.getIsActive())) {
            throw new ApiException("An active plan cannot be deleted");
        }

        if (plan.getGeneratedByHistory() != null) {
            throw new ApiException("Generated plans must be preserved with their generation history");
        }

        if (plan.getPlanApproval() != null) {
            throw new ApiException("A plan with an approval record cannot be deleted");
        }

        if (plan.getProposedItems() != null || plan.getPendingScope() != null) {
            throw new ApiException("A plan with a pending proposal cannot be deleted");
        }

        if (!planItemRepository.findAllByVisitPlan_Id(plan.getId()).isEmpty()) {
            throw new ApiException("A plan containing activities cannot be deleted through this endpoint");
        }

        if ("GROUP".equals(plan.getPlanKind()) && visitPlanRepository.existsByVisit_IdAndPlanKind(visit.getId(), "INDIVIDUAL")) {
            throw new ApiException("A group plan cannot be deleted while individual plans depend on it");
        }

        visitPlanRepository.delete(plan);
    }

    @Transactional(readOnly = true)
    public String getPlanStatus(Integer visitId) {

        Visit visit = getRequiredVisit(visitId);

        if ("CANCELLED".equals(visit.getStatus())) {
            return "NOT_STARTED";
        }

        List<VisitPlan> plans = visitPlanRepository.findAllByVisit_IdAndPlanKind(visitId, "GROUP");
        plans.sort(Comparator.comparing(VisitPlan::getId).reversed());

        VisitPlan activePlan = findActiveGroupPlan(visitId);

        if (activePlan != null) {
            getSortedPlanItems(activePlan);
            return "APPROVED";
        }

        for (VisitPlan plan : plans) {

            if (plan.getGeneratedByHistory() != null && !"SUCCEEDED".equals(plan.getGeneratedByHistory().getStatus())) {
                continue;
            }

            if (planItemRepository.findAllByVisitPlan_Id(plan.getId()).isEmpty()) {
                continue;
            }

            getSortedPlanItems(plan);

            return "GENERATED";
        }

        return "NOT_STARTED";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void preparePlanning(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        if (visitId == null || visitId <= 0) {
            throw new ApiException("A positive visit ID is required");
        }

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can start planning");
        }

        if (!"PLANNING".equals(visit.getStatus())) {
            throw new ApiException("Planning preparation requires a visit in PLANNING status");
        }

        List<PlanHistory> histories = planHistoryRepository.findAllByAccount_IdOrderByStartedAtDescIdDesc(accountId);

        PlanHistory processingHistory = null;

        for (PlanHistory history : histories) {

            if (!history.getVisit().getId().equals(visitId) || !"PROCESSING".equals(history.getStatus())) {
                continue;
            }

            if (processingHistory != null) {
                throw new ApiException("Multiple processing generation requests were found for this visit");
            }

            processingHistory = history;
        }

        if (processingHistory == null) {
            throw new ApiException("Start generation through the entitlement reservation flow first");
        }

        if (!"FREE".equals(processingHistory.getBillingMode()) && !"PAID".equals(processingHistory.getBillingMode())) {
            throw new ApiException("The generation request has an invalid billing mode");
        }

        List<VisitMember> goingMembers = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING");

        if (goingMembers.isEmpty()) {
            throw new ApiException("The visit must have at least one GOING member");
        }

        for (VisitMember member : goingMembers) {
            validateMemberIdentity(member);
        }

        if (!Boolean.TRUE.equals(visit.getMembersLocked())) {
            visit.setMembersLocked(true);
            visitRepository.save(visit);
        }
    }

    @Transactional(readOnly = true)
    public VisitPlanDetailsDtoOut getMyIndividualVisitPlan(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);
        VisitMember member = getRequiredAccountMember(accountId, visitId);

        if (isFamilyVisit(visit)) {
            throw new ApiException("Family visits use one unified group plan");
        }

        List<VisitPlan> plans = visitPlanRepository.findAllByVisit_Id(visitId);
        plans.sort(Comparator.comparing(VisitPlan::getId).reversed());

        VisitPlan activeGroupPlan = findActiveGroupPlan(visitId);

        for (VisitPlan plan : plans) {

            if (!"INDIVIDUAL".equals(plan.getPlanKind()) || plan.getOwnerVisitMember() == null || !plan.getOwnerVisitMember().getId().equals(member.getId())) {
                continue;
            }

            if (activeGroupPlan != null && !Objects.equals(getGenerationHistoryId(plan), getGenerationHistoryId(activeGroupPlan))) {
                continue;
            }

            return getVisitPlanDetails(accountId, plan.getId());
        }

        throw new ApiException("Your individual plan has not been created for the current generation");
    }

    @Transactional(readOnly = true)
    public VisitPlanDetailsDtoOut getApprovedGroupVisitPlan(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);

        if (!visit.getOrganizer().getId().equals(accountId)) {
            getRequiredAccountMember(accountId, visit.getId());
        }

        VisitPlan activePlan = findActiveGroupPlan(visitId);

        if (activePlan == null) {
            throw new ApiException("No active approved group plan is available");
        }

        return getVisitPlanDetails(accountId, activePlan.getId());
    }

    @Transactional(readOnly = true)
    public VisitPlanDetailsDtoOut getPendingGroupVisitPlan(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can preview the pending group plan");
        }

        if ("IN_PROGRESS".equals(visit.getStatus())) {

            VisitPlan activePlan = findActiveGroupPlan(visitId);

            if (activePlan == null || activePlan.getProposedItems() == null) {
                throw new ApiException("No pending replanning proposal is available");
            }

            return getVisitPlanDetails(accountId, activePlan.getId());
        }

        if (!"PLANNING".equals(visit.getStatus())) {
            throw new ApiException("Initial plan preview requires a visit in PLANNING status");
        }

        List<VisitPlan> plans = visitPlanRepository.findAllByVisit_IdAndPlanKind(visitId, "GROUP");
        plans.sort(Comparator.comparing(VisitPlan::getId).reversed());

        for (VisitPlan plan : plans) {

            if (Boolean.TRUE.equals(plan.getIsActive())) {
                continue;
            }

            if (plan.getGeneratedByHistory() != null && !"SUCCEEDED".equals(plan.getGeneratedByHistory().getStatus())) {
                continue;
            }

            if (plan.getPlanApproval() != null && !"PENDING".equals(plan.getPlanApproval().getStatus())) {
                continue;
            }

            if (planItemRepository.findAllByVisitPlan_Id(plan.getId()).isEmpty()) {
                continue;
            }

            return getVisitPlanDetails(accountId, plan.getId());
        }

        throw new ApiException("No group plan is available for preview");
    }

    @Transactional(readOnly = true)
    public List<PlanItemDtoOut> getVisitMeetingPoints(Integer accountId, Integer visitId) {

        VisitPlanDetailsDtoOut details = getApprovedGroupVisitPlan(accountId, visitId);

        List<PlanItemDtoOut> results = new ArrayList<>();

        for (PlanItemDtoOut item : details.getPlanItems()) {

            if ("MEETING_POINT".equals(item.getActivityKind())) {
                results.add(item);
            }
        }

        return results;
    }

    @Transactional(readOnly = true)
    public HashSet<Integer> getMyPlannedRideIds(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);

        if (!"READY".equals(visit.getStatus()) && !"IN_PROGRESS".equals(visit.getStatus()) && !"COMPLETED".equals(visit.getStatus())) {
            throw new ApiException("Progress is not available in the current visit status");
        }

        VisitMember member = getRequiredAccountMember(accountId, visitId);
        VisitPlan sourcePlan = getActiveMemberActivitySource(member);

        List<PlanItem> items = getSortedPlanItems(sourcePlan);
        HashSet<Integer> results = new HashSet<>();

        for (PlanItem item : items) {

            if (!parseActivityParticipantIds(item.getParticipantVisitMemberIds()).contains(member.getId())) {
                continue;
            }

            if ("RIDE".equals(item.getActivityKind())) {
                results.add(item.getRide().getId());
            }
        }

        return results;
    }

    @Transactional(readOnly = true)
    public NextActivityDtoOut getMyNextActivity(Integer accountId, Integer visitId) {

        validateActiveAccount(accountId);

        Visit visit = getRequiredVisit(visitId);

        if (!"IN_PROGRESS".equals(visit.getStatus())) {
            throw new ApiException("The visit must be in progress to view the next activity");
        }

        VisitMember member = getRequiredAccountMember(accountId, visitId);

        if (!"GOING".equals(member.getRsvpStatus())) {
            throw new ApiException("Only GOING members can view their next activity");
        }

        VisitPlan sourcePlan = getActiveMemberActivitySource(member);

        if (!"SCHEDULED".equals(sourcePlan.getTimingMode())) {
            throw new ApiException("The active plan must be scheduled before execution");
        }

        List<PlanItem> allItems = getSortedPlanItems(sourcePlan);
        List<PlanItem> memberItems = new ArrayList<>();
        HashSet<Integer> memberItemIds = new HashSet<>();

        for (PlanItem item : allItems) {

            if (parseActivityParticipantIds(item.getParticipantVisitMemberIds()).contains(member.getId())) {
                memberItems.add(item);
                memberItemIds.add(item.getId());
            }
        }

        Map<Integer, VisitRecord> recordsByItem = new HashMap<>();

        for (VisitRecord record : visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(member.getId())) {

            if (record.getPlanItem() == null || !memberItemIds.contains(record.getPlanItem().getId())) {
                continue;
            }

            if (!"RIDE".equals(record.getPlanItem().getActivityKind()) || record.getPlanItem().getRide() == null || !record.getPlanItem().getRide().getId().equals(record.getRide().getId())) {
                throw new ApiException("A ride record does not match its activity");
            }

            if (recordsByItem.putIfAbsent(record.getPlanItem().getId(), record) != null) {
                throw new ApiException("Duplicate ride records were found for an activity");
            }
        }

        Map<Integer, ActivitySession> sessionsByItem = new HashMap<>();

        for (ActivitySession session : activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(member.getId())) {

            if (!session.getPlanItem().getVisitPlan().getVisit().getId().equals(visitId)) {
                throw new ApiException("An activity session does not belong to this visit");
            }

            if (!memberItemIds.contains(session.getPlanItem().getId())) {

                if ("WAITING".equals(session.getStatus()) || "ACTIVE".equals(session.getStatus())) {
                    throw new ApiException("A current session must be reconciled with the active plan before continuing");
                }

                continue;
            }

            if (sessionsByItem.putIfAbsent(session.getPlanItem().getId(), session) != null) {
                throw new ApiException("Duplicate activity sessions were found");
            }
        }

        PlanItem trackedItem = null;
        ActivitySession trackedSession = null;

        for (PlanItem item : memberItems) {

            ActivitySession session = sessionsByItem.get(item.getId());

            if (isActivityFinished(item, recordsByItem.get(item.getId()), session)) {
                continue;
            }

            if (session == null || (!"WAITING".equals(session.getStatus()) && !"ACTIVE".equals(session.getStatus()))) {
                continue;
            }

            if (trackedSession != null) {
                throw new ApiException("Multiple current activity sessions were found");
            }

            trackedItem = item;
            trackedSession = session;
        }

        if (trackedItem != null) {
            return new NextActivityDtoOut(visitId, "CURRENT", sourcePlan.getTimingMode(), convertPlanItemToDtoOut(trackedItem), convertActivitySessionToDtoOut(trackedSession));
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));

        for (PlanItem item : memberItems) {

            ActivitySession session = sessionsByItem.get(item.getId());

            if (isActivityFinished(item, recordsByItem.get(item.getId()), session)) {
                continue;
            }

            String activityStatus = item.getStartsAt().isAfter(now) ? "UPCOMING" : "CURRENT";
            ActivitySessionDtoOut sessionResult = session == null ? null : convertActivitySessionToDtoOut(session);

            return new NextActivityDtoOut(visitId, activityStatus, sourcePlan.getTimingMode(), convertPlanItemToDtoOut(item), sessionResult);
        }

        return new NextActivityDtoOut(visitId, "NONE", sourcePlan.getTimingMode(), null, null);
    }

    @Transactional(readOnly = true)
    public MySubgroupDtoOut getMyCurrentSubgroup(Integer accountId, Integer visitId) {

        NextActivityDtoOut nextActivity = getMyNextActivity(accountId, visitId);

        if (!"CURRENT".equals(nextActivity.getActivityStatus())) {
            return new MySubgroupDtoOut(visitId, "NONE", null, null, new ArrayList<>());
        }

        PlanItemDtoOut activity = nextActivity.getActivity();

        HashSet<Integer> participantIds = parseActivityParticipantIds(activity.getParticipantVisitMemberIds());
        List<VisitMember> members = visitMemberRepository.findAllByVisit_Id(visitId);
        List<SubgroupMemberDtoOut> results = new ArrayList<>();

        members.sort(Comparator.comparing(VisitMember::getId));

        for (VisitMember member : members) {

            if (participantIds.contains(member.getId())) {
                results.add(new SubgroupMemberDtoOut(member.getId(), getMemberName(member)));
            }
        }

        return new MySubgroupDtoOut(visitId, "CURRENT", activity.getSubgroupKey(), activity, results);
    }

    @Transactional(readOnly = true)
    public PlanItem getCanonicalExecutionPlanItem(Integer accountId, Integer planItemId) {

        validateActiveAccount(accountId);

        if (planItemId == null || planItemId <= 0) {
            throw new ApiException("A positive plan item ID is required");
        }

        PlanItem item = planItemRepository.findPlanItemById(planItemId);

        if (item == null) {
            throw new ApiException("Plan item not found");
        }

        VisitPlan plan = item.getVisitPlan();

        validatePlanReadAccess(accountId, plan);

        if ("INDIVIDUAL".equals(plan.getPlanKind())) {

            VisitPlan sourcePlan = getIndividualActivitySource(plan);

            if (!sourcePlan.getId().equals(plan.getId())) {
                throw new ApiException("Use the shared activity ID returned in the individual plan details");
            }
        }

        return item;
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

    private VisitPlan getRequiredVisitPlan(Integer visitPlanId) {

        if (visitPlanId == null || visitPlanId <= 0) {
            throw new ApiException("A positive visit plan ID is required");
        }

        VisitPlan plan = visitPlanRepository.findVisitPlanById(visitPlanId);

        if (plan == null) {
            throw new ApiException("Visit plan not found");
        }

        return plan;
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

    private boolean isFamilyVisit(Visit visit) {
        return "FAMILY".equals(visit.getTripType());
    }

    private void validateMemberIdentity(VisitMember member) {

        boolean hasProfile = member.getVisitorProfile() != null;
        boolean hasFamilyMember = member.getFamilyMember() != null;

        if (hasProfile && hasFamilyMember || !hasProfile && !hasFamilyMember && member.getSnapshotAt() == null) {
            throw new ApiException("A visit member must reference one person or a historical snapshot");
        }
    }

    private String getMemberName(VisitMember member) {

        validateMemberIdentity(member);

        return member.getName();
    }

    private void validatePlanReadAccess(Integer accountId, VisitPlan plan) {

        Visit visit = plan.getVisit();

        if ("INDIVIDUAL".equals(plan.getPlanKind())) {

            if (isFamilyVisit(visit)) {
                throw new ApiException("Family visits use one unified group plan");
            }

            VisitMember owner = plan.getOwnerVisitMember();

            if (owner == null || !owner.getVisit().getId().equals(visit.getId())) {
                throw new ApiException("Individual plan owner not found in this visit");
            }

            validateMemberIdentity(owner);

            if (!isOwnMember(accountId, owner)) {
                throw new ApiException("Only the plan owner can view this individual plan");
            }

            return;
        }

        if (!"GROUP".equals(plan.getPlanKind())) {
            throw new ApiException("Invalid plan kind");
        }

        if (visit.getOrganizer().getId().equals(accountId)) {
            return;
        }

        getRequiredAccountMember(accountId, visit.getId());

        if (!Boolean.TRUE.equals(plan.getIsActive()) && (plan.getPlanApproval() == null || !"APPROVED".equals(plan.getPlanApproval().getStatus()))) {
            throw new ApiException("Only the visit organizer can view an unapproved group plan");
        }
    }

    private Integer getGenerationHistoryId(VisitPlan plan) {
        return plan.getGeneratedByHistory() == null ? null : plan.getGeneratedByHistory().getId();
    }

    private VisitPlan findActiveGroupPlan(Integer visitId) {

        VisitPlan result = null;

        for (VisitPlan plan : visitPlanRepository.findAllByVisit_IdAndPlanKind(visitId, "GROUP")) {

            if (!Boolean.TRUE.equals(plan.getIsActive())) {
                continue;
            }

            if (result != null) {
                throw new ApiException("Multiple active group plans were found");
            }

            result = plan;
        }

        return result;
    }

    private VisitPlan getIndividualActivitySource(VisitPlan individualPlan) {

        VisitPlan result = null;

        for (VisitPlan groupPlan : visitPlanRepository.findAllByVisit_IdAndPlanKind(individualPlan.getVisit().getId(), "GROUP")) {

            if (!Objects.equals(getGenerationHistoryId(groupPlan), getGenerationHistoryId(individualPlan))) {
                continue;
            }

            if (result != null) {
                throw new ApiException("Multiple group sources were found for this individual plan");
            }

            result = groupPlan;
        }

        if (result != null) {
            return result;
        }

        if (individualPlan.getGeneratedByHistory() != null) {
            throw new ApiException("The generated individual plan is missing its group source");
        }

        return individualPlan;
    }

    private VisitPlan getActiveMemberActivitySource(VisitMember member) {

        VisitPlan groupPlan = findActiveGroupPlan(member.getVisit().getId());

        if (groupPlan != null) {
            return groupPlan;
        }

        if (isFamilyVisit(member.getVisit())) {
            throw new ApiException("No active unified family plan is available");
        }

        VisitPlan result = null;

        for (VisitPlan plan : visitPlanRepository.findAllByVisit_Id(member.getVisit().getId())) {

            if (!"INDIVIDUAL".equals(plan.getPlanKind()) || !Boolean.TRUE.equals(plan.getIsActive()) || plan.getOwnerVisitMember() == null || !plan.getOwnerVisitMember().getId().equals(member.getId())) {
                continue;
            }

            if (result != null) {
                throw new ApiException("Multiple active individual plans were found");
            }

            result = plan;
        }

        if (result == null) {
            throw new ApiException("No active plan is available for this member");
        }

        VisitPlan sourcePlan = getIndividualActivitySource(result);

        if (!Boolean.TRUE.equals(sourcePlan.getIsActive())) {
            throw new ApiException("The individual plan source is not active");
        }

        return sourcePlan;
    }

    private List<PlanItem> getSortedPlanItems(VisitPlan plan) {

        List<PlanItem> items = planItemRepository.findAllByVisitPlan_Id(plan.getId());

        if (items.isEmpty()) {
            throw new ApiException("The plan does not contain activities");
        }

        HashSet<Integer> visitMemberIds = new HashSet<>();

        for (VisitMember member : visitMemberRepository.findAllByVisit_Id(plan.getVisit().getId())) {
            visitMemberIds.add(member.getId());
        }

        for (PlanItem item : items) {

            validateActivityTiming(plan.getTimingMode(), item.getRelativeStartMinutes(), item.getRelativeEndMinutes(), item.getStartsAt(), item.getEndsAt());

            HashSet<Integer> participantIds = parseActivityParticipantIds(item.getParticipantVisitMemberIds());

            if (!visitMemberIds.containsAll(participantIds)) {
                throw new ApiException("An activity contains participants from outside this visit");
            }

            if ("RIDE".equals(item.getActivityKind())) {

                if (item.getRide() == null) {
                    throw new ApiException("A ride activity must reference a ride");
                }

            } else if ("PLACE".equals(item.getActivityKind()) || "MEETING_POINT".equals(item.getActivityKind())) {

                if (item.getPlace() == null) {
                    throw new ApiException("A place or meeting activity must reference a place");
                }

            } else if (!"BREAK".equals(item.getActivityKind())) {
                throw new ApiException("Invalid activity kind");
            }

            if (item.getSortOrder() == null || item.getSortOrder() < 0) {
                throw new ApiException("Activities must have a non-negative sort order");
            }
        }

        if ("RELATIVE".equals(plan.getTimingMode())) {
            items.sort(Comparator.comparing(PlanItem::getRelativeStartMinutes).thenComparing(PlanItem::getSortOrder).thenComparing(PlanItem::getId));
        } else {
            items.sort(Comparator.comparing(PlanItem::getStartsAt).thenComparing(PlanItem::getSortOrder).thenComparing(PlanItem::getId));
        }

        return items;
    }

    private void validateActivityTiming(String timingMode, Integer relativeStart, Integer relativeEnd, LocalDateTime startsAt, LocalDateTime endsAt) {

        if ("RELATIVE".equals(timingMode)) {

            if (relativeStart == null || relativeEnd == null || relativeStart < 0 || relativeEnd <= relativeStart) {
                throw new ApiException("Relative activities must have valid start and end offsets");
            }

            if (startsAt != null || endsAt != null) {
                throw new ApiException("Relative activities must not contain scheduled times");
            }

            return;
        }

        if (!"SCHEDULED".equals(timingMode)) {
            throw new ApiException("Invalid plan timing mode");
        }

        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new ApiException("Scheduled activities must have valid start and end times");
        }
    }

    private boolean isActivityFinished(PlanItem item, VisitRecord record, ActivitySession session) {

        if ("RIDE".equals(item.getActivityKind())) {
            return record != null && ("COMPLETED".equals(record.getStatus()) || "SKIPPED".equals(record.getStatus()));
        }

        return session != null && ("COMPLETED".equals(session.getStatus()) || "SKIPPED".equals(session.getStatus()));
    }

    private HashSet<Integer> parseActivityParticipantIds(String participantVisitMemberIds) {

        if (participantVisitMemberIds == null || participantVisitMemberIds.isBlank()) {
            throw new ApiException("Activity participant IDs are required");
        }

        JsonNode participantIdsNode;

        try {
            participantIdsNode = participantJsonMapper.readTree(participantVisitMemberIds);
        } catch (JacksonException exception) {
            throw new ApiException("Activity participant IDs contain invalid JSON");
        }

        if (participantIdsNode == null || !participantIdsNode.isArray() || participantIdsNode.isEmpty()) {
            throw new ApiException("Activity participant IDs must be a non-empty JSON array");
        }

        HashSet<Integer> participantIds = new HashSet<>();

        for (JsonNode node : participantIdsNode) {

            if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() <= 0) {
                throw new ApiException("Activity participant IDs must be positive integers");
            }

            if (!participantIds.add(node.intValue())) {
                throw new ApiException("Activity participant IDs must not contain duplicates");
            }
        }

        return participantIds;
    }

    private boolean canViewPendingProposal(Integer accountId, VisitPlan plan) {

        if (plan.getProposedItems() == null) {
            return false;
        }

        if (plan.getVisit().getOrganizer().getId().equals(accountId)) {
            return true;
        }

        VisitMember requester = plan.getRequestedByVisitMember();

        if (requester != null && isOwnMember(accountId, requester)) {
            return true;
        }

        PlanApproval approval = plan.getPlanApproval();

        return approval != null && approval.getRequiredApproverAccount() != null && approval.getRequiredApproverAccount().getId().equals(accountId);
    }

    private List<Integer> getProposalAffectedMemberIds(VisitPlan plan) {

        if (!"INDIVIDUAL".equals(plan.getPendingScope()) && !"GROUP".equals(plan.getPendingScope())) {
            throw new ApiException("The pending proposal has an invalid scope");
        }

        if (isFamilyVisit(plan.getVisit()) && !"GROUP".equals(plan.getPendingScope())) {
            throw new ApiException("Family plan changes must use GROUP scope");
        }

        HashSet<Integer> affectedIds = parseActivityParticipantIds(plan.getAffectedMemberIds());
        HashSet<Integer> visitMemberIds = new HashSet<>();

        for (VisitMember member : visitMemberRepository.findAllByVisit_Id(plan.getVisit().getId())) {
            visitMemberIds.add(member.getId());
        }

        if (!visitMemberIds.containsAll(affectedIds)) {
            throw new ApiException("The proposal contains members from outside this visit");
        }

        VisitMember requester = plan.getRequestedByVisitMember();

        if (requester == null || !requester.getVisit().getId().equals(plan.getVisit().getId())) {
            throw new ApiException("The proposal requester does not belong to this visit");
        }

        if ("INDIVIDUAL".equals(plan.getPendingScope()) && (affectedIds.size() != 1 || !affectedIds.contains(requester.getId()))) {
            throw new ApiException("An individual proposal must affect only its requester");
        }

        List<Integer> results = new ArrayList<>(affectedIds);
        results.sort(Integer::compareTo);

        return results;
    }

    private List<PlanItemDtoOut> parseProposedPlanItems(VisitPlan plan) {

        PlanItemDtoOut[] proposedItems;

        try {
            proposedItems = participantJsonMapper.readValue(plan.getProposedItems(), PlanItemDtoOut[].class);
        } catch (JacksonException exception) {
            throw new ApiException("The proposed activities contain invalid data");
        }

        if (proposedItems == null || proposedItems.length == 0) {
            throw new ApiException("The pending proposal must contain activities");
        }

        HashSet<Integer> visitMemberIds = new HashSet<>();

        for (VisitMember member : visitMemberRepository.findAllByVisit_Id(plan.getVisit().getId())) {
            visitMemberIds.add(member.getId());
        }

        HashSet<Integer> existingItemIds = new HashSet<>();
        List<PlanItemDtoOut> results = new ArrayList<>();

        for (PlanItemDtoOut item : proposedItems) {

            if (item == null) {
                throw new ApiException("A proposed activity cannot be null");
            }

            if (item.getId() != null) {

                if (item.getId() <= 0 || !existingItemIds.add(item.getId())) {
                    throw new ApiException("Proposed activity IDs must be positive and unique");
                }

                PlanItem existingItem = planItemRepository.findPlanItemById(item.getId());

                if (existingItem == null || !existingItem.getVisitPlan().getId().equals(plan.getId())) {
                    throw new ApiException("A proposed activity ID does not belong to this plan");
                }
            }

            validateActivityTiming(plan.getTimingMode(), item.getRelativeStartMinutes(), item.getRelativeEndMinutes(), item.getStartsAt(), item.getEndsAt());

            HashSet<Integer> participantIds = parseActivityParticipantIds(item.getParticipantVisitMemberIds());

            if (!visitMemberIds.containsAll(participantIds)) {
                throw new ApiException("A proposed activity contains members from outside this visit");
            }

            if (item.getSortOrder() == null || item.getSortOrder() < 0) {
                throw new ApiException("Proposed activities must have a non-negative sort order");
            }

            if ("RIDE".equals(item.getActivityKind())) {

                if (item.getRideId() == null || item.getRideId() <= 0) {
                    throw new ApiException("A proposed ride activity must reference a ride");
                }

                item.setIsRateable(false);

            } else if ("PLACE".equals(item.getActivityKind()) || "MEETING_POINT".equals(item.getActivityKind())) {

                if (item.getPlaceId() == null || item.getPlaceId() <= 0) {
                    throw new ApiException("A proposed place activity must reference a place");
                }

            } else if (!"BREAK".equals(item.getActivityKind())) {
                throw new ApiException("Invalid proposed activity kind");
            }

            if ("PRAYER".equals(item.getBreakType())) {
                item.setIsRateable(false);
            }

            results.add(item);
        }

        if ("RELATIVE".equals(plan.getTimingMode())) {
            results.sort(Comparator.comparing(PlanItemDtoOut::getRelativeStartMinutes).thenComparing(PlanItemDtoOut::getSortOrder));
        } else {
            results.sort(Comparator.comparing(PlanItemDtoOut::getStartsAt).thenComparing(PlanItemDtoOut::getSortOrder));
        }

        return results;
    }

    private VisitPlanDtoOut convertVisitPlanToDtoOut(VisitPlan plan) {

        Integer ownerVisitMemberId = plan.getOwnerVisitMember() == null ? null : plan.getOwnerVisitMember().getId();
        String ownerName = plan.getOwnerVisitMember() == null ? null : getMemberName(plan.getOwnerVisitMember());
        Integer historyId = getGenerationHistoryId(plan);
        Integer requestedByVisitMemberId = plan.getRequestedByVisitMember() == null ? null : plan.getRequestedByVisitMember().getId();

        return new VisitPlanDtoOut(plan.getId(), plan.getVisit().getId(), plan.getPlanKind(), plan.getTimingMode(), ownerVisitMemberId, ownerName, plan.getIsActive(), historyId, plan.getPendingScope(), requestedByVisitMemberId, plan.getAffectedMemberIds(), plan.getCreatedAt());
    }

    private PlanItemDtoOut convertPlanItemToDtoOut(PlanItem item) {

        Integer rideId = item.getRide() == null ? null : item.getRide().getId();
        String rideName = item.getRide() == null ? null : item.getRide().getName();
        Integer placeId = item.getPlace() == null ? null : item.getPlace().getId();
        String placeName = item.getPlace() == null ? null : item.getPlace().getName();

        boolean isRateable = !"RIDE".equals(item.getActivityKind()) && !"PRAYER".equals(item.getBreakType()) && Boolean.TRUE.equals(item.getIsRateable());

        return new PlanItemDtoOut(item.getId(), item.getActivityKind(), item.getTitle(), rideId, rideName, placeId, placeName, item.getRelativeStartMinutes(), item.getRelativeEndMinutes(), item.getStartsAt(), item.getEndsAt(), item.getWalkingMinutes(), item.getWaitingMinutes(), item.getActivityMinutes(), item.getBreakType(), isRateable, item.getSortOrder(), item.getPriority(), item.getPinned(), item.getParticipantVisitMemberIds(), item.getSubgroupKey());
    }

    private ActivitySessionDtoOut convertActivitySessionToDtoOut(ActivitySession session) {

        Integer rideId = session.getRide() == null ? null : session.getRide().getId();
        Integer placeId = session.getPlace() == null ? null : session.getPlace().getId();

        PlanItem item = session.getPlanItem();
        boolean isRateable = !"RIDE".equals(item.getActivityKind()) && !"PRAYER".equals(item.getBreakType()) && Boolean.TRUE.equals(item.getIsRateable());

        return new ActivitySessionDtoOut(session.getId(), session.getVisitMember().getId(), item.getId(), rideId, placeId, session.getStatus(), session.getTrackingMode(), session.getEnteredAreaAt(), session.getQueueStartedAt(), session.getCompletedAt(), session.getExpectedWaitMinutes(), session.getExpectedRideMinutes(), session.getBufferMinutes(), session.getLastLocationAt(), session.getDelayReason(), session.getDelayRespondedAt(), isRateable, session.getRating(), session.getReviewNote());
    }

    private Map<String, Object> buildVisitPlanningContext(Visit visit) {

        if (visit == null || visit.getId() == null) {
            throw new ApiException("Visit is required");
        }

        if (visit.getVisitDate() == null) {
            throw new ApiException("Visit date is required");
        }

        if (!"SOLO".equals(visit.getTripType()) && !"FRIENDS".equals(visit.getTripType()) && !"FAMILY".equals(visit.getTripType())) {
            throw new ApiException("A valid trip type is required");
        }

        if (visit.getDurationMinutes() == null || visit.getDurationMinutes() <= 0) {
            throw new ApiException("A positive visit duration is required");
        }

        if ("FRIENDS".equals(visit.getTripType()) && (visit.getTogetherMode() == null || visit.getTogetherMode().isBlank())) {
            throw new ApiException("Together mode is required");
        }

        if (visit.getIncludePrayer() == null) {
            throw new ApiException("Prayer preference is required");
        }

        if (visit.getGoals() == null || visit.getGoals().isBlank()) {
            throw new ApiException("Visit goals are required");
        }

        JsonNode goalsNode;

        try {
            goalsNode = participantJsonMapper.readTree(visit.getGoals());
        } catch (JacksonException exception) {
            throw new ApiException("Visit goals must contain valid JSON");
        }

        if (goalsNode == null || !goalsNode.isArray() || goalsNode.isEmpty()) {
            throw new ApiException("Visit goals must be a non-empty array");
        }

        List<String> goals = new ArrayList<>();
        HashSet<String> uniqueGoals = new HashSet<>();

        for (JsonNode goalNode : goalsNode) {

            if (!goalNode.isString()) {
                throw new ApiException("Each visit goal must be a string");
            }

            String goal = goalNode.asString();

            if (goal.isBlank()) {
                throw new ApiException("Visit goals must not be blank");
            }

            if (!uniqueGoals.add(goal)) {
                throw new ApiException("Visit goals must not be repeated");
            }

            goals.add(goal);
        }

        if (visit.getPrimaryGoal() == null || !uniqueGoals.contains(visit.getPrimaryGoal())) {
            throw new ApiException("Primary goal must be one of the selected visit goals");
        }

        Map<String, Object> context = new LinkedHashMap<>();

        context.put("visitId", visit.getId());
        context.put("visitDate", visit.getVisitDate().toString());
        context.put("tripType", visit.getTripType());
        context.put("durationMinutes", visit.getDurationMinutes());
        context.put("goals", goals);
        context.put("primaryGoal", visit.getPrimaryGoal());
        context.put("togetherMode", "FRIENDS".equals(visit.getTripType()) ? visit.getTogetherMode() : togetherModeValue);
        context.put("includePrayer", visit.getIncludePrayer());
        context.put("timingMode", "RELATIVE");
        context.put("timezone", "Asia/Riyadh");

        if (visit.getSharedSelectedRides() == null || visit.getSharedSelectedRides().isBlank()) {
            context.put("sharedSelectedRideIds", List.of());
        } else {
            context.put("sharedSelectedRideIds", readPositiveIntegerArray(readGenerationJson(visit.getSharedSelectedRides()), "Shared selected rides", true));
        }

        if (!isSplitAllowedForInitialPlan(visit) && !"SOLO".equals(visit.getTripType())) {
            VisitMember organizer = getRequiredAccountMember(visit.getOrganizer().getId(), visit.getId());
            List<Integer> required = new ArrayList<>();
            if (organizer.getSelectedRides() != null) {
                JsonNode selected = readGenerationJson(organizer.getSelectedRides());
                context.put("sharedSelectedRides", selected);
                for (JsonNode ride : selected) {
                    if ("MUST_RIDE".equals(ride.path("priority").asString())) {
                        required.add(requirePositiveInteger(ride, "rideId"));
                    }
                }
            }
            context.put("sharedSelectedRideIds", required);
        }
        return context;
    }

    private List<Map<String, Object>> buildGoingMemberPlanningContext(Visit visit) {

        if (visit == null || visit.getId() == null || visit.getVisitDate() == null) {
            throw new ApiException("Visit and visit date are required");
        }

        List<VisitMember> members = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visit.getId(), "GOING");

        if (members.isEmpty()) {
            throw new ApiException("At least one GOING member is required");
        }

        if (visit.getMaxParticipants() == null || visit.getMaxParticipants() <= 0 || members.size() > visit.getMaxParticipants()) {
            throw new ApiException("Planning participants exceed the configured visit capacity");
        }

        members.sort(Comparator.comparing(VisitMember::getId));

        List<Map<String, Object>> results = new ArrayList<>();
        HashSet<Integer> memberIds = new HashSet<>();

        for (VisitMember member : members) {

            if (!member.getVisit().getId().equals(visit.getId())) {
                throw new ApiException("Planning member does not belong to this visit");
            }

            if (!memberIds.add(member.getId())) {
                throw new ApiException("Duplicate planning member found");
            }

            boolean hasProfile = member.getVisitorProfile() != null;
            boolean hasFamilyMember = member.getFamilyMember() != null;

            if (hasProfile == hasFamilyMember) {
                throw new ApiException("Each member must reference either a visitor profile or a family member");
            }

            LocalDate birthDate;

            if (hasProfile) {

                if (!"ACTIVE".equals(member.getVisitorProfile().getAccount().getStatus())) {
                    throw new ApiException("Planning participants must have active accounts");
                }

                birthDate = member.getBirthDate();

            } else {

                if (!member.getFamilyMember().getOwnerAccount().getId().equals(visit.getOrganizer().getId())) {
                    throw new ApiException("Family member must belong to the visit organizer");
                }


                birthDate = member.getBirthDate();

                if (birthDate == null) {
                    throw new ApiException("Family member birth date is required");
                }
            }

            Integer ageYears = null;

            if (birthDate != null) {

                if (birthDate.isAfter(visit.getVisitDate())) {
                    throw new ApiException("Member birth date cannot be after the visit date");
                }

                ageYears = Period.between(birthDate, visit.getVisitDate()).getYears();
            }

            String answerStatus = member.getAnswerStatus();

            if (!"PENDING".equals(answerStatus) && !"ANSWERED".equals(answerStatus) && !"SKIPPED".equals(answerStatus)) {
                throw new ApiException("Member answer status is invalid");
            }

            List<Integer> selectedRideIds = new ArrayList<>();

            if ("ANSWERED".equals(answerStatus) && member.getSelectedRides() != null && !member.getSelectedRides().isBlank()) {

                JsonNode selectedRidesNode;

                try {
                    selectedRidesNode = participantJsonMapper.readTree(member.getSelectedRides());
                } catch (JacksonException exception) {
                    throw new ApiException("Selected rides must contain valid JSON");
                }

                if (selectedRidesNode == null || !selectedRidesNode.isArray()) {
                    throw new ApiException("Selected rides must be an array");
                }

                HashSet<Integer> uniqueRideIds = new HashSet<>();

                for (JsonNode selectedRide : selectedRidesNode) {
                    JsonNode rideIdNode = selectedRide.path("rideId");
                    String priority = selectedRide.path("priority").asString("");
                    if (!Set.of("MUST_RIDE", "PREFERRED", "OPTIONAL").contains(priority)) {
                        throw new ApiException("Every selected ride requires a valid priority");
                    }
                    if (!rideIdNode.isIntegralNumber() || !rideIdNode.canConvertToInt() || rideIdNode.intValue() <= 0) {
                        throw new ApiException("Selected ride IDs must be positive integers");
                    }

                    if (!uniqueRideIds.add(rideIdNode.intValue())) {
                        throw new ApiException("Selected ride IDs must not be repeated");
                    }

                    if ("MUST_RIDE".equals(priority)) {
                        selectedRideIds.add(rideIdNode.intValue());
                    }
                }
            }

            Map<String, Object> memberContext = new LinkedHashMap<>();

            memberContext.put("visitMemberId", member.getId());
            memberContext.put("ageYears", ageYears);
            memberContext.put("ageKnown", ageYears != null);
            memberContext.put("requiresSupervision", ageYears == null || ageYears < 18);
            memberContext.put("answerStatus", answerStatus);
            selectedRideIds.sort(Integer::compareTo);
            memberContext.put("selectedRideIds", selectedRideIds);
            memberContext.put("selectedRides", member.getSelectedRides() == null ? List.of() : readGenerationJson(member.getSelectedRides()));
            List<String> dislikedFeatures = buildDislikedFeaturesContext(member);

            memberContext.put("dislikedFeatures", dislikedFeatures);
            memberContext.put("dislikedFeaturesKnown", dislikedFeatures != null);
            JsonNode savedPreferences = member.getPreferences() == null ? participantJsonMapper.createObjectNode() : readGenerationJson(member.getPreferences());
            memberContext.put("excludedRideIds", savedPreferences.path("excludedRideIds").isArray() ? savedPreferences.path("excludedRideIds") : List.of());
            memberContext.put("excludedRideTypes", savedPreferences.path("excludedRideTypes").isArray() ? savedPreferences.path("excludedRideTypes") : List.of());

            results.add(memberContext);
        }

        return results;
    }

    private List<String> buildDislikedFeaturesContext(VisitMember member) {

        if (!"ANSWERED".equals(member.getAnswerStatus())) {
            return null;
        }

        if (member.getPreferences() == null || member.getPreferences().isBlank()) {
            return null;
        }

        JsonNode preferencesNode;

        try {
            preferencesNode = participantJsonMapper.readTree(member.getPreferences());
        } catch (JacksonException exception) {
            throw new ApiException("Member preferences must contain valid JSON");
        }

        if (preferencesNode == null || !preferencesNode.isObject()) {
            throw new ApiException("Member preferences must be a JSON object");
        }

        JsonNode dislikedFeaturesNode = preferencesNode.get("dislikedFeatures");

        if (dislikedFeaturesNode == null || dislikedFeaturesNode.isNull()) {
            return null;
        }

        if (!dislikedFeaturesNode.isArray()) {
            throw new ApiException("Disliked features must be an array");
        }

        List<String> dislikedFeatures = new ArrayList<>();
        HashSet<String> uniqueFeatures = new HashSet<>();

        for (JsonNode featureNode : dislikedFeaturesNode) {

            if (!featureNode.isString()) {
                throw new ApiException("Each disliked feature must be a string");
            }

            String feature = featureNode.asString();

            if (feature.isBlank()) {
                throw new ApiException("Disliked features must not be blank");
            }

            if (!uniqueFeatures.add(feature)) {
                throw new ApiException("Disliked features must not be repeated");
            }

            dislikedFeatures.add(feature);
        }

        dislikedFeatures.sort(String::compareTo);

        return dislikedFeatures;
    }

    private String buildInitialPlanInput(Visit visit, List<Map<String, Object>> memberContexts, JsonNode rideSnapshots, JsonNode placeSnapshots, JsonNode walkingContext, JsonNode mealPolicy, Map<Integer, Map<Integer, String>> eligibilityByRideAndMember) {

        if (memberContexts == null || memberContexts.isEmpty()) {
            throw new ApiException("Planning member context is required");
        }

        if (rideSnapshots == null || !rideSnapshots.isArray()) {
            throw new ApiException("Ride snapshots must be an array");
        }

        if (placeSnapshots == null || !placeSnapshots.isArray()) {
            throw new ApiException("Place snapshots must be an array");
        }

        if (walkingContext == null || !walkingContext.isObject()) {
            throw new ApiException("Walking context is required");
        }

        if (mealPolicy == null || !mealPolicy.isObject()) {
            throw new ApiException("Meal break policy is required");
        }

        if (eligibilityByRideAndMember == null) {
            throw new ApiException("Server-side eligibility results are required");
        }

        Map<String, Object> input = new LinkedHashMap<>(buildVisitPlanningContext(visit));

        input.put("members", memberContexts);
        input.put("rides", rideSnapshots);
        input.put("places", placeSnapshots);
        input.put("walking", walkingContext);
        input.put("mealPolicy", mealPolicy);
        input.put("eligibilityByRideAndMember", eligibilityByRideAndMember);
        input.put("arrivalTimeKnown", visit.getPlannedStartTime() != null && ("READY".equals(visit.getStatus()) || "IN_PROGRESS".equals(visit.getStatus())));
        input.put("expectedArrivalTime", visit.getExpectedArrivalTime() == null ? null : visit.getExpectedArrivalTime().toString());
        input.put("prayerTimesVerified", false);
        input.put("prayerReserveMinutes", Boolean.TRUE.equals(visit.getIncludePrayer()) ? 15 : 0);

        List<String> warnings = new ArrayList<>();

        for (Map<String, Object> memberContext : memberContexts) {

            if (!Boolean.TRUE.equals(memberContext.get("ageKnown"))) {
                warnings.add("عمر العضو " + memberContext.get("visitMemberId") + " غير معروف؛ سيُراعى احتياجه للإشراف عند توزيع الأنشطة.");
            }

            if ("SKIPPED".equals(memberContext.get("answerStatus"))) {
                warnings.add("العضو " + memberContext.get("visitMemberId") + " تخطّى الإجابة عن تفضيلاته.");
            }
        }

        if (Boolean.TRUE.equals(visit.getIncludePrayer())) {
            warnings.add("توقيت الصلاة مبدئي، وتُراجع مواقيتها عند تحديد الوصول يوم الزيارة.");
        }

        input.put("inputWarnings", warnings);

        try {
            return participantJsonMapper.writeValueAsString(input);
        } catch (JacksonException exception) {
            throw new ApiException("Unable to prepare the AI planning input");
        }
    }

    private void validateCandidateResources(AiPlanDtoOut plan, java.time.LocalDate visitDate) {

        if (plan == null || !Boolean.TRUE.equals(plan.getFeasible()) || plan.getActivities() == null || plan.getActivities().isEmpty()) {
            throw new ApiException("A feasible plan with activities is required");
        }

        Map<Integer, Ride> rides = new HashMap<>();
        Map<Integer, Place> places = new HashMap<>();

        for (AiPlanItemDtoOut activity : plan.getActivities()) {

            if ("RIDE".equals(activity.getActivityKind())) {

                if (activity.getRideId() == null) {
                    throw new ApiException("Ride ID is required");
                }

                Ride ride = rides.get(activity.getRideId());

                if (ride == null) {
                    ride = rideRepository.findRideById(activity.getRideId());

                    if (ride == null) {
                        throw new ApiException("The generated plan contains a ride that does not exist");
                    }

                    rides.put(ride.getId(), ride);
                }

                if (!PlanningAvailability.canPlanRide(ride, visitDate)) {
                    throw new ApiException("The generated plan contains a ride that is not open");
                }

                if (ride.getRideDurationMinutes() == null || ride.getRideDurationMinutes() <= 0) {
                    throw new ApiException("Ride duration must be configured before planning");
                }

                if (ride.getEntryExitBufferMinutes() == null || ride.getEntryExitBufferMinutes() < 0) {
                    throw new ApiException("Ride entry and exit buffer must be configured before planning");
                }

                long expectedActivityMinutes = (long) ride.getRideDurationMinutes() + ride.getEntryExitBufferMinutes();

                if (expectedActivityMinutes != activity.getActivityMinutes().longValue()) {
                    throw new ApiException("Generated ride duration does not match the configured duration");
                }
            }

            if (activity.getPlaceId() == null) {
                continue;
            }

            Place place = places.get(activity.getPlaceId());

            if (place == null) {
                place = placeRepository.findPlaceById(activity.getPlaceId());

                if (place == null) {
                    throw new ApiException("The generated plan contains a place that does not exist");
                }

                places.put(place.getId(), place);
            }

            if (!PlanningAvailability.canPlanPlace(place)) {
                throw new ApiException("The generated plan contains a place that is not open");
            }

            if ("MEETING_POINT".equals(activity.getActivityKind()) && !"MEETING_POINT".equals(place.getKind())) {
                throw new ApiException("A meeting activity must reference a meeting point");
            }

            if (("MEAL".equals(activity.getBreakType()) || "SNACK".equals(activity.getBreakType())) && !"RESTAURANT".equals(place.getKind())) {
                throw new ApiException("A meal or snack place must be a restaurant");
            }

            if ("PRAYER".equals(activity.getBreakType()) && !"PRAYER_ROOM".equals(place.getKind())) {
                throw new ApiException("A prayer place must be a prayer area");
            }
        }
    }

    private PlanHistory findExistingGenerationRequest(Integer accountId, Integer visitId, String requestKey, String operationType) {

        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 80) {
            throw new ApiException("A request key between 1 and 80 characters is required");
        }

        PlanHistory existingRequest = planHistoryRepository.findPlanHistoryByAccount_IdAndRequestKey(accountId, requestKey);

        if (existingRequest == null) {
            return null;
        }

        if (!existingRequest.getVisit().getId().equals(visitId)) {
            throw new ApiException("This request key is already used for another visit");
        }

        if (!operationType.equals(existingRequest.getOperationType())) {
            throw new ApiException("This request key is already used for another generation operation");
        }

        return existingRequest;
    }

    private GenerateVisitPlansDtoOut convertGenerationRequestToDtoOut(PlanHistory history) {

        if (history == null || history.getId() == null) {
            throw new ApiException("A saved generation request is required");
        }

        GenerateVisitPlansDtoOut result = new GenerateVisitPlansDtoOut();

        result.setVisitId(history.getVisit().getId());
        result.setPlanHistoryId(history.getId());
        result.setRequestStatus(history.getStatus());
        result.setFailureReason(history.getFailureReason());
        result.setIndividualPlansCount(0);

        if (history.getGenerationResultSnapshot() != null) {

            try {
                result.setGeneratedResult(participantJsonMapper.readValue(history.getGenerationResultSnapshot(), AiPlanDtoOut.class));
            } catch (JacksonException exception) {
                throw new ApiException("Saved generation result is invalid");
            }
        }

        if (!"SUCCEEDED".equals(history.getStatus())) {
            return result;
        }

        List<VisitPlan> plans = visitPlanRepository.findAllByVisit_Id(history.getVisit().getId());

        VisitPlan groupPlan = null;
        int individualPlansCount = 0;

        for (VisitPlan plan : plans) {

            if (plan.getGeneratedByHistory() == null || !plan.getGeneratedByHistory().getId().equals(history.getId())) {
                continue;
            }

            if ("GROUP".equals(plan.getPlanKind())) {

                if (groupPlan != null) {
                    throw new ApiException("Multiple group plans were found for the same generation request");
                }

                groupPlan = plan;

            } else if ("INDIVIDUAL".equals(plan.getPlanKind())) {
                individualPlansCount++;
            }
        }

        if (groupPlan == null) {
            throw new ApiException("The successful generation request has no group plan");
        }

        if (isFamilyVisit(history.getVisit()) && individualPlansCount > 0) {
            throw new ApiException("Family generation must contain only the unified group plan");
        }

        result.setGroupPlanId(groupPlan.getId());
        result.setIndividualPlansCount(individualPlansCount);
        result.setPlanStatus(Boolean.TRUE.equals(groupPlan.getIsActive()) ? "APPROVED" : "GENERATED");

        return result;
    }

    private List<VisitPlan> deriveInitialIndividualPlans(VisitPlan groupPlan, List<VisitMember> goingMembers) {

        if (groupPlan == null || groupPlan.getId() == null || !"GROUP".equals(groupPlan.getPlanKind())) {
            throw new ApiException("A saved group plan is required");
        }

        if (groupPlan.getGeneratedByHistory() == null) {
            throw new ApiException("The group plan must reference its generation request");
        }

        if (goingMembers == null || goingMembers.isEmpty()) {
            throw new ApiException("Planning participants are required");
        }

        Visit visit = groupPlan.getVisit();

        List<VisitPlan> existingIndividualPlans = visitPlanRepository.findAllByVisit_IdAndPlanKind(visit.getId(), "INDIVIDUAL");

        if (!existingIndividualPlans.isEmpty()) {
            throw new ApiException("Initial generation cannot overwrite existing individual plans");
        }

        List<VisitPlan> individualPlans = new ArrayList<>();

        if (isFamilyVisit(visit)) {
            return individualPlans;
        }

        HashSet<Integer> memberIds = new HashSet<>();

        for (VisitMember member : goingMembers) {

            if (member == null || member.getId() == null || !member.getVisit().getId().equals(visit.getId())) {
                throw new ApiException("Planning member does not belong to this visit");
            }

            if (!"GOING".equals(member.getRsvpStatus())) {
                throw new ApiException("Individual plans can only be derived for GOING members");
            }

            validateMemberIdentity(member);

            if (!memberIds.add(member.getId())) {
                throw new ApiException("Planning member IDs must not be repeated");
            }
        }

        for (VisitMember member : goingMembers) {
            VisitPlan individualPlan = new VisitPlan();

            individualPlan.setVisit(visit);
            individualPlan.setPlanKind("INDIVIDUAL");
            individualPlan.setOwnerVisitMember(member);
            individualPlan.setGeneratedByHistory(groupPlan.getGeneratedByHistory());
            individualPlan.setTimingMode(groupPlan.getTimingMode());
            individualPlan.setIsActive(false);

            individualPlans.add(visitPlanRepository.save(individualPlan));
        }

        return individualPlans;
    }

    private PlanItem convertAiActivityToPlanItem(AiPlanItemDtoOut activity, VisitPlan groupPlan, Integer sortOrder, String priority, boolean pinned, boolean rateable) {

        if (activity == null || groupPlan == null || !"GROUP".equals(groupPlan.getPlanKind())) {
            throw new ApiException("Activity and group plan are required");
        }

        if (sortOrder == null || sortOrder < 0) {
            throw new ApiException("A non-negative activity sort order is required");
        }

        if (priority != null && !"MUST_RIDE".equals(priority) && !"PREFERRED".equals(priority) && !"OPTIONAL".equals(priority)) {
            throw new ApiException("Invalid ride priority");
        }

        boolean isRide = "RIDE".equals(activity.getActivityKind());
        boolean isPrayer = "PRAYER".equals(activity.getBreakType());
        boolean isMeetingPoint = "MEETING_POINT".equals(activity.getActivityKind());

        if (!isRide && priority != null) {
            throw new ApiException("Priority can only be assigned to ride activities");
        }

        PlanItem item = new PlanItem();

        item.setVisitPlan(groupPlan);
        item.setActivityKind(activity.getActivityKind());
        item.setRelativeStartMinutes(activity.getRelativeStartMinutes());
        item.setRelativeEndMinutes(activity.getRelativeEndMinutes());
        item.setWalkingMinutes(activity.getWalkingMinutes());
        item.setWaitingMinutes(activity.getWaitingMinutes());
        item.setActivityMinutes(activity.getActivityMinutes());
        item.setBreakType(activity.getBreakType());
        item.setSubgroupKey(activity.getSubgroupKey());
        item.setSortOrder(sortOrder);
        item.setPriority(priority);
        item.setPinned(pinned);
        item.setIsRateable(rateable && !isRide && !isPrayer && !isMeetingPoint);

        if (isRide) {
            Ride ride = rideRepository.findRideById(activity.getRideId());

            if (ride == null) {
                throw new ApiException("Ride not found while preparing plan activities");
            }

            item.setRide(ride);
            item.setTitle(ride.getName());
        }

        if (activity.getPlaceId() != null) {
            Place place = placeRepository.findPlaceById(activity.getPlaceId());

            if (place == null) {
                throw new ApiException("Place not found while preparing plan activities");
            }

            item.setPlace(place);
            item.setTitle(place.getName());
        }

        if (item.getTitle() == null || item.getTitle().isBlank()) {
            String title = switch (activity.getBreakType() == null ? "" : activity.getBreakType()) {
                case "MEAL" -> "بريك وجبة";
                case "SNACK" -> "بريك سناك";
                case "PRAYER" -> "بريك صلاة";
                default -> throw new ApiException("Activity title could not be resolved");
            };

            item.setTitle(title);
        }

        try {
            item.setParticipantVisitMemberIds(participantJsonMapper.writeValueAsString(activity.getParticipantVisitMemberIds()));
        } catch (JacksonException exception) {
            throw new ApiException("Unable to prepare activity participant IDs");
        }

        return item;
    }

    private VisitPlan saveInitialPlanEntities(Visit visit, PlanHistory history, AiPlanDtoOut candidate, List<VisitMember> goingMembers, Map<Integer, String> ridePriorities, Set<Integer> pinnedRideIds, Set<Integer> rateablePlaceIds) {

        if (visit == null || history == null || history.getId() == null) {
            throw new ApiException("Visit and saved generation request are required");
        }

        if (!history.getVisit().getId().equals(visit.getId())) {
            throw new ApiException("Generation request does not belong to this visit");
        }

        if (!"INITIAL".equals(history.getOperationType()) || !"PROCESSING".equals(history.getStatus())) {
            throw new ApiException("A processing initial generation request is required");
        }

        if (candidate == null || !Boolean.TRUE.equals(candidate.getFeasible()) || candidate.getActivities() == null || candidate.getActivities().isEmpty()) {
            throw new ApiException("A feasible plan with activities is required");
        }

        if (goingMembers == null || goingMembers.isEmpty()) {
            throw new ApiException("Planning participants are required");
        }

        if (ridePriorities == null || pinnedRideIds == null || rateablePlaceIds == null) {
            throw new ApiException("Server-side activity preferences and rating rules are required");
        }

        if (!visitPlanRepository.findAllByVisit_IdAndPlanKind(visit.getId(), "GROUP").isEmpty()) {
            throw new ApiException("Initial generation cannot overwrite an existing group plan");
        }

        VisitPlan groupPlan = saveGeneratedGroupActivities(visit, history, candidate, ridePriorities, pinnedRideIds, rateablePlaceIds, history.getPlanningInputSnapshot());

        deriveInitialIndividualPlans(groupPlan, goingMembers);

        return groupPlan;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GenerateVisitPlansDtoOut generatePlan(Integer accountId, Integer visitId, GeneratePlanDtoIn dto) {

        if (dto == null || dto.getRequestKey() == null || dto.getRequestKey().isBlank() || dto.getRequestKey().length() > 80) {
            throw new ApiException("A request key between 1 and 80 characters is required");
        }

        InitialGenerationStart start = runGenerationTransaction(() -> reserveInitialGeneration(accountId, visitId, dto.getRequestKey()));

        if (start.previousResult() != null) {
            return start.previousResult();
        }

        try {
            GenerationSnapshot snapshot = runGenerationTransaction(() -> prepareInitialGenerationSnapshot(accountId, visitId, start.historyId()));
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new ApiException("The OpenAI call must run outside the database transaction");
            }

            JsonNode generationInput = readGenerationJson(snapshot.inputJson());
            AiPlanDtoOut candidate = selectInitialPlanWithExpectedPrayer(generationInput);
            resolveExcludedRideNames(candidate);
            if (!Boolean.TRUE.equals(candidate.getFeasible())) return runGenerationTransaction(() -> failInitialGeneration(accountId, visitId, start.historyId(), candidate.getInfeasibilityReason(), candidate));
            aiPlanService.validatePlanCandidateTiming(candidate, snapshot.durationMinutes(), snapshot.memberIds(), snapshot.tripType());
            aiPlanService.validatePlanCandidateSupervision(candidate, snapshot.ages());
            aiPlanService.validatePlanCandidateActivityKinds(candidate);
            aiPlanService.validatePlanCandidateEligibility(candidate, snapshot.eligibility());
            validateInitialCandidateAgainstSnapshot(candidate, snapshot);
            mergeGenerationWarnings(candidate, generationInput);

            return runGenerationTransaction(() -> finishInitialGeneration(accountId, visitId, start.historyId(), snapshot, candidate));

        } catch (RuntimeException failure) {

            String failureReason = failure instanceof ApiException && failure.getMessage() != null && !failure.getMessage().isBlank() ? failure.getMessage() : "تعذر إكمال التوليد. لم تُحتسب محاولة ناجحة؛ تحقق من الربط قبل إعادة المحاولة بمفتاح جديد.";

            try {
                return runGenerationTransaction(() -> failInitialGeneration(accountId, visitId, start.historyId(), failureReason, null));
            } catch (RuntimeException cleanupFailure) {
                cleanupFailure.addSuppressed(failure);
                throw new ApiException("Generation outcome could not be confirmed. Check this request key before sending a new request");
            }
        }
    }

    private AiPlanDtoOut selectInitialPlanWithExpectedPrayer(JsonNode input) {
        if (!input.path("includePrayer").asBoolean() || !input.path("expectedArrivalTime").isTextual()) return serverItineraryService.selectInitialPlan(input);
        ObjectNode scheduleInput = (ObjectNode) input.deepCopy();
        LocalDateTime arrival = LocalDate.parse(input.path("visitDate").asString()).atTime(java.time.LocalTime.parse(input.path("expectedArrivalTime").asString()));
        int duration = input.path("durationMinutes").asInt();
        LocalDateTime end = arrival.plusMinutes(duration);
        List<Map<String, Object>> events = new ArrayList<>();
        for (LocalDate date = arrival.toLocalDate(); !date.isAfter(end.toLocalDate()); date = date.plusDays(1)) {
            Map<String, LocalDateTime> times = prayerTimeService.getPrayerTimes(date, parkLatitude, parkLongitude);
            for (String name : List.of("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")) {
                LocalDateTime at = times.get(name);
                if (at == null || !at.toLocalDate().equals(date)) throw new ApiException("تعذر التحقق من مواقيت الصلاة قبل التوليد؛ حاول مرة ثانية.");
                if (!at.isBefore(arrival) && at.isBefore(end)) {
                    if (at.plusMinutes(15).isAfter(end)) throw new ApiException("الصلاة تقع عند نهاية الزيارة؛ زِد المدة قليلًا أو عدّل وقت الوصول المتوقع قبل التوليد.");
                    events.add(Map.of("relativePrayerMinute", Math.toIntExact(java.time.Duration.between(arrival, at).toMinutes()), "activityMinutes", 15));
                }
            }
        }
        events.sort(Comparator.comparingInt(event -> (Integer) event.get("relativePrayerMinute")));
        scheduleInput.set("prayerEvents", participantJsonMapper.valueToTree(events));
        scheduleInput.put("prayerTimesVerified", true);
        scheduleInput.put("prayerReserveMinutes", events.size() * 15);
        ObjectNode selectionInput = scheduleInput.deepCopy();
        for (int reserve = events.size() * 15; reserve < duration && reserve <= events.size() * 15 + 180; reserve += 15) {
            selectionInput.put("prayerReserveMinutes", reserve);
            AiPlanDtoOut candidate = serverItineraryService.selectInitialPlan(selectionInput);
            if (!Boolean.TRUE.equals(candidate.getFeasible())) return candidate;
            if (scheduleInput.path("allowSplit").asBoolean() || arrivalPrayerScheduleService.scheduleApprovedActivitiesWithPrayer(scheduleInput, candidate.getActivities()) != null) return candidate;
        }
        throw new ApiException("الألعاب والوجبات والصلاة لا تتسع ضمن المدة المحددة حسب وصولك المتوقع؛ زِد مدة الزيارة أو قلّل اختياراتك ثم ولّد الخطة.");
    }

    private <T> T runGenerationTransaction(Supplier<T> action) {

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(60);

        return template.execute(status -> action.get());
    }

    private Visit lockGenerationOwnerAndVisit(Integer accountId, Integer visitId) {

        if (accountId == null || accountId <= 0 || visitId == null || visitId <= 0) {
            throw new ApiException("Positive account and visit IDs are required");
        }

        Account account = planHistoryRepository.findAccountByIdForGenerationUpdate(accountId);

        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("An active account is required");
        }

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can generate this plan");
        }

        return visit;
    }

    private PlanHistory lockInitialGeneration(Integer accountId, Integer visitId, Integer historyId) {

        PlanHistory history = planHistoryRepository.findPlanHistoryByIdForUpdate(historyId);

        if (history == null || !history.getAccount().getId().equals(accountId) || !history.getVisit().getId().equals(visitId) || !"INITIAL".equals(history.getOperationType())) {
            throw new ApiException("Initial generation request not found for this account and visit");
        }

        return history;
    }

    private InitialGenerationStart reserveInitialGeneration(Integer accountId, Integer visitId, String requestKey) {

        Visit visit = lockGenerationOwnerAndVisit(accountId, visitId);
        PlanHistory existing = findExistingGenerationRequest(accountId, visitId, requestKey, "INITIAL");

        if (existing != null) {
            return new InitialGenerationStart(existing.getId(), convertGenerationRequestToDtoOut(existing));
        }

        if ((!"DRAFT".equals(visit.getStatus()) && !"PLANNING".equals(visit.getStatus())) || visit.getStartedAt() != null) {
            throw new ApiException("Initial generation requires a visit in PLANNING status before execution");
        }

        if (visit.getVisitDate() == null || visit.getVisitDate().isBefore(LocalDate.now(ZoneId.of("Asia/Riyadh")))) {
            throw new ApiException("Visit date must not be in the past");
        }

        if (!visitPlanRepository.findAllByVisit_Id(visitId).isEmpty()) {
            throw new ApiException("Use regeneration for a visit that already has plans");
        }

        for (PlanHistory history : planHistoryRepository.findAllByAccount_IdOrderByStartedAtDescIdDesc(accountId)) {

            if (history.getVisit().getId().equals(visitId) && "PROCESSING".equals(history.getStatus())) {
                throw new ApiException("Another generation request is already processing for this visit");
            }
        }

        visitMemberService.freezeMemberSnapshots(visit);
        buildVisitPlanningContext(visit);
        validateInitialMemberAnswers(visit, buildGoingMemberPlanningContext(visit));

        // Expected Nawaf contract: joins this transaction; reserves quota/payment and creates PROCESSING history.
        Integer historyId = paymentOrderService.reserveEntitlement(accountId, visitId, requestKey, "INITIAL");
        PlanHistory history = lockInitialGeneration(accountId, visitId, historyId);

        if (!requestKey.equals(history.getRequestKey()) || !"PROCESSING".equals(history.getStatus()) || Boolean.TRUE.equals(history.getCountsTowardQuota())) {
            throw new ApiException("Entitlement reservation returned an invalid generation request");
        }

        if (!"FREE".equals(history.getBillingMode()) && !"PAID".equals(history.getBillingMode())) {
            throw new ApiException("Entitlement reservation returned an invalid billing mode");
        }


        visit.setStatus("PLANNING");
        visit.setMembersLocked(true);
        visitRepository.save(visit);

        return new InitialGenerationStart(historyId, null);
    }

    private GenerationSnapshot prepareInitialGenerationSnapshot(Integer accountId, Integer visitId, Integer historyId) {

        Visit visit = lockGenerationOwnerAndVisit(accountId, visitId);
        PlanHistory history = lockInitialGeneration(accountId, visitId, historyId);

        if (!"PROCESSING".equals(history.getStatus()) || !"PLANNING".equals(visit.getStatus()) || !Boolean.TRUE.equals(visit.getMembersLocked())) {
            throw new ApiException("Generation request is no longer ready for planning");
        }

        if (history.getPlanningInputSnapshot() != null) {
            throw new ApiException("This generation request already has a prepared input; do not repeat its AI call");
        }

        GenerationSnapshot snapshot = collectInitialGenerationSnapshot(accountId, visit);

        history.setPlanningInputSnapshot(snapshot.inputJson());
        planHistoryRepository.save(history);

        return snapshot;
    }

    private GenerationSnapshot collectInitialGenerationSnapshot(Integer accountId, Visit visit) {

        List<Map<String, Object>> members = buildGoingMemberPlanningContext(visit);
        Map<Integer, Integer> ages = new LinkedHashMap<>();
        Set<Integer> memberIds = new HashSet<>();

        for (Map<String, Object> member : members) {
            Integer memberId = (Integer) member.get("visitMemberId");
            memberIds.add(memberId);
            ages.put(memberId, (Integer) member.get("ageYears"));
        }

        validateInitialMemberAnswers(visit, members);

        Map<Integer, Map<Integer, String>> persistentPreferences = collectPersistentRidePreferences(visit, memberIds);
        List<Map<String, Object>> rideRows = new ArrayList<>();
        Map<Integer, Map<Integer, String>> eligibility = new LinkedHashMap<>();
        Map<Integer, Map<Integer, String>> ridePreferences = new LinkedHashMap<>();
        List<Ride> openRides = new ArrayList<>(rideRepository.findAll().stream().filter(ride -> PlanningAvailability.canPlanRide(ride, visit.getVisitDate())).toList());

        openRides.sort(Comparator.comparing(Ride::getId));

        for (Ride ride : openRides) {
            // These provider calls must read stored planning data, not call an external API inside this transaction.
            JsonNode snapshot = participantJsonMapper.valueToTree(rideService.getRideSnapshotForVisit(ride.getId(), visit.getVisitDate(), visit.getExpectedArrivalTime() == null ? visit.getPlannedStartTime() : visit.getExpectedArrivalTime(), visit.getDurationMinutes()));

            if (!snapshot.isObject() || requirePositiveInteger(snapshot, "id") != ride.getId().intValue() || !"OPEN".equals(requireText(snapshot, "status"))) {
                throw new ApiException("Ride snapshot identity or status is invalid");
            }

            Map<String, Object> row = new LinkedHashMap<>();

            row.put("id", ride.getId());
            row.put("name", ride.getName());
            row.put("category", ride.getCategory());
            row.put("status", "OPEN");
            row.put("operationalStatus", snapshot.path("operationalStatus").asString("UNKNOWN"));
            row.put("availabilityIsEstimate", snapshot.path("availabilityIsEstimate").asBoolean());
            row.put("availabilitySource", snapshot.path("availabilitySource").asString("UNKNOWN"));
            row.put("nodeKey", requireText(snapshot, "nodeKey"));
            row.put("rideDurationMinutes", requirePositiveInteger(snapshot, "rideDurationMinutes"));
            row.put("timingSource", snapshot.path("timingSource").asString("UNKNOWN"));
            row.put("entryExitBufferMinutes", requireNonNegativeInteger(snapshot, "entryExitBufferMinutes"));

            long planningActivityMinutes = (long) requirePositiveInteger(snapshot, "rideDurationMinutes") + requireNonNegativeInteger(snapshot, "entryExitBufferMinutes");

            if (planningActivityMinutes > Integer.MAX_VALUE) {
                throw new ApiException("Ride activity duration exceeds the supported range");
            }

            row.put("planningActivityMinutes", (int) planningActivityMinutes);
            row.put("features", readTextArray(snapshot.path("features"), "Ride features"));

            JsonNode wait = snapshot.path("planningWaitMinutes");

            if (!wait.isNull() && !wait.isMissingNode()) {
                row.put("planningWaitMinutes", requireNonNegativeInteger(snapshot, "planningWaitMinutes"));
            } else {
                row.put("planningWaitMinutes", null);
            }

            if (!snapshot.path("waitIsEstimate").isBoolean()) {
                throw new ApiException("Ride wait estimate classification is required");
            }

            row.put("waitIsEstimate", snapshot.path("waitIsEstimate").asBoolean());
            row.put("waitSource", requireText(snapshot, "waitSource"));
            row.put("waitObservedAt", row.get("planningWaitMinutes") == null ? null : requireText(snapshot, "waitObservedAt"));

            validateWaitSnapshot(row);

            Map<Integer, String> statuses = new LinkedHashMap<>();
            Map<Integer, String> preferences = new LinkedHashMap<>();
            Map<Integer, List<String>> missingFields = new LinkedHashMap<>();

            for (Integer memberId : memberIds.stream().sorted().toList()) {
                JsonNode result = participantJsonMapper.valueToTree(rideService.checkEligibilityForMember(accountId, memberId, ride.getId()));
                String status = requireText(result, "status");

                if (!Set.of("ELIGIBLE", "NOT_ELIGIBLE", "UNKNOWN").contains(status)) {
                    throw new ApiException("Ride eligibility provider returned an invalid status");
                }

                statuses.put(memberId, status);
                missingFields.put(memberId, readTextArray(result.path("missingFields"), "Eligibility missing fields"));
                preferences.put(memberId, persistentPreferences.getOrDefault(memberId, Map.of()).getOrDefault(ride.getId(), "NEUTRAL"));
            }

            eligibility.put(ride.getId(), statuses);
            ridePreferences.put(ride.getId(), preferences);
            row.put("missingFieldsByMember", missingFields);
            row.put("preferencesByMember", preferences);
            rideRows.add(row);
        }

        JsonNode locationData = participantJsonMapper.valueToTree(placeService.getWalkingContext(accountId, visit.getId()));

        if (!locationData.isObject() || !locationData.path("places").isArray() || !locationData.path("routes").isArray()) {
            throw new ApiException("Calibrated places and directed walking routes are required");
        }

        Map<String, Object> walking = new LinkedHashMap<>();

        walking.put("entranceNodeKey", requireText(locationData, "entranceNodeKey"));
        walking.put("revision", requireText(locationData, "revision"));

        List<Map<String, Object>> placeRows = new ArrayList<>();
        List<JsonNode> providerPlaces = new ArrayList<>();
        locationData.path("places").forEach(providerPlaces::add);
        providerPlaces.sort(Comparator.comparingInt(node -> requirePositiveInteger(node, "id")));
        Set<Integer> placeIds = new HashSet<>();
        Set<Integer> rateablePlaceIds = new HashSet<>();

        for (JsonNode placeNode : providerPlaces) {
            int placeId = requirePositiveInteger(placeNode, "id");

            if (!placeIds.add(placeId) || !"OPEN".equals(requireText(placeNode, "status")) || !placeNode.path("rateable").isBoolean()) {
                throw new ApiException("Planning place identity, status or rating rule is invalid");
            }

            Map<String, Object> row = new LinkedHashMap<>();

            row.put("id", placeId);
            row.put("name", requireText(placeNode, "name"));
            row.put("kind", requireText(placeNode, "kind"));
            row.put("status", "OPEN");
            row.put("availabilitySource", placeNode.path("availabilitySource").asString("UNKNOWN"));
            row.put("nodeKey", requireText(placeNode, "nodeKey"));
            row.put("rateable", placeNode.path("rateable").asBoolean());

            if (placeNode.path("rateable").asBoolean()) {
                rateablePlaceIds.add(placeId);
            }

            placeRows.add(row);
        }

        List<Map<String, Object>> routes = new ArrayList<>();
        Set<String> routeKeys = new HashSet<>();

        for (JsonNode route : locationData.path("routes")) {
            String from = requireText(route, "fromNodeKey");
            String to = requireText(route, "toNodeKey");
            String key = walkingRouteKey(from, to);

            if (!routeKeys.add(key)) {
                throw new ApiException("Duplicate directed walking route");
            }

            routes.add(Map.of("fromNodeKey", from, "toNodeKey", to, "minutes", requireNonNegativeInteger(route, "minutes")));
        }

        routes.sort(Comparator.comparing(row -> walkingRouteKey((String) row.get("fromNodeKey"), (String) row.get("toNodeKey"))));
        walking.put("routes", routes);

        JsonNode rawMealPolicy = participantJsonMapper.valueToTree(visitMealBreakService.getMealBreakPolicy(accountId, visit.getId()));
        JsonNode mealPolicy = normalizeMealPolicy(rawMealPolicy, memberIds, visit.getDurationMinutes());

        JsonNode rideSnapshots = participantJsonMapper.valueToTree(rideRows);
        JsonNode placeSnapshots = participantJsonMapper.valueToTree(placeRows);
        JsonNode walkingContext = participantJsonMapper.valueToTree(walking);
        String inputJson = buildInitialPlanInput(visit, members, rideSnapshots, placeSnapshots, walkingContext, mealPolicy, eligibility);
        ObjectNode input = (ObjectNode) readGenerationJson(inputJson);

        input.set("ridePreferencesByRideAndMember", participantJsonMapper.valueToTree(ridePreferences));
        input.put("allowSplit", isSplitAllowedForInitialPlan(visit));

        Map<Integer, String> priorities = new LinkedHashMap<>();

        for (Map<String, Object> row : rideRows) {
            priorities.put((Integer) row.get("id"), "OPTIONAL");
        }

        for (JsonNode member : input.path("members")) {
            for (JsonNode selection : member.path("selectedRides")) {
                int rideId = requirePositiveInteger(selection, "rideId");
                String priority = requireText(selection, "priority");
                if (priorities.containsKey(rideId) && ("MUST_RIDE".equals(priority) || "PREFERRED".equals(priority) && !"MUST_RIDE".equals(priorities.get(rideId)))) {
                    priorities.put(rideId, priority);
                }
            }
        }
        Map<Integer, Set<Integer>> requiredRides = buildRequiredRideParticipants(input);

        for (Integer rideId : requiredRides.keySet()) {
            Ride requestedRide = rideRepository.findRideById(rideId);
            if (requestedRide == null) throw new ApiException("A requested ride does not exist");
            priorities.put(rideId, "MUST_RIDE");
        }

        return new GenerationSnapshot(writeGenerationJson(input), visit.getDurationMinutes(), visit.getTripType(), memberIds, ages, eligibility, priorities, Set.of(), rateablePlaceIds);
    }

    private Map<Integer, Map<Integer, String>> collectPersistentRidePreferences(Visit visit, Set<Integer> memberIds) {

        Map<Integer, Map<Integer, String>> results = new LinkedHashMap<>();

        for (VisitMember member : visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visit.getId(), "GOING")) {

            if (!memberIds.contains(member.getId())) {
                throw new ApiException("Planning membership changed while preparing preferences");
            }

            Map<Integer, String> preferences = new LinkedHashMap<>();

            if (member.getVisitorProfile() != null) {

                for (RidePreference preference : ridePreferenceRepository.findRidePreferencesByVisitorProfileId(member.getVisitorProfile().getId())) {
                    String value = preference.getPreference();

                    if (!Set.of("REPEAT", "AVOID", "NEUTRAL").contains(value) || preferences.put(preference.getRide().getId(), value) != null) {
                        throw new ApiException("Stored ride preferences are invalid or duplicated");
                    }
                }
            }

            if (member.getFamilyMember() != null && "ANSWERED".equals(member.getAnswerStatus()) && member.getPreferences() != null && !member.getPreferences().isBlank()) {
                JsonNode familyPreferences = readGenerationJson(member.getPreferences()).path("ridePreferences");

                if (!familyPreferences.isMissingNode() && !familyPreferences.isNull()) {

                    if (!familyPreferences.isArray()) {
                        throw new ApiException("Family ride preferences must be an array");
                    }

                    for (JsonNode entry : familyPreferences) {
                        String value = requireText(entry, "preference");

                        if (!Set.of("REPEAT", "AVOID", "NEUTRAL").contains(value) || preferences.put(requirePositiveInteger(entry, "rideId"), value) != null) {
                            throw new ApiException("Family ride preferences are invalid or duplicated");
                        }
                    }
                }
            }

            results.put(member.getId(), preferences);
        }

        return results;
    }

    private GenerateVisitPlansDtoOut finishInitialGeneration(Integer accountId, Integer visitId, Integer historyId, GenerationSnapshot prepared, AiPlanDtoOut candidate) {

        Visit visit = lockGenerationOwnerAndVisit(accountId, visitId);
        PlanHistory history = lockInitialGeneration(accountId, visitId, historyId);

        if ("SUCCEEDED".equals(history.getStatus())) {
            return convertGenerationRequestToDtoOut(history);
        }

        if (!"PROCESSING".equals(history.getStatus()) || !"PLANNING".equals(visit.getStatus()) || visit.getStartedAt() != null || !Boolean.TRUE.equals(visit.getMembersLocked())) {
            throw new ApiException("Generation state changed before the plan could be saved");
        }

        if (history.getPlanningInputSnapshot() == null || !readGenerationJson(history.getPlanningInputSnapshot()).equals(readGenerationJson(prepared.inputJson()))) {
            throw new ApiException("Generation input does not match the saved request snapshot");
        }

        GenerationSnapshot current = collectInitialGenerationSnapshot(visit.getOrganizer().getId(), visit);

        if (!generationSemanticInput(prepared.inputJson()).equals(generationSemanticInput(current.inputJson()))) {
            throw new ApiException("Planning data changed while generating the plan; a new request is required");
        }

        aiPlanService.validatePlanCandidateTiming(candidate, current.durationMinutes(), current.memberIds(), current.tripType());
        aiPlanService.validatePlanCandidateSupervision(candidate, current.ages());
        aiPlanService.validatePlanCandidateActivityKinds(candidate);
        aiPlanService.validatePlanCandidateEligibility(candidate, current.eligibility());
        validateInitialCandidateAgainstSnapshot(candidate, current);
        validateCandidateResources(candidate, visit.getVisitDate());

        List<VisitMember> members = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING");

        saveInitialPlanEntities(visit, history, candidate, members, current.priorities(), current.pinnedRideIds(), current.rateablePlaceIds());
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ApiException("Entitlement consumption must run inside the plan saving transaction");
        }

        if (history == null || history.getId() == null) {
            throw new ApiException("A saved generation request is required");
        }

        if (!"PROCESSING".equals(history.getStatus())) {
            throw new ApiException("Only a processing generation request can consume entitlement");
        }


        if (!"FREE".equals(history.getBillingMode()) && !"PAID".equals(history.getBillingMode())) {
            throw new ApiException("Invalid generation billing mode");
        }


        paymentOrderService.consumeEntitlement(history.getAccount().getId(), history.getId());

        history.setStatus("SUCCEEDED");
        history.setCountsTowardQuota(true);
        history.setCompletedAt(LocalDateTime.now(ZoneId.of("Asia/Riyadh")));
        history.setFailureReason(null);
        history.setGenerationResultSnapshot(writeGenerationJson(candidate));

        planHistoryRepository.save(history);
        planHistoryRepository.flush();
        notificationEventService.notifyPlanGenerated(history);

        return convertGenerationRequestToDtoOut(history);
    }

    private GenerateVisitPlansDtoOut failInitialGeneration(Integer accountId, Integer visitId, Integer historyId, String reason, AiPlanDtoOut candidate) {

        Visit visit = lockGenerationOwnerAndVisitForCleanup(accountId, visitId);

        PlanHistory history = lockInitialGeneration(accountId, visitId, historyId);

        if ("SUCCEEDED".equals(history.getStatus()) || "FAILED".equals(history.getStatus())) {
            return convertGenerationRequestToDtoOut(history);
        }

        if (!"PROCESSING".equals(history.getStatus()) || Boolean.TRUE.equals(history.getCountsTowardQuota())) {
            throw new ApiException("The generation reservation requires reconciliation");
        }

        // Expected Nawaf contract: releases this request's reservation once, within this same transaction.
        paymentOrderService.releaseEntitlement(accountId, historyId);

        if ("PLANNING".equals(visit.getStatus()) && visitPlanRepository.findAllByVisit_Id(visitId).isEmpty()) {
            visit.setStatus("DRAFT");
            visit.setMembersLocked(false);
            visitRepository.save(visit);
        }

        history.setStatus("FAILED");
        history.setCountsTowardQuota(false);
        history.setCompletedAt(LocalDateTime.now(ZoneId.of("Asia/Riyadh")));
        history.setFailureReason(reason.length() <= 500 ? reason : reason.substring(0, 500));

        if (candidate != null) {
            history.setGenerationResultSnapshot(writeGenerationJson(candidate));
        }

        planHistoryRepository.save(history);

        return convertGenerationRequestToDtoOut(history);
    }

    private Visit lockGenerationOwnerAndVisitForCleanup(Integer accountId, Integer visitId) {

        Account account = planHistoryRepository.findAccountByIdForGenerationUpdate(accountId);
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

        if (account == null || visit == null || !visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Generation reservation owner or visit no longer exists");
        }

        return visit;
    }

    private void validateInitialCandidateAgainstSnapshot(AiPlanDtoOut candidate, GenerationSnapshot snapshot) {

        JsonNode input = readGenerationJson(snapshot.inputJson());
        candidate.setExcludedRides(serverItineraryService.explainRequiredExclusions(input, candidate.getActivities()));
        resolveExcludedRideNames(candidate);
        Map<Integer, JsonNode> rides = indexSnapshotResources(input.path("rides"));
        Map<Integer, JsonNode> places = indexSnapshotResources(input.path("places"));
        Map<Integer, JsonNode> members = new HashMap<>();

        for (JsonNode member : input.path("members")) {
            members.put(requirePositiveInteger(member, "visitMemberId"), member);
        }

        Map<Integer, Map<Integer, Integer>> rideCounts = new HashMap<>();
        Map<Integer, Set<Integer>> completedRideParticipants = new HashMap<>();
        Map<Integer, List<AiPlanItemDtoOut>> byMember = new HashMap<>();
        boolean allowSplit = input.path("allowSplit").asBoolean();

        for (AiPlanItemDtoOut activity : candidate.getActivities()) {
            Set<Integer> participants = new HashSet<>(activity.getParticipantVisitMemberIds());

            if (!allowSplit && !participants.equals(snapshot.memberIds())) {
                throw new ApiException("This visit requires all participants to follow the unified plan");
            }

            if ("PRAYER".equals(activity.getBreakType()) && !input.path("includePrayer").asBoolean()) {
                throw new ApiException("The plan contains a prayer break that was not requested");
            }

            if ("PRAYER".equals(activity.getBreakType()) && activity.getPlaceId() == null) {
                throw new ApiException("A prayer break must reference a known prayer area");
            }

            if (activity.getPlaceId() != null && !places.containsKey(activity.getPlaceId())) {
                throw new ApiException("The plan contains a place outside the supplied planning catalog");
            }

            if ("RIDE".equals(activity.getActivityKind())) {
                JsonNode ride = rides.get(activity.getRideId());

                if (ride == null || activity.getActivityMinutes() != requirePositiveInteger(ride, "planningActivityMinutes")) {
                    throw new ApiException("Generated ride identity or duration does not match the planning snapshot");
                }

                if (ride.path("planningWaitMinutes").isNull() || activity.getWaitingMinutes() != requireNonNegativeInteger(ride, "planningWaitMinutes")) {
                    throw new ApiException("Waiting time mismatch for ride " + activity.getRideId() + ": expected=" + ride.path("planningWaitMinutes") + ", generated=" + activity.getWaitingMinutes());                }

                if (completedRideParticipants.containsKey(activity.getRideId())) {
                    throw new ApiException("A ride must appear only once in the group plan");
                }
                Set<String> features = new HashSet<>(readTextArray(ride.path("features"), "Ride features"));

                for (Integer memberId : participants) {
                    JsonNode member = members.get(memberId);
                    String preference = input.path("ridePreferencesByRideAndMember").path(String.valueOf(activity.getRideId())).path(String.valueOf(memberId)).asString();

                    if (!serverItineraryService.canRide(input, ride, List.of(memberId))) {
                        throw new ApiException("The generated ride conflicts with eligibility or saved exclusions");
                    }
                    if ("AVOID".equals(preference)) {
                        throw new ApiException("The generated plan includes an avoided ride for a participant");
                    }

                    if (member.path("dislikedFeaturesKnown").asBoolean()) {

                        for (String feature : readTextArray(member.path("dislikedFeatures"), "Disliked features")) {

                            if (features.contains(feature)) {
                                throw new ApiException("The generated ride conflicts with a participant's avoidance preferences");
                            }
                        }
                    }

                    int count = rideCounts.computeIfAbsent(memberId, key -> new HashMap<>()).merge(activity.getRideId(), 1, Integer::sum);

                    if (count > 1) {
                        throw new ApiException("A ride must appear only once in the group plan");
                    }
                }

                completedRideParticipants.computeIfAbsent(activity.getRideId(), key -> new HashSet<>()).addAll(participants);
            } else if (activity.getWaitingMinutes() != 0) {
                throw new ApiException("Non-ride queue estimates are not supported by the planning data contract");
            }

            for (Integer memberId : participants) {
                byMember.computeIfAbsent(memberId, key -> new ArrayList<>()).add(activity);
            }
        }

        validateExcludedRides(candidate, input, completedRideParticipants, rides, snapshot.eligibility());

        if (input.path("includePrayer").asBoolean() && !input.path("arrivalTimeKnown").asBoolean() && !"ARRIVAL".equals(input.path("operationType").asString()) && candidate.getActivities().stream().noneMatch(activity -> "PRAYER".equals(activity.getBreakType()))) {
            int latestEnd = candidate.getActivities().stream().mapToInt(AiPlanItemDtoOut::getRelativeEndMinutes).max().orElse(0);
            int duration = requirePositiveInteger(input, "durationMinutes");
            if (latestEnd > duration - 15) throw new ApiException("Initial planning must reserve 15 minutes for prayer without inventing a prayer time");
        }

        validateSnapshotWalking(byMember, rides, places, input.path("walking"));

        if (allowSplit) {
            validateMinorTimelineCoverage(byMember, snapshot.ages());
        }

        validateSnapshotMeals(candidate, input.path("mealPolicy"), byMember);
    }

    private void validateAdditionalRides(JsonNode input, List<AiPlanItemDtoOut> baseline, List<AiPlanItemDtoOut> additions, List<Integer> affected) {
        List<AiPlanItemDtoOut> combined = new ArrayList<>(baseline);
        combined.addAll(additions);
        Set<AiPlanItemDtoOut> inserted = new HashSet<>(additions);
        Map<Integer, JsonNode> rides = indexSnapshotResources(input.path("rides"));
        for (AiPlanItemDtoOut addition : additions) {
            if (!"RIDE".equals(addition.getActivityKind()) || addition.getPlaceId() != null || addition.getBreakType() != null || !new HashSet<>(affected).containsAll(addition.getParticipantVisitMemberIds())) throw new ApiException("A new ride is outside the approved proposal scope");
            JsonNode ride = rides.get(addition.getRideId());
            if (!serverItineraryService.canRide(input, ride, addition.getParticipantVisitMemberIds()) || addition.getWaitingMinutes() != requireNonNegativeInteger(ride, "planningWaitMinutes") || addition.getActivityMinutes() != requirePositiveInteger(ride, "planningActivityMinutes")) throw new ApiException("A new ride no longer matches planning data");
            if (!input.path("allowSplit").asBoolean() && !new HashSet<>(affected).equals(new HashSet<>(addition.getParticipantVisitMemberIds()))) throw new ApiException("A unified visit cannot add a split activity");
            for (Integer memberId : addition.getParticipantVisitMemberIds()) if (combined.stream().filter(activity -> addition.getRideId().equals(activity.getRideId()) && activity.getParticipantVisitMemberIds().contains(memberId)).count() != 1) throw new ApiException("An added ride must not repeat an existing activity");
        }
        for (Integer memberId : affected) {
            String node = requireText(input.path("walking"), "entranceNodeKey");
            AiPlanItemDtoOut previous = null;
            for (AiPlanItemDtoOut activity : combined.stream().filter(item -> item.getParticipantVisitMemberIds().contains(memberId)).sorted(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes)).toList()) {
                String destination = serverItineraryService.destinationNode(input, activity, node);
                if (inserted.contains(activity) || previous != null && inserted.contains(previous)) {
                    Integer walk = serverItineraryService.walking(input, node, destination);
                    if (walk == null || !walk.equals(activity.getWalkingMinutes())) throw new ApiException("An added ride changes an approved walking route or uses a missing route");
                    if (previous != null && activity.getRelativeStartMinutes() < (previous.getExecutionEndMinutes() == null ? previous.getRelativeEndMinutes() : previous.getExecutionEndMinutes())) throw new ApiException("An added ride overlaps an existing activity");
                }
                node = destination;
                previous = activity;
            }
        }
    }

    private void resolveExcludedRideNames(AiPlanDtoOut candidate) {
        for (ExcludedRideDtoOut exclusion : candidate.getExcludedRides()) {
            Ride ride = rideRepository.findRideById(exclusion.getRideId());
            if (ride == null) throw new ApiException("A selected ride no longer exists");
            exclusion.setRideName(ride.getName());
        }
    }

    private void validateExcludedRides(AiPlanDtoOut candidate, JsonNode input, Map<Integer, Set<Integer>> scheduled, Map<Integer, JsonNode> rides, Map<Integer, Map<Integer, String>> eligibility) {
        Map<Integer, Set<Integer>> required = buildRequiredRideParticipants(input);
        Set<String> seen = new HashSet<>();
        List<ExcludedRideDtoOut> excluded = candidate.getExcludedRides() == null ? List.of() : candidate.getExcludedRides();
        for (ExcludedRideDtoOut item : excluded) {
            if (item == null || item.getRideId() == null || item.getVisitMemberId() == null || item.getReasonCode() == null || item.getReasonMessage() == null || item.getReasonMessage().isBlank()) throw new ApiException("Excluded ride details are incomplete");
            Integer rideId = item.getRideId();
            Integer memberId = item.getVisitMemberId();
            if (!required.getOrDefault(rideId, Set.of()).contains(memberId) || !seen.add(rideId + ":" + memberId)) throw new ApiException("Excluded ride is not an omitted required selection");
            if (scheduled.getOrDefault(rideId, Set.of()).contains(memberId)) throw new ApiException("A ride cannot be scheduled and excluded for the same member");
            JsonNode ride = rides.get(rideId);
            String status = eligibility.getOrDefault(rideId, Map.of()).get(memberId);
            String reason = item.getReasonCode();
            if (!"MUST_RIDE".equals(item.getPriority())) throw new ApiException("Excluded required ride priority does not match the original selection");
            Ride persistedRide = rideRepository.findRideById(rideId);
            if (persistedRide == null || !Objects.equals(item.getRideName(), persistedRide.getName())) throw new ApiException("Excluded ride must match a real park ride");
            if (ride == null && !"CLOSED".equals(reason)) throw new ApiException("A non-closed excluded ride must have a planning snapshot");
            if ("NOT_ELIGIBLE".equals(status) && !"NOT_ELIGIBLE".equals(reason)) throw new ApiException("Ineligible ride exclusion must state the eligibility reason");
            if (ride != null && ride.path("planningWaitMinutes").isNull() && !"MISSING_WAIT_DATA".equals(reason) && !"NOT_ELIGIBLE".equals(reason)) throw new ApiException("Missing wait data must be reported before time-based exclusions");
            if ("NOT_ELIGIBLE".equals(reason) && !"NOT_ELIGIBLE".equals(status)) throw new ApiException("Ride ineligibility is not supported by planning data");
            if ("INSUFFICIENT_TIME".equals(reason) && ride != null && ride.path("planningWaitMinutes").isNumber()) {
                long requiredMinutes = (long) ride.path("planningWaitMinutes").asInt() + ride.path("planningActivityMinutes").asInt();
                long deadline = (long) requirePositiveInteger(input, "durationMinutes") - (input.path("includePrayer").asBoolean() ? 15 : 0);
                long occupiedMinutes = candidate.getActivities().stream().filter(activity -> activity.getParticipantVisitMemberIds().contains(memberId)).mapToLong(activity -> (long) activity.getWalkingMinutes() + activity.getWaitingMinutes() + activity.getActivityMinutes()).sum();
                if (requiredMinutes + occupiedMinutes <= deadline) throw new ApiException("Insufficient-time exclusion is not established by the supplied activity durations; use a grounded route, scheduling, or goal conflict reason");
            }
            if ("MISSING_WAIT_DATA".equals(reason) && ride != null && ride.path("planningWaitMinutes").isNumber()) throw new ApiException("Ride waiting data exists in the planning snapshot");
            if ("CLOSED".equals(reason) && (ride != null && "OPEN".equals(ride.path("status").asString()) || ride == null && (rideRepository.findRideById(rideId) == null || "OPEN".equals(rideRepository.findRideById(rideId).getStatus())))) throw new ApiException("Ride closure is not confirmed by planning data");
            if (!Set.of("INSUFFICIENT_TIME", "CLOSED", "NOT_ELIGIBLE", "MISSING_WAIT_DATA", "MISSING_ROUTE", "SCHEDULE_CONFLICT", "GOAL_CONFLICT", "PREFERENCE_CONFLICT").contains(reason)) throw new ApiException("Unknown excluded ride reason");
            if ("PREFERENCE_CONFLICT".equals(reason) && ride != null && serverItineraryService.canRide(input, ride, List.of(memberId))) throw new ApiException("Preference exclusion is not supported by current planning data");
            if ("GOAL_CONFLICT".equals(reason) && (!input.path("primaryGoal").isTextual() || input.path("primaryGoal").asString().isBlank())) throw new ApiException("Goal conflict requires a matching primary goal");
            if (ride != null && !Objects.equals(item.getRideName(), ride.path("name").asString())) throw new ApiException("Excluded ride name does not match the planning snapshot");
        }
        for (Map.Entry<Integer, Set<Integer>> request : required.entrySet()) {
            for (Integer memberId : request.getValue()) {
                if (!scheduled.getOrDefault(request.getKey(), Set.of()).contains(memberId) && !seen.contains(request.getKey() + ":" + memberId)) throw new ApiException("A requested MUST_RIDE must be scheduled or explicitly excluded");
            }
        }
    }

    private void validateMinorTimelineCoverage(Map<Integer, List<AiPlanItemDtoOut>> byMember, Map<Integer, Integer> ages) {

        int planEnd = byMember.values().stream().flatMap(List::stream).mapToInt(AiPlanItemDtoOut::getRelativeEndMinutes).max().orElse(0);

        for (Map.Entry<Integer, Integer> member : ages.entrySet()) {

            if (member.getValue() != null && member.getValue() >= 18) {
                continue;
            }

            List<AiPlanItemDtoOut> activities = new ArrayList<>(byMember.getOrDefault(member.getKey(), List.of()));
            activities.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes));
            int coveredUntil = 0;

            for (AiPlanItemDtoOut activity : activities) {

                if (activity.getRelativeStartMinutes() != coveredUntil) {
                    throw new ApiException("A split plan must explicitly cover each minor's waiting and activities without unsupervised gaps");
                }

                coveredUntil = activity.getRelativeEndMinutes();
            }

            if (coveredUntil != planEnd) {
                throw new ApiException("A split plan must cover supervision until the shared itinerary ends");
            }
        }
    }

    private Map<Integer, Set<Integer>> buildRequiredRideParticipants(JsonNode input) {

        Map<Integer, Set<Integer>> required = new LinkedHashMap<>();
        Set<Integer> allMembers = new HashSet<>();

        for (JsonNode member : input.path("members")) {
            allMembers.add(requirePositiveInteger(member, "visitMemberId"));
        }

        for (Integer rideId : readPositiveIntegerArray(input.path("sharedSelectedRideIds"), "Shared selected rides", true)) {
            required.computeIfAbsent(rideId, key -> new HashSet<>()).addAll(allMembers);
        }

        if (input.path("allowSplit").asBoolean() || "SOLO".equals(input.path("tripType").asString())) {

            for (JsonNode member : input.path("members")) {
                int memberId = requirePositiveInteger(member, "visitMemberId");

                for (Integer rideId : readPositiveIntegerArray(member.path("selectedRideIds"), "Selected rides", true)) {
                    required.computeIfAbsent(rideId, key -> new HashSet<>()).add(memberId);
                }
            }
        }

        return required;
    }

    private void validateSnapshotWalking(Map<Integer, List<AiPlanItemDtoOut>> byMember, Map<Integer, JsonNode> rides, Map<Integer, JsonNode> places, JsonNode walking) {

        Map<String, Integer> routes = new HashMap<>();

        for (JsonNode route : walking.path("routes")) {
            routes.put(walkingRouteKey(requireText(route, "fromNodeKey"), requireText(route, "toNodeKey")), requireNonNegativeInteger(route, "minutes"));
        }

        Map<Integer, String> positions = new HashMap<>();
        Set<AiPlanItemDtoOut> unique = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        byMember.values().forEach(unique::addAll);
        List<AiPlanItemDtoOut> ordered = unique.stream().sorted(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes)).toList();
        for (AiPlanItemDtoOut activity : ordered) {
            int maximumWalk = 0;
            Map<Integer, String> destinations = new HashMap<>();
            for (Integer memberId : activity.getParticipantVisitMemberIds()) {
                String previousNode = positions.getOrDefault(memberId, requireText(walking, "entranceNodeKey"));
                String nextNode = "RIDE".equals(activity.getActivityKind()) ? requireText(rides.get(activity.getRideId()), "nodeKey") : activity.getPlaceId() != null ? requireText(places.get(activity.getPlaceId()), "nodeKey") : previousNode;
                Integer minutes = previousNode.equals(nextNode) ? 0 : routes.get(walkingRouteKey(previousNode, nextNode));
                if (minutes == null) throw new ApiException("A participant has no verified walking route to the activity");
                maximumWalk = Math.max(maximumWalk, minutes);
                destinations.put(memberId, nextNode);
            }
            if (activity.getWalkingMinutes() != maximumWalk) throw new ApiException("Walking time does not match the longest verified participant route");
            positions.putAll(destinations);
        }
    }

    private Map<AiPlanItemDtoOut, String> validateSnapshotMeals(AiPlanDtoOut candidate, JsonNode mealPolicy, Map<Integer, List<AiPlanItemDtoOut>> byMember) {

        List<AiPlanItemDtoOut> foodActivities = new ArrayList<>();

        for (AiPlanItemDtoOut activity : candidate.getActivities()) {

            if ("MEAL".equals(activity.getBreakType()) || "SNACK".equals(activity.getBreakType())) {
                foodActivities.add(activity);
            }
        }

        foodActivities.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes));
        Set<AiPlanItemDtoOut> matched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Map<AiPlanItemDtoOut, String> breakKeys = new java.util.IdentityHashMap<>();

        for (JsonNode definition : mealPolicy.path("breaks")) {
            Set<Integer> participants = new HashSet<>(readPositiveIntegerArray(definition.path("participantVisitMemberIds"), "Meal participants", false));
            String breakType = requireText(definition, "breakType");
            String timing = requireText(definition, "timingPreference");
            AiPlanItemDtoOut match = null;

            for (AiPlanItemDtoOut activity : foodActivities) {

                if (matched.contains(activity) || !breakType.equals(activity.getBreakType()) || !participants.equals(new HashSet<>(activity.getParticipantVisitMemberIds()))) {
                    continue;
                }

                Integer selectedRestaurantId = definition.path("restaurantId").isNull() ? null : requirePositiveInteger(definition, "restaurantId");

                if (!Objects.equals(selectedRestaurantId, activity.getPlaceId())) {
                    continue;
                }

                if (activity.getRelativeStartMinutes() < requireNonNegativeInteger(definition, "earliestStartMinutes") || activity.getRelativeStartMinutes() > requireNonNegativeInteger(definition, "latestStartMinutes")) {
                    continue;
                }

                boolean timingMatches = true;

                for (Integer memberId : participants) {
                    List<AiPlanItemDtoOut> activities = byMember.get(memberId);
                    int index = activities.indexOf(activity);

                    boolean hasEarlierNonFoodActivity = activities.subList(0, index).stream().anyMatch(item -> !isFoodActivity(item));
                    boolean hasLaterNonFoodActivity = activities.subList(index + 1, activities.size()).stream().anyMatch(item -> !isFoodActivity(item));

                    if (("START".equals(timing) && hasEarlierNonFoodActivity) || ("END".equals(timing) && hasLaterNonFoodActivity) || ("MIDDLE".equals(timing) && (!hasEarlierNonFoodActivity || !hasLaterNonFoodActivity))) {
                        timingMatches = false;
                        break;
                    }
                }

                if (timingMatches) {
                    match = activity;
                    break;
                }
            }

            if (match == null) {
                throw new ApiException("The generated plan does not preserve the requested meal participants and timing");
            }

            matched.add(match);
            breakKeys.put(match, requireText(definition, "breakKey"));
        }

        if (matched.size() != foodActivities.size()) {
            throw new ApiException("The generated plan contains an unrequested food break");
        }

        return breakKeys;
    }

    private JsonNode normalizeMealPolicy(JsonNode policy, Set<Integer> memberIds, Integer durationMinutes) {

        if (policy == null || !policy.isObject() || !policy.path("ready").isBoolean() || !policy.path("ready").asBoolean() || !policy.path("breaks").isArray()) {
            throw new ApiException("An explicit saved meal policy is required; missing data is not a skipped meal choice");
        }

        String choice = requireText(policy, "choice");
        Map<String, List<String>> typesByChoice = Map.of("NONE", List.of(), "SKIPPED", List.of(), "DEFERRED", List.of(), "ONE_MEAL", List.of("MEAL"), "TWO_MEALS", List.of("MEAL", "MEAL"), "TWO_SNACKS", List.of("SNACK", "SNACK"), "SNACK", List.of("SNACK"), "MEAL_AND_SNACK", List.of("MEAL", "SNACK"));

        if (!typesByChoice.containsKey(choice)) {
            throw new ApiException("Meal policy choice is invalid");
        }

        Set<String> keys = new HashSet<>();
        List<Map<String, Object>> breaks = new ArrayList<>();
        Map<Integer, List<String>> memberTypes = new HashMap<>();

        for (JsonNode definition : policy.path("breaks")) {
            String key = requireText(definition, "breakKey");
            String type = requireText(definition, "breakType");
            String timing = requireText(definition, "timingPreference");
            List<Integer> participants = readPositiveIntegerArray(definition.path("participantVisitMemberIds"), "Meal participants", false);
            int earliest = requireNonNegativeInteger(definition, "earliestStartMinutes");
            int latest = requireNonNegativeInteger(definition, "latestStartMinutes");

            if (definition.get("restaurantId") == null) {
                throw new ApiException("Meal policy must explicitly identify its selected restaurant or return null");
            }

            Integer restaurantId = definition.path("restaurantId").isNull() ? null : requirePositiveInteger(definition, "restaurantId");

            if (!keys.add(key) || !Set.of("MEAL", "SNACK").contains(type) || !Set.of("START", "MIDDLE", "END").contains(timing) || !memberIds.containsAll(participants) || earliest > latest || latest >= durationMinutes) {
                throw new ApiException("Meal break policy contains an invalid definition");
            }

            if (requirePositiveInteger(definition, "durationMinutes") != ("MEAL".equals(type) ? 60 : 30)) {
                throw new ApiException("Meal policy duration must be 60 minutes for meals or 30 minutes for snacks");
            }

            for (Integer memberId : participants) {
                memberTypes.computeIfAbsent(memberId, id -> new ArrayList<>()).add(type);
            }

            Map<String, Object> row = new LinkedHashMap<>();

            row.put("breakKey", key);
            row.put("breakType", type);
            row.put("timingPreference", timing);
            row.put("durationMinutes", "MEAL".equals(type) ? 60 : 30);
            row.put("participantVisitMemberIds", participants.stream().sorted().toList());
            row.put("earliestStartMinutes", earliest);
            row.put("latestStartMinutes", latest);
            row.put("restaurantId", restaurantId);
            breaks.add(row);
        }

        List<String> expectedTypes = new ArrayList<>(typesByChoice.get(choice));
        expectedTypes.sort(String::compareTo);

        for (Integer memberId : memberIds) {
            List<String> actualTypes = new ArrayList<>(memberTypes.getOrDefault(memberId, List.of()));
            actualTypes.sort(String::compareTo);

            if (!expectedTypes.equals(actualTypes)) {
                throw new ApiException("Meal break definitions do not match the saved meal choice for every participant");
            }
        }

        breaks.sort(Comparator.comparing(row -> (String) row.get("breakKey")));

        return participantJsonMapper.valueToTree(Map.of("ready", true, "choice", choice, "breaks", breaks));
    }

    private boolean isSplitAllowedForInitialPlan(Visit visit) {

        if ("FAMILY".equals(visit.getTripType()) || "SOLO".equals(visit.getTripType())) {
            return false;
        }

        if (togetherModeValue.equals(visit.getTogetherMode())) {
            return false;
        }

        if (splitAllowedModeValue.equals(visit.getTogetherMode())) {
            return true;
        }

        throw new ApiException("Together mode does not match the configured planning contract");
    }

    private void validateWaitSnapshot(Map<String, Object> row) {

        if (row.get("planningWaitMinutes") == null) {
            return;
        }

        OffsetDateTime observed;

        try {
            observed = OffsetDateTime.parse((String) row.get("waitObservedAt"));
        } catch (DateTimeParseException exception) {
            throw new ApiException("Wait observation timestamp must contain a timezone offset");
        }

        if (observed.toInstant().isAfter(Instant.now().plusSeconds(30))) {
            throw new ApiException("Wait observation timestamp cannot be in the future");
        }

        if (!Boolean.TRUE.equals(row.get("waitIsEstimate")) && (maxLiveWaitAgeSeconds <= 0 || observed.toInstant().isBefore(Instant.now().minusSeconds(maxLiveWaitAgeSeconds)))) {
            throw new ApiException("Live wait observation is stale; supply a labeled planning estimate instead");
        }
    }

    private void mergeGenerationWarnings(AiPlanDtoOut candidate, JsonNode input) {
        Set<String> warnings = new LinkedHashSet<>();
        if (candidate.getWarnings() != null) warnings.addAll(candidate.getWarnings());
        Set<Integer> plannedRideIds = new HashSet<>();

        for (JsonNode member : input.path("members")) {
            int memberId = requirePositiveInteger(member, "visitMemberId");

            if (!member.path("ageKnown").isBoolean() || !member.path("ageKnown").asBoolean()) {
                warnings.add("عمر العضو " + memberId + " غير معروف؛ يلزم مراعاة الإشراف والتحقق من متطلبات الألعاب.");
            }

            if ("SKIPPED".equals(member.path("answerStatus").asString(""))) {
                warnings.add("العضو " + memberId + " تخطّى إجابات التخطيط؛ استُخدمت المعلومات المتاحة.");
            }
        }

        boolean includesPrayer = input.path("includePrayer").isBoolean() && input.path("includePrayer").asBoolean();
        boolean prayerTimesVerified = input.path("prayerTimesVerified").isBoolean() && input.path("prayerTimesVerified").asBoolean();

        if (includesPrayer && !prayerTimesVerified) {
            warnings.add("تُضاف بريكات الصلاة بعد تحديد وقت الوصول والتحقق من مواقيتها ليوم الزيارة.");
        }

        if (candidate.getActivities() != null) {
            for (AiPlanItemDtoOut activity : candidate.getActivities()) {
                if (!"RIDE".equals(activity.getActivityKind()) || activity.getRideId() == null) {
                    continue;
                }

                int rideId = activity.getRideId();
                plannedRideIds.add(rideId);

                if (activity.getParticipantVisitMemberIds() == null) {
                    continue;
                }

                for (Integer memberId : activity.getParticipantVisitMemberIds()) {
                    String eligibility = input.path("eligibilityByRideAndMember").path(String.valueOf(rideId)).path(String.valueOf(memberId)).asString("");

                    if ("UNKNOWN".equals(eligibility)) {
                        List<String> missing = new ArrayList<>();
                        for (JsonNode ride : input.path("rides")) if (ride.path("id").asInt() == rideId) for (JsonNode field : ride.path("missingFieldsByMember").path(String.valueOf(memberId))) {
                            String key = field.asString();
                            missing.add(key.startsWith("ride.") ? "توثيق شروط اللعبة" : Map.of("heightCm", "الطول", "weightKg", "الوزن", "birthDate", "تاريخ الميلاد", "healthData", "المعلومات الصحية", "healthConsent", "موافقة حفظ المعلومات الصحية", "healthNotesRequireReview", "مراجعة الملاحظة الصحية", "companionAssignment", "تحديد المرافق").getOrDefault(key, "مراجعة متطلبات الأهلية"));
                        }
                        warnings.add("أهلية العضو " + memberId + " للعبة " + rideId + " غير مؤكدة" + (missing.isEmpty() ? "" : "؛ المطلوب: " + String.join("، ", new LinkedHashSet<>(missing))) + ". الخطة مبدئية؛ تحقق من الشروط قبل الركوب.");
                    }
                }
            }
        }

        for (JsonNode ride : input.path("rides")) {
            int rideId = requirePositiveInteger(ride, "id");

            if (!plannedRideIds.contains(rideId)) {
                continue;
            }

            if (ride.path("availabilityIsEstimate").asBoolean() || "SIKKA_DEFAULT_AVAILABILITY".equals(ride.path("availabilitySource").asString())) warnings.add("إتاحة اللعبة " + rideId + " افتراضية للتخطيط، وليست تأكيد تشغيل مباشرًا من المشغّل.");
            if ("SIKKA_DEFAULT_ESTIMATE".equals(ride.path("timingSource").asString())) warnings.add("مدة اللعبة " + rideId + " ووقت الدخول والخروج يتضمنان قيمًا افتراضية تقديرية تحتاج مراجعة ميدانية.");

            if (!ride.path("planningWaitMinutes").isIntegralNumber()) {
                warnings.add("وقت انتظار اللعبة " + rideId + " غير متاح؛ لا يُفترض أنه صفر.");
            } else if (ride.path("waitIsEstimate").isBoolean() && ride.path("waitIsEstimate").asBoolean()) {
                warnings.add("SIKKA_DEFAULT_ESTIMATE".equals(ride.path("waitSource").asString()) ? "انتظار اللعبة " + rideId + " قيمة افتراضية للتخطيط (" + ride.path("planningWaitMinutes").asInt() + " دقيقة)، وليست قراءة انتظار فعلية." : "QUEUE_ANALYTICS_HISTORICAL_ESTIMATE".equals(ride.path("waitSource").asString()) ? "انتظار اللعبة " + rideId + " تقدير من بيانات التحليل السابقة حسب يوم الزيارة وساعاتها، وليس قراءة مباشرة مضمونة." : "انتظار اللعبة " + rideId + " تقدير تخطيطي من المصدر وليس قراءة مباشرة مضمونة ليوم الزيارة.");
            }
        }

        if ("DEFERRED".equals(input.path("mealPolicy").path("choice").asString(""))) {
            warnings.add("اختيار الأكل مؤجل؛ يظهر سؤاله يوم الزيارة وقد يؤثر اختياره على الجدول.");
        }

        candidate.setWarnings(new ArrayList<>(warnings));
    }

    private Map<Integer, JsonNode> indexSnapshotResources(JsonNode nodes) {

        if (nodes == null || !nodes.isArray()) {
            throw new ApiException("Planning resource catalog must be an array");
        }

        Map<Integer, JsonNode> results = new HashMap<>();

        for (JsonNode node : nodes) {

            if (results.put(requirePositiveInteger(node, "id"), node) != null) {
                throw new ApiException("Duplicate planning resource ID");
            }
        }

        return results;
    }

    private String walkingRouteKey(String from, String to) {
        return writeGenerationJson(List.of(from, to));
    }

    private boolean isFoodActivity(AiPlanItemDtoOut activity) {
        return "MEAL".equals(activity.getBreakType()) || "SNACK".equals(activity.getBreakType());
    }

    private JsonNode generationSemanticInput(String inputJson) {

        JsonNode snapshot = readGenerationJson(inputJson).deepCopy();
        if (snapshot instanceof ObjectNode object) object.remove("variationSeed");

        for (JsonNode ride : snapshot.path("rides")) {
            ((ObjectNode) ride).remove("waitObservedAt");
        }

        return snapshot;
    }

    private int requirePositiveInteger(JsonNode node, String field) {

        int value = requireNonNegativeInteger(node, field);

        if (value <= 0) {
            throw new ApiException("A positive value is required for " + field);
        }

        return value;
    }

    private int requireNonNegativeInteger(JsonNode node, String field) {

        JsonNode value = node == null ? null : node.get(field);

        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new ApiException("A non-negative integer is required for " + field);
        }

        return value.intValue();
    }

    private String requireText(JsonNode node, String field) {

        JsonNode value = node == null ? null : node.get(field);

        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new ApiException("A non-blank string is required for " + field);
        }

        return value.asString();
    }

    private List<String> readTextArray(JsonNode node, String label) {

        if (node == null || !node.isArray()) {
            throw new ApiException(label + " must be an array");
        }

        Set<String> results = new java.util.TreeSet<>();

        for (JsonNode value : node) {

            if (!value.isString() || value.asString().isBlank() || !results.add(value.asString())) {
                throw new ApiException(label + " must contain unique non-blank strings");
            }
        }

        return new ArrayList<>(results);
    }

    private List<Integer> readPositiveIntegerArray(JsonNode node, String label, boolean allowEmpty) {

        if (node == null || !node.isArray() || (!allowEmpty && node.isEmpty())) {
            throw new ApiException(label + " must be an explicit array of participant or ride IDs");
        }

        Set<Integer> values = new java.util.TreeSet<>();

        for (JsonNode value : node) {

            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0 || !values.add(value.intValue())) {
                throw new ApiException(label + " must contain unique positive integer IDs");
            }
        }

        return new ArrayList<>(values);
    }

    private JsonNode readGenerationJson(String json) {

        try {
            return participantJsonMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new ApiException("Generation snapshot contains invalid JSON");
        }
    }

    private String writeGenerationJson(Object value) {

        try {
            return participantJsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException("Unable to serialize generation data");
        }
    }

    private record InitialGenerationStart(Integer historyId, GenerateVisitPlansDtoOut previousResult) {
    }

    private record GenerationSnapshot(String inputJson, Integer durationMinutes, String tripType, Set<Integer> memberIds, Map<Integer, Integer> ages, Map<Integer, Map<Integer, String>> eligibility, Map<Integer, String> priorities, Set<Integer> pinnedRideIds, Set<Integer> rateablePlaceIds) {
    }

    private void validateInitialMemberAnswers(Visit visit, List<Map<String, Object>> members) {

        boolean everyMemberAnswers = "SOLO".equals(visit.getTripType()) || isSplitAllowedForInitialPlan(visit);
        VisitMember organizerMember = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visit.getId(), visit.getOrganizer().getId());

        if (organizerMember == null || !"GOING".equals(organizerMember.getRsvpStatus())) {
            throw new ApiException("The visit organizer must be a GOING participant");
        }

        Set<String> identities = new HashSet<>();

        for (VisitMember member : visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visit.getId(), "GOING")) {
            String identity = member.getVisitorProfile() != null ? "ACCOUNT:" + member.getVisitorProfile().getAccount().getId() : "FAMILY:" + member.getFamilyMember().getId();

            if (!identities.add(identity)) {
                throw new ApiException("The same person cannot be planned as multiple visit members");
            }
        }

        for (Map<String, Object> member : members) {
            boolean mustAnswer = everyMemberAnswers || organizerMember.getId().equals(member.get("visitMemberId"));

            if (mustAnswer && "PENDING".equals(member.get("answerStatus"))) {
                throw new ApiException("Required planning answers must be submitted or explicitly skipped");
            }
        }
    }

    private void validateVisitForRegeneration(Visit visit) {

        if (visit.getStartedAt() != null || (!"PLANNING".equals(visit.getStatus()) && !"READY".equals(visit.getStatus()))) {
            throw new ApiException("Plans can only be regenerated before the visit starts");
        }

        if (visit.getVisitDate() == null || visit.getVisitDate().isBefore(LocalDate.now(ZoneId.of("Asia/Riyadh")))) {
            throw new ApiException("Visit date must not be in the past");
        }

        if (!visitPlanRepository.existsByVisit_IdAndPlanKind(visit.getId(), "GROUP")) {
            throw new ApiException("Generate a group plan before requesting regeneration");
        }
    }

    private PlanHistory lockRegenerationRequest(Integer accountId, Integer visitId, Integer historyId) {

        PlanHistory history = planHistoryRepository.findPlanHistoryByIdForUpdate(historyId);

        if (history == null || !history.getAccount().getId().equals(accountId) || !history.getVisit().getId().equals(visitId) || !"REGENERATE".equals(history.getOperationType())) {
            throw new ApiException("Regeneration request not found for this account and visit");
        }

        return history;
    }

    private RegenerationStart reserveRegeneration(Integer accountId, Integer visitId, String requestKey) {

        Visit visit = lockGenerationOwnerAndVisit(accountId, visitId);
        PlanHistory existing = findExistingGenerationRequest(accountId, visitId, requestKey, "REGENERATE");

        if (existing != null) {
            return new RegenerationStart(existing.getId(), convertGenerationRequestToDtoOut(existing));
        }

        validateVisitForRegeneration(visit);

        for (PlanHistory history : planHistoryRepository.findAllByAccount_IdOrderByStartedAtDescIdDesc(accountId)) {

            if (history.getVisit().getId().equals(visitId) && "PROCESSING".equals(history.getStatus())) {
                throw new ApiException("Another generation request is already processing for this visit");
            }
        }

        buildVisitPlanningContext(visit);
        validateInitialMemberAnswers(visit, buildGoingMemberPlanningContext(visit));

        Integer historyId = paymentOrderService.reserveEntitlement(accountId, visitId, requestKey, "REGENERATE");
        PlanHistory history = lockRegenerationRequest(accountId, visitId, historyId);

        if (!requestKey.equals(history.getRequestKey()) || !"PROCESSING".equals(history.getStatus()) || !Boolean.FALSE.equals(history.getCountsTowardQuota())) {
            throw new ApiException("Entitlement reservation returned an invalid regeneration request");
        }

        if (!"FREE".equals(history.getBillingMode()) && !"PAID".equals(history.getBillingMode())) {
            throw new ApiException("Entitlement reservation returned an invalid billing mode");
        }


        visit.setMembersLocked(true);
        visitRepository.save(visit);

        return new RegenerationStart(historyId, null);
    }

    private record RegenerationStart(Integer historyId, GenerateVisitPlansDtoOut previousResult) {
    }

    private GenerationSnapshot prepareRegenerationSnapshot(Integer accountId, Integer visitId, Integer historyId) {

        Visit visit = lockGenerationOwnerAndVisit(accountId, visitId);
        PlanHistory history = lockRegenerationRequest(accountId, visitId, historyId);

        validateVisitForRegeneration(visit);

        if (!"PROCESSING".equals(history.getStatus()) || !Boolean.TRUE.equals(visit.getMembersLocked())) {
            throw new ApiException("Regeneration request is no longer ready for planning");
        }

        if (history.getPlanningInputSnapshot() != null) {
            throw new ApiException("This regeneration request already has a prepared input; do not repeat its AI call");
        }

        GenerationSnapshot snapshot = collectInitialGenerationSnapshot(accountId, visit);

        ObjectNode varied = (ObjectNode) readGenerationJson(snapshot.inputJson());
        varied.put("variationSeed", historyId);
        snapshot = new GenerationSnapshot(writeGenerationJson(varied), snapshot.durationMinutes(), snapshot.tripType(), snapshot.memberIds(), snapshot.ages(), snapshot.eligibility(), snapshot.priorities(), snapshot.pinnedRideIds(), snapshot.rateablePlaceIds());
        history.setPlanningInputSnapshot(snapshot.inputJson());
        planHistoryRepository.save(history);

        return snapshot;
    }

    private GenerateVisitPlansDtoOut failRegeneration(Integer accountId, Integer visitId, Integer historyId, String reason, AiPlanDtoOut candidate) {

        lockGenerationOwnerAndVisitForCleanup(accountId, visitId);
        PlanHistory history = lockRegenerationRequest(accountId, visitId, historyId);

        if ("SUCCEEDED".equals(history.getStatus()) || "FAILED".equals(history.getStatus())) {
            return convertGenerationRequestToDtoOut(history);
        }

        if (!"PROCESSING".equals(history.getStatus()) || !Boolean.FALSE.equals(history.getCountsTowardQuota())) {
            throw new ApiException("The regeneration reservation requires reconciliation");
        }

        paymentOrderService.releaseEntitlement(accountId, historyId);

        String failureReason = reason == null || reason.isBlank() ? "Unable to regenerate the visit plan" : reason;

        history.setStatus("FAILED");
        history.setCountsTowardQuota(false);
        history.setCompletedAt(LocalDateTime.now(ZoneId.of("Asia/Riyadh")));
        history.setFailureReason(failureReason.length() <= 500 ? failureReason : failureReason.substring(0, 500));

        if (candidate != null) {
            history.setGenerationResultSnapshot(writeGenerationJson(candidate));
        }

        planHistoryRepository.save(history);

        return convertGenerationRequestToDtoOut(history);
    }

    private GenerationSnapshot validateRegenerationCandidate(Integer accountId, Visit visit, PlanHistory history, GenerationSnapshot prepared, AiPlanDtoOut candidate) {

        validateVisitForRegeneration(visit);

        if (!"PROCESSING".equals(history.getStatus()) || !Boolean.TRUE.equals(visit.getMembersLocked())) {
            throw new ApiException("Regeneration state changed before the plan could be saved");
        }

        if (candidate == null || !Boolean.TRUE.equals(candidate.getFeasible()) || candidate.getActivities() == null || candidate.getActivities().isEmpty()) {
            throw new ApiException("A feasible replacement plan with activities is required");
        }

        if (prepared == null || history.getPlanningInputSnapshot() == null || !readGenerationJson(history.getPlanningInputSnapshot()).equals(readGenerationJson(prepared.inputJson()))) {
            throw new ApiException("Regeneration input does not match the saved request snapshot");
        }

        GenerationSnapshot current = collectInitialGenerationSnapshot(visit.getOrganizer().getId(), visit);

        if (!generationSemanticInput(prepared.inputJson()).equals(generationSemanticInput(current.inputJson()))) {
            throw new ApiException("Planning data changed while regenerating the plan; a new request is required");
        }

        aiPlanService.validatePlanCandidateTiming(candidate, current.durationMinutes(), current.memberIds(), current.tripType());
        aiPlanService.validatePlanCandidateSupervision(candidate, current.ages());
        aiPlanService.validatePlanCandidateActivityKinds(candidate);
        aiPlanService.validatePlanCandidateEligibility(candidate, current.eligibility());
        validateInitialCandidateAgainstSnapshot(candidate, current);
        validateCandidateResources(candidate, visit.getVisitDate());

        return current;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GenerateVisitPlansDtoOut regeneratePlan(Integer accountId, Integer visitId, GeneratePlanDtoIn dto) {

        if (dto == null || dto.getRequestKey() == null || dto.getRequestKey().isBlank() || dto.getRequestKey().length() > 80) {
            throw new ApiException("A request key between 1 and 80 characters is required");
        }

        RegenerationStart start = runGenerationTransaction(() -> reserveRegeneration(accountId, visitId, dto.getRequestKey()));

        if (start.previousResult() != null) {
            return start.previousResult();
        }

        try {
            GenerationSnapshot prepared = runGenerationTransaction(() -> prepareRegenerationSnapshot(accountId, visitId, start.historyId()));

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new ApiException("The OpenAI call must run outside the database transaction");
            }

            AiPlanDtoOut candidate = selectInitialPlanWithExpectedPrayer(readGenerationJson(prepared.inputJson()));
            resolveExcludedRideNames(candidate);

            mergeGenerationWarnings(candidate, readGenerationJson(prepared.inputJson()));

            if (!Boolean.TRUE.equals(candidate.getFeasible())) {
                return runGenerationTransaction(() -> failRegeneration(accountId, visitId, start.historyId(), "تعذر ترتيب الخطة البديلة ضمن البيانات والمدة المتاحة؛ بقيت الخطة السابقة.", candidate));
            }

            return runGenerationTransaction(() -> {

                Visit visit = lockGenerationOwnerAndVisit(accountId, visitId);
                PlanHistory history = lockRegenerationRequest(accountId, visitId, start.historyId());

                if ("SUCCEEDED".equals(history.getStatus())) {
                    return convertGenerationRequestToDtoOut(history);
                }

                GenerationSnapshot current = validateRegenerationCandidate(accountId, visit, history, prepared, candidate);

                List<VisitPlan> previousPlans = visitPlanRepository.findAllByVisit_Id(visitId);
                previousPlans.sort(Comparator.comparing(VisitPlan::getId));

                for (VisitPlan previous : previousPlans) {

                    VisitPlan lockedPlan = visitPlanRepository.findVisitPlanByIdForUpdate(previous.getId());

                    if (lockedPlan == null || !lockedPlan.getVisit().getId().equals(visitId)) {
                        throw new ApiException("A previous plan changed before regeneration could be saved");
                    }

                    if (lockedPlan.getProposedItems() != null || lockedPlan.getPendingScope() != null) {
                        throw new ApiException("Resolve pending proposals before regenerating the plan");
                    }
                }

                if (!Boolean.FALSE.equals(history.getCountsTowardQuota())) {
                    throw new ApiException("The regeneration reservation requires reconciliation");
                }

                if (!"FREE".equals(history.getBillingMode()) && !"PAID".equals(history.getBillingMode())) {
                    throw new ApiException("Invalid regeneration billing mode");
                }

                VisitPlan groupPlan = saveGeneratedGroupActivities(visit, history, candidate, current.priorities(), current.pinnedRideIds(), current.rateablePlaceIds(), current.inputJson());

                List<VisitMember> members = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING");

                if (!isFamilyVisit(visit)) {

                    for (VisitMember member : members) {

                        VisitPlan individualPlan = new VisitPlan();

                        individualPlan.setVisit(visit);
                        individualPlan.setPlanKind("INDIVIDUAL");
                        individualPlan.setOwnerVisitMember(member);
                        individualPlan.setGeneratedByHistory(history);
                        individualPlan.setTimingMode("RELATIVE");
                        individualPlan.setIsActive(false);

                        visitPlanRepository.save(individualPlan);
                    }
                }

                boolean keepApproved = previousPlans.stream().anyMatch(previous -> Boolean.TRUE.equals(previous.getIsActive()) && previous.getPlanApproval() != null && "APPROVED".equals(previous.getPlanApproval().getStatus()));
                LocalDateTime completedAt = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));

                for (VisitPlan previous : previousPlans) {

                    if (!keepApproved) previous.setIsActive(false);

                    PlanApproval approval = previous.getPlanApproval();

                    if (approval != null && "PENDING".equals(approval.getStatus())) {
                        approval.setStatus("REJECTED");
                        approval.setApprovedByAccount(visit.getOrganizer());
                        approval.setRespondedAt(completedAt);
                    }
                }

                visitPlanRepository.saveAll(previousPlans);

                if (!keepApproved) {
                    visit.setStatus("PLANNING");
                    visit.setPlannedStartTime(null);
                    visit.setPlannedEndTime(null);
                } else {
                    PlanApproval approval = new PlanApproval();
                    approval.setVisitPlan(groupPlan);
                    approval.setApprovalScope("GROUP");
                    approval.setRequestedByAccount(visit.getOrganizer());
                    approval.setRequiredApproverAccount(visit.getOrganizer());
                    approval.setStatus("PENDING");
                    planApprovalRepository.save(approval);
                    groupPlan.setPlanApproval(approval);
                    visitPlanRepository.save(groupPlan);
                    notificationEventService.notifyPlanApprovalRequested(approval);
                }

                visitRepository.save(visit);

                paymentOrderService.consumeEntitlement(accountId, history.getId());

                history.setStatus("SUCCEEDED");
                history.setCountsTowardQuota(true);
                history.setCompletedAt(completedAt);
                history.setFailureReason(null);
                history.setGenerationResultSnapshot(writeGenerationJson(candidate));

                planHistoryRepository.save(history);
                notificationEventService.notifyPlanGenerated(history);
                planHistoryRepository.flush();

                return convertGenerationRequestToDtoOut(history);
            });

        } catch (RuntimeException failure) {

            String reason = failure instanceof ApiException && failure.getMessage() != null && !failure.getMessage().isBlank() ? failure.getMessage() : "تعذر إكمال إعادة التوليد. تحقق من حالة الطلب قبل إرسال محاولة جديدة.";

            try {
                return runGenerationTransaction(() -> failRegeneration(accountId, visitId, start.historyId(), reason, null));
            } catch (RuntimeException cleanupFailure) {
                cleanupFailure.addSuppressed(failure);
                throw new ApiException("Regeneration outcome could not be confirmed. Check this request key before sending a new request");
            }
        }
    }

    private boolean isTogetherGroupProposalAllowed(Visit visit) {
        return "FRIENDS".equals(visit.getTripType()) && togetherModeValue.equals(visit.getTogetherMode());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReplanProposalDtoOut suggestReplan(Integer accountId, Integer visitId, SuggestReplanDtoIn dto) {

        if (accountId == null || accountId <= 0 || visitId == null || visitId <= 0) {
            throw new ApiException("Positive account and visit IDs are required");
        }

        if (dto == null || dto.getReason() == null || dto.getReason().isBlank() || dto.getReason().length() > 500) {
            throw new ApiException("A replanning reason between 1 and 500 characters is required");
        }

        String reason = dto.getReason().trim();
        boolean replaceUnavailableRides = Boolean.TRUE.equals(dto.getReplaceUnavailableRides());
        Integer sourceDelayNotificationId = dto.getSourceDelayNotificationId();
        String sourceDelayReason = dto.getSourceDelayReason() == null || dto.getSourceDelayReason().isBlank() ? null : dto.getSourceDelayReason().trim();

        if (sourceDelayNotificationId != null && (sourceDelayNotificationId <= 0 || (dto.getSourceDelayReason() != null && dto.getSourceDelayReason().length() > 500))) {
            throw new ApiException("Invalid source delay response");
        }

        List<Integer> requestedIds = dto.getAffectedMemberIds() == null ? null : new ArrayList<>(dto.getAffectedMemberIds());

        if (requestedIds != null && (requestedIds.isEmpty() || requestedIds.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(requestedIds).size() != requestedIds.size())) {
            throw new ApiException("Affected member IDs must be positive, non-empty and unique");
        }

        record ReplanInput(Integer planId, Integer requesterId, String scope, List<Integer> affectedIds, LocalDateTime startsAt, GenerationSnapshot planning, List<PlanItemDtoOut> items, List<AiPlanItemDtoOut> originalActivities, Set<Integer> editableIndexes, String executionJson, String aiInput, String delaySnapshot, String executionExtras) {
        }

        ReplanInput prepared = runGenerationTransaction(() -> {

            validateActiveAccount(accountId);
            Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

            if (visit == null || !"IN_PROGRESS".equals(visit.getStatus()) || visit.getStartedAt() == null || visit.getPlannedStartTime() == null) {
                throw new ApiException("Replanning requires a started visit with scheduled activity times");
            }

            VisitMember requester = getRequiredAccountMember(accountId, visitId);

            if (!"GOING".equals(requester.getRsvpStatus())) {
                throw new ApiException("Only GOING members can request replanning");
            }

            VisitPlan active = findActiveGroupPlan(visitId);

            if (active == null) {
                throw new ApiException("An active group plan is required");
            }

            VisitPlan plan = visitPlanRepository.findVisitPlanByIdForUpdate(active.getId());

            if (plan == null || !Boolean.TRUE.equals(plan.getIsActive()) || !"SCHEDULED".equals(plan.getTimingMode())) {
                throw new ApiException("An active scheduled group plan is required");
            }

            if (plan.getProposedItems() != null || plan.getPendingScope() != null || plan.getProposalBaseSnapshot() != null) {
                throw new ApiException("Resolve the existing proposal before requesting another change");
            }

            if (plan.getPlanApproval() == null || !"APPROVED".equals(plan.getPlanApproval().getStatus())) {
                throw new ApiException("The active plan must have an approved baseline");
            }

            DelayProposalSource delaySource = lockAndValidateDelayProposalSource(sourceDelayNotificationId, accountId, visitId, visit, plan);
            String delaySnapshot = delaySource.snapshot();

            GenerationSnapshot planning = collectInitialGenerationSnapshot(visit.getOrganizer().getId(), visit);
            boolean organizer = visit.getOrganizer().getId().equals(accountId);
            Set<Integer> selected = requestedIds == null ? new HashSet<>(organizer ? planning.memberIds() : Set.of(requester.getId())) : new HashSet<>(requestedIds);

            if (!planning.memberIds().containsAll(selected)) {
                throw new ApiException("All affected members must be GOING members of this visit");
            }

            if (!organizer && (selected.size() != 1 || !selected.contains(requester.getId()))) {
                throw new ApiException("A member can only request changes within their own scope");
            }

            boolean sharedTogether = isTogetherGroupProposalAllowed(visit);
            boolean individual = !sharedTogether && !isFamilyVisit(visit) && selected.size() == 1 && selected.contains(requester.getId());

            if (!individual && !organizer && !sharedTogether) {
                throw new ApiException("Only the organizer can request a group change");
            }

            if ((individual || sharedTogether && !organizer) && (planning.ages().get(requester.getId()) == null || planning.ages().get(requester.getId()) < 18)) {
                throw new ApiException("Changes involving minors require a supervised group proposal from the organizer");
            }

            String scope = individual ? "INDIVIDUAL" : "GROUP";
            List<Integer> affected = new ArrayList<>(individual ? Set.of(requester.getId()) : planning.memberIds());
            affected.sort(Integer::compareTo);

            LocalDateTime startsAt = visit.getVisitDate().atTime(visit.getPlannedStartTime());
            long elapsedSeconds = java.time.Duration.between(startsAt, LocalDateTime.now(ZoneId.of("Asia/Riyadh"))).getSeconds();

            if (elapsedSeconds >= (long) planning.durationMinutes() * 60) {
                throw new ApiException("The remaining visit time is unavailable; review the visit duration");
            }

            int currentMinute = Math.toIntExact((Math.max(0, elapsedSeconds) + 59) / 60);
            List<PlanItemDtoOut> items = new ArrayList<>();
            Set<Integer> itemIds = new HashSet<>();
            Map<Integer, Integer> executionEnds = new LinkedHashMap<>();

            for (PlanItem item : getSortedPlanItems(plan)) {
                items.add(convertPlanItemToDtoOut(item));
                itemIds.add(item.getId());

                if (item.getExecutionReleasedAt() != null && item.getStartsAt() != null && !item.getExecutionReleasedAt().isBefore(item.getStartsAt()) && item.getExecutionReleasedAt().isBefore(item.getEndsAt())) {
                    java.time.Duration releaseOffset = java.time.Duration.between(startsAt, item.getExecutionReleasedAt());
                    long releasedSeconds = releaseOffset.getSeconds() + (releaseOffset.getNano() > 0 ? 1 : 0);
                    executionEnds.put(item.getId(), Math.toIntExact(Math.floorDiv(releasedSeconds + 59, 60)));
                }
            }

            if (items.isEmpty()) {
                throw new ApiException("An empty plan cannot be replanned");
            }

            List<Object> execution = new ArrayList<>();
            Set<Integer> startedIds = new HashSet<>();
            List<Integer> memberIds = new ArrayList<>(planning.memberIds());
            memberIds.sort(Integer::compareTo);

            for (Integer memberId : memberIds) {
                startedIds.addAll(appendExecutionSnapshotRows(execution, memberId, itemIds, activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(memberId), visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(memberId)));
            }

            String executionJson = writeGenerationJson(Map.of("planId", plan.getId(), "items", items, "execution", execution));
            List<AiPlanItemDtoOut> originals = new ArrayList<>();
            Set<Integer> editable = new HashSet<>();

            for (int index = 0; index < items.size(); index++) {

                PlanItemDtoOut item = items.get(index);
                validateActivityTiming("SCHEDULED", item.getRelativeStartMinutes(), item.getRelativeEndMinutes(), item.getStartsAt(), item.getEndsAt());

                if (item.getRelativeStartMinutes() == null || item.getRelativeEndMinutes() == null || !startsAt.plusMinutes(item.getRelativeStartMinutes()).equals(item.getStartsAt()) || !startsAt.plusMinutes(item.getRelativeEndMinutes()).equals(item.getEndsAt())) {
                    throw new ApiException("Scheduled activities must match their relative offsets before replanning");
                }

                List<Integer> participants = new ArrayList<>(parseActivityParticipantIds(item.getParticipantVisitMemberIds()));
                participants.sort(Integer::compareTo);

                AiPlanItemDtoOut original = convertPlanItemToAiActivity(item, participants);
                original.setExecutionEndMinutes(executionEnds.get(item.getId()));
                originals.add(original);

                boolean fixedCommitment = Boolean.TRUE.equals(item.getPinned()) || "MEETING_POINT".equals(item.getActivityKind()) || "PRAYER".equals(item.getBreakType()) || "MEAL".equals(item.getBreakType()) || "SNACK".equals(item.getBreakType());
                boolean withinScope = !individual || (participants.size() == 1 && participants.contains(requester.getId()));

                if (!fixedCommitment && !startedIds.contains(item.getId()) && withinScope) {
                    editable.add(index);
                }
            }


            Set<Integer> replaceable = new HashSet<>();
            Map<Integer, JsonNode> availableRides = indexSnapshotResources(readGenerationJson(planning.inputJson()).path("rides"));
            Map<Integer, JsonNode> canonicalRideNodes = new LinkedHashMap<>();
            JsonNode historicalInput = plan.getGeneratedByHistory() == null || plan.getGeneratedByHistory().getPlanningInputSnapshot() == null ? null : readGenerationJson(plan.getGeneratedByHistory().getPlanningInputSnapshot());
            Map<Integer, JsonNode> historicalRides = historicalInput == null ? Map.of() : indexSnapshotResources(historicalInput.path("rides"));

            for (int index = 0; index < items.size(); index++) {

                PlanItemDtoOut item = items.get(index);

                if (!"RIDE".equals(item.getActivityKind())) {
                    continue;
                }

                JsonNode resource = availableRides.get(item.getRideId());

                if (resource == null && replaceUnavailableRides) {
                    resource = historicalRides.get(item.getRideId());

                    if (resource == null) {
                        throw new ApiException("The original ride has no verified planning node; its replacement cannot be routed safely");
                    }
                }

                if (resource != null && replaceUnavailableRides) {
                    canonicalRideNodes.put(item.getRideId(), participantJsonMapper.valueToTree(Map.of("id", item.getRideId(), "nodeKey", requireText(resource, "nodeKey"))));
                }

                if (replaceUnavailableRides && editable.contains(index) && !availableRides.containsKey(item.getRideId())) {

                    Ride unavailable = rideRepository.findRideById(item.getRideId());

                    if (unavailable == null || PlanningAvailability.canPlanRide(unavailable, visit.getVisitDate())) {
                        throw new ApiException("Only a confirmed closed or unavailable unstarted ride can be replaced");
                    }

                    replaceable.add(index);
                }
            }

            if (replaceUnavailableRides && replaceable.isEmpty()) {
                throw new ApiException("No unavailable unstarted ride can be replaced within this scope; pinned or shared activities may require a group review");
            }

            String executionExtras = writeGenerationJson(Map.of("replaceUnavailableRides", replaceUnavailableRides, "replaceableIndexes", replaceable.stream().sorted().toList(), "canonicalRideNodes", canonicalRideNodes.values(), "executionEndsByItemId", executionEnds));

            ObjectNode input = (ObjectNode) readGenerationJson(planning.inputJson());
            input.put("operationType", "REPLAN");
            input.set("planningEligibilityPolicy", participantJsonMapper.valueToTree(Map.of("existingEditableRideUnknown", "ALLOW_PROVISIONAL_WITH_WARNING", "replacementRide", "REQUIRE_CONFIRMED_ELIGIBLE", "missingEligibility", "FORBID", "notEligible", "FORBID", "estimatedWait", "USE_SUPPLIED_KNOWN_ESTIMATE")));
            input.put("reason", reason);
            input.set("executionRules", readGenerationJson(executionExtras));
            input.put("approvalScope", scope);
            input.put("currentRelativeMinute", currentMinute);
            input.put("deadlineRelativeMinute", planning.durationMinutes());
            input.set("affectedMemberIds", participantJsonMapper.valueToTree(affected));
            input.set("executionContext", participantJsonMapper.valueToTree(Map.of("activities", originals, "planItemIds", items.stream().map(PlanItemDtoOut::getId).toList(), "editableIndexes", editable.stream().sorted().toList(), "activityOrderMustStayUnchanged", true, "walkingAndActivityMinutesMustStayUnchanged", !replaceUnavailableRides, "returnEntireActivityList", true)));

            return new ReplanInput(plan.getId(), requester.getId(), scope, affected, startsAt, planning, items, originals, editable, executionJson, writeGenerationJson(input), delaySnapshot, executionExtras);
        });

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ApiException("The OpenAI call must run outside the database transaction");
        }

        JsonNode timingInput = readGenerationJson(prepared.aiInput());
        boolean directTiming = !replaceUnavailableRides && timingInput.path("allowSplit").isBoolean() && !timingInput.path("allowSplit").asBoolean();
        boolean editableSeen = false;
        for (int index = 0; index < prepared.originalActivities().size(); index++) {
            AiPlanItemDtoOut original = prepared.originalActivities().get(index);
            boolean editable = prepared.editableIndexes().contains(index);
            if (!"RIDE".equals(original.getActivityKind()) || original.getPlaceId() != null || original.getBreakType() != null || !new HashSet<>(original.getParticipantVisitMemberIds()).equals(prepared.planning().memberIds()) || (editableSeen && !editable)) {
                directTiming = false;
            }
            editableSeen = editableSeen || editable;
        }

        AiPlanDtoOut candidate;
        if (directTiming) {
            Map<Integer, JsonNode> timingRides = indexSnapshotResources(timingInput.path("rides"));
            List<AiPlanItemDtoOut> timedActivities = new ArrayList<>();
            long availableMinute = 0;
            int currentMinute = requireNonNegativeInteger(timingInput, "currentRelativeMinute");
            for (int index = 0; index < prepared.originalActivities().size(); index++) {
                AiPlanItemDtoOut original = prepared.originalActivities().get(index);
                AiPlanItemDtoOut timed = new AiPlanItemDtoOut();
                timed.setActivityKind(original.getActivityKind());
                timed.setRideId(original.getRideId());
                timed.setPlaceId(original.getPlaceId());
                timed.setBreakType(original.getBreakType());
                timed.setParticipantVisitMemberIds(new ArrayList<>(original.getParticipantVisitMemberIds()));
                timed.setSubgroupKey(original.getSubgroupKey());
                timed.setWalkingMinutes(original.getWalkingMinutes());
                timed.setWaitingMinutes(original.getWaitingMinutes());
                timed.setActivityMinutes(original.getActivityMinutes());
                timed.setRelativeStartMinutes(original.getRelativeStartMinutes());
                timed.setRelativeEndMinutes(original.getRelativeEndMinutes());
                timed.setExecutionEndMinutes(original.getExecutionEndMinutes());
                if (prepared.editableIndexes().contains(index)) {
                    JsonNode ride = timingRides.get(original.getRideId());
                    if (ride == null || !"OPEN".equals(ride.path("status").asString())) {
                        throw new ApiException("An editable ride is unavailable; request an authorized replacement or review the plan");
                    }
                    int waiting = requireNonNegativeInteger(ride, "planningWaitMinutes");
                    long begins = Math.max(availableMinute, currentMinute);
                    long ends = begins + timed.getWalkingMinutes() + waiting + timed.getActivityMinutes();
                    if (ends > prepared.planning().durationMinutes()) {
                        throw new ApiException("The remaining activities exceed the visit duration; review the duration or requested activities");
                    }
                    timed.setWaitingMinutes(waiting);
                    timed.setRelativeStartMinutes(Math.toIntExact(begins));
                    timed.setRelativeEndMinutes(Math.toIntExact(ends));
                }
                availableMinute = Math.max(availableMinute, timed.getExecutionEndMinutes() == null ? timed.getRelativeEndMinutes() : timed.getExecutionEndMinutes());
                timedActivities.add(timed);
            }
            candidate = new AiPlanDtoOut();
            candidate.setFeasible(true);
            candidate.setActivities(timedActivities);
            candidate.setWarnings(new ArrayList<>());
        } else if (prepared.editableIndexes().isEmpty()) {
            candidate = new AiPlanDtoOut();
            candidate.setFeasible(true);
            candidate.setActivities(new ArrayList<>(prepared.originalActivities()));
            candidate.setWarnings(new ArrayList<>());
        } else {
            AiPlanDtoOut generatedCandidate = aiPlanService.generatePlanCandidate(prepared.aiInput());

            if (!Boolean.TRUE.equals(generatedCandidate.getFeasible()) && !replaceUnavailableRides) {
                ObjectNode reviewInput = (ObjectNode) readGenerationJson(prepared.aiInput());
                reviewInput.put("validationFeedback", "Review infeasibility once for timing-only REPLAN. Existing approved rides with explicit UNKNOWN eligibility may remain provisionally scheduled with warnings; do not reject solely because confirmation is absent. Use supplied known planning wait estimates even when estimated. Preserve every frozen activity and every actual prohibition, missing-data restriction, editable index, walking duration, activity duration, order and deadline. If a real conflict remains return feasible=false with the concrete conflict. Do not force a feasible result or invent data.");
                reviewInput.set("previousRejectedCandidate", participantJsonMapper.valueToTree(generatedCandidate));
                generatedCandidate = aiPlanService.generatePlanCandidate(writeGenerationJson(reviewInput));
            }

            candidate = generatedCandidate;
        }

        if (!Boolean.TRUE.equals(candidate.getFeasible())) {
            throw new ApiException(candidate.getInfeasibilityReason() == null || candidate.getInfeasibilityReason().isBlank() ? "The remaining activities cannot fit without changing fixed commitments or your choices" : candidate.getInfeasibilityReason());
        }

        if (candidate.getActivities().size() != prepared.originalActivities().size()) {
            throw new ApiException("A proposal must preserve all existing activities");
        }

        JsonNode executionRules = readGenerationJson(prepared.executionExtras());
        Set<Integer> replacementIndexes = new HashSet<>();
        executionRules.path("replaceableIndexes").forEach(index -> replacementIndexes.add(index.intValue()));

        int substantialChanges = replacementIndexes.size();
        if (substantialChanges > 1 && substantialChanges * 4 > prepared.editableIndexes().size()) {
            throw new ApiException("A replan must change no more than 25 percent of remaining editable activities; keep the approved schedule or request a separate review");
        }

        for (int index = 0; index < candidate.getActivities().size(); index++) {
            candidate.getActivities().get(index).setExecutionEndMinutes(prepared.originalActivities().get(index).getExecutionEndMinutes());
        }

        aiPlanService.validatePlanCandidateTiming(candidate, prepared.planning().durationMinutes(), prepared.planning().memberIds(), prepared.planning().tripType());
        aiPlanService.validatePlanCandidateSupervision(candidate, prepared.planning().ages());
        aiPlanService.validatePlanCandidateActivityKinds(candidate);

        List<AiPlanItemDtoOut> changedActivities = new ArrayList<>();
        Map<Integer, List<AiPlanItemDtoOut>> activitiesByMember = new HashMap<>();
        Map<Integer, JsonNode> rides = indexSnapshotResources(readGenerationJson(prepared.planning().inputJson()).path("rides"));
        boolean changed = false;

        for (int index = 0; index < candidate.getActivities().size(); index++) {

            AiPlanItemDtoOut proposed = candidate.getActivities().get(index);
            AiPlanItemDtoOut original = prepared.originalActivities().get(index);
            proposed.getParticipantVisitMemberIds().sort(Integer::compareTo);

            if (!Objects.equals(original.getActivityKind(), proposed.getActivityKind()) || (!replacementIndexes.contains(index) && !Objects.equals(original.getRideId(), proposed.getRideId())) || !Objects.equals(original.getPlaceId(), proposed.getPlaceId()) || !Objects.equals(original.getBreakType(), proposed.getBreakType()) || !Objects.equals(original.getSubgroupKey(), proposed.getSubgroupKey()) || !original.getParticipantVisitMemberIds().equals(proposed.getParticipantVisitMemberIds())) {
                throw new ApiException("A proposal cannot replace activities, change their order or change participants");
            }

            boolean same = readGenerationJson(writeGenerationJson(original)).equals(readGenerationJson(writeGenerationJson(proposed)));

            if (!prepared.editableIndexes().contains(index)) {

                if (!same) {
                    throw new ApiException("Completed, ongoing, fixed and out-of-scope activities must stay unchanged");
                }

            } else {

                if ((!replaceUnavailableRides && !Objects.equals(original.getWalkingMinutes(), proposed.getWalkingMinutes())) || (!replacementIndexes.contains(index) && !Objects.equals(original.getActivityMinutes(), proposed.getActivityMinutes()))) {
                    throw new ApiException("Approved walking and activity durations must stay unchanged");
                }

                if ("RIDE".equals(proposed.getActivityKind())) {

                    JsonNode ride = rides.get(proposed.getRideId());

                    if (replacementIndexes.contains(index)) {

                        if (Objects.equals(original.getRideId(), proposed.getRideId()) || ride == null || proposed.getActivityMinutes() != requirePositiveInteger(ride, "planningActivityMinutes")) {
                            throw new ApiException("The unavailable ride must be replaced by a supplied open ride with its verified duration");
                        }

                        validateReplacementParticipants(readGenerationJson(prepared.planning().inputJson()), prepared.planning().eligibility(), ride, proposed.getRideId(), proposed.getParticipantVisitMemberIds(), memberId -> candidate.getActivities().stream().filter(activity -> "RIDE".equals(activity.getActivityKind()) && proposed.getRideId().equals(activity.getRideId()) && activity.getParticipantVisitMemberIds().contains(memberId)).count(), false);
                    }



                    if (ride == null || !ride.path("planningWaitMinutes").isIntegralNumber() || proposed.getWaitingMinutes() != requireNonNegativeInteger(ride, "planningWaitMinutes")) {
                        throw new ApiException("A future ride requires a known server-provided wait estimate");
                    }

                } else if (proposed.getWaitingMinutes() != 0) {
                    throw new ApiException("Non-ride queue estimates are not supported");
                }

                changedActivities.add(proposed);
                changed = changed || !same;
            }

            for (Integer memberId : proposed.getParticipantVisitMemberIds()) {

                List<AiPlanItemDtoOut> memberActivities = activitiesByMember.computeIfAbsent(memberId, key -> new ArrayList<>());

                if (!memberActivities.isEmpty() && proposed.getRelativeStartMinutes() < (memberActivities.get(memberActivities.size() - 1).getExecutionEndMinutes() == null ? memberActivities.get(memberActivities.size() - 1).getRelativeEndMinutes() : memberActivities.get(memberActivities.size() - 1).getExecutionEndMinutes())) {
                    throw new ApiException("The existing activity order must stay unchanged for every participant");
                }

                memberActivities.add(proposed);
            }
        }

        if (replaceUnavailableRides) {
            validateReplacementWalking(activitiesByMember, rides, executionRules, readGenerationJson(prepared.planning().inputJson()));
        }

        List<AiPlanItemDtoOut> additions = serverItineraryService.selectAdditionalRides(readGenerationJson(prepared.planning().inputJson()), candidate.getActivities(), prepared.affectedIds(), requireNonNegativeInteger(timingInput, "currentRelativeMinute"));
        changedActivities.addAll(additions);
        List<AiPlanItemDtoOut> allProposedActivities = new ArrayList<>(candidate.getActivities());
        allProposedActivities.addAll(additions);
        validateAdditionalRides(readGenerationJson(prepared.planning().inputJson()), candidate.getActivities(), additions, prepared.affectedIds());
        AiPlanDtoOut completeProposal = new AiPlanDtoOut();
        completeProposal.setFeasible(true);
        completeProposal.setActivities(allProposedActivities);
        completeProposal.setWarnings(new ArrayList<>());
        aiPlanService.validatePlanCandidateTiming(completeProposal, prepared.planning().durationMinutes(), prepared.planning().memberIds(), prepared.planning().tripType());
        aiPlanService.validatePlanCandidateSupervision(completeProposal, prepared.planning().ages());
        for (AiPlanItemDtoOut addition : additions) for (Integer memberId : addition.getParticipantVisitMemberIds()) activitiesByMember.computeIfAbsent(memberId, key -> new ArrayList<>()).add(addition);

        if (!changed && additions.isEmpty()) {
            throw new ApiException("No schedule change is needed; the current plan remains unchanged");
        }

        if (readGenerationJson(prepared.planning().inputJson()).path("allowSplit").asBoolean()) {
            validateMinorTimelineCoverage(activitiesByMember, prepared.planning().ages());
        }

        AiPlanDtoOut editableCandidate = new AiPlanDtoOut();
        editableCandidate.setFeasible(true);
        editableCandidate.setActivities(changedActivities);
        editableCandidate.setWarnings(new ArrayList<>());
        aiPlanService.validatePlanCandidateEligibility(editableCandidate, prepared.planning().eligibility());

        return runGenerationTransaction(() -> {

            Account account = validateActiveAccount(accountId);
            Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

            if (visit == null || !"IN_PROGRESS".equals(visit.getStatus()) || visit.getStartedAt() == null || visit.getPlannedStartTime() == null || !visit.getVisitDate().atTime(visit.getPlannedStartTime()).equals(prepared.startsAt())) {
                throw new ApiException("The visit changed while preparing the proposal");
            }

            VisitPlan plan = visitPlanRepository.findVisitPlanByIdForUpdate(prepared.planId());

            if (plan == null || !plan.getVisit().getId().equals(visitId) || !Boolean.TRUE.equals(plan.getIsActive()) || !"SCHEDULED".equals(plan.getTimingMode()) || plan.getProposedItems() != null || plan.getPendingScope() != null || plan.getProposalBaseSnapshot() != null) {
                throw new ApiException("The active plan changed or another proposal is already pending");
            }

            VisitMember requester = getRequiredAccountMember(accountId, visitId);

            if (!requester.getId().equals(prepared.requesterId()) || !"GOING".equals(requester.getRsvpStatus()) || ("GROUP".equals(prepared.scope()) && !visit.getOrganizer().getId().equals(accountId) && !isTogetherGroupProposalAllowed(visit))) {
                throw new ApiException("The proposal requester's permissions changed");
            }

            DelayProposalSource delaySource = lockAndValidateDelayProposalSource(sourceDelayNotificationId, accountId, visitId, visit, plan);
            Notification delayNotification = delaySource.notification();
            ActivitySession delaySession = delaySource.session();
            String delaySnapshot = delaySource.snapshot();

            if (!Objects.equals(delaySnapshot, prepared.delaySnapshot())) {
                throw new ApiException("The source delay event changed while preparing the proposal");
            }

            GenerationSnapshot current = collectInitialGenerationSnapshot(visit.getOrganizer().getId(), visit);

            if (!generationSemanticInput(current.inputJson()).equals(generationSemanticInput(prepared.planning().inputJson()))) {
                throw new ApiException("Planning data changed while preparing the proposal; request a new proposal");
            }

            List<PlanItemDtoOut> items = new ArrayList<>();
            Set<Integer> itemIds = new HashSet<>();

            for (PlanItem item : getSortedPlanItems(plan)) {
                items.add(convertPlanItemToDtoOut(item));
                itemIds.add(item.getId());
            }

            List<Object> execution = new ArrayList<>();
            List<Integer> memberIds = new ArrayList<>(current.memberIds());
            memberIds.sort(Integer::compareTo);

            for (Integer memberId : memberIds) {
                appendExecutionSnapshotRows(execution, memberId, itemIds, activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(memberId), visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(memberId));
            }

            String executionJson = writeGenerationJson(Map.of("planId", plan.getId(), "items", items, "execution", execution));

            if (!readGenerationJson(executionJson).equals(readGenerationJson(prepared.executionJson()))) {
                throw new ApiException("Activity execution changed while preparing the proposal; request a new proposal");
            }

            validateCandidateResources(editableCandidate, visit.getVisitDate());
            LocalDateTime proposedAt = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));
            long elapsedSeconds = java.time.Duration.between(prepared.startsAt(), proposedAt).getSeconds();

            if (elapsedSeconds >= (long) current.durationMinutes() * 60) {
                throw new ApiException("The visit deadline was reached before the proposal could be saved");
            }

            int currentMinute = Math.toIntExact((Math.max(0, elapsedSeconds) + 59) / 60);
            List<PlanItemDtoOut> proposedItems = new ArrayList<>();

            for (int index = 0; index < prepared.items().size(); index++) {

                PlanItemDtoOut item = prepared.items().get(index);
                AiPlanItemDtoOut proposed = candidate.getActivities().get(index);

                if (prepared.editableIndexes().contains(index) && proposed.getRelativeStartMinutes() < currentMinute) {
                    throw new ApiException("A proposed future activity would already be in the past; request a fresh proposal");
                }

                if (replaceUnavailableRides && prepared.editableIndexes().contains(index)) {
                    item.setWalkingMinutes(proposed.getWalkingMinutes());
                }

                if (replacementIndexes.contains(index)) {
                    Ride replacement = rideRepository.findRideById(proposed.getRideId());

                    if (replacement == null || !PlanningAvailability.canPlanRide(replacement, visit.getVisitDate())) {
                        throw new ApiException("The replacement ride is no longer open");
                    }

                    item.setRideId(replacement.getId());
                    item.setRideName(replacement.getName());
                    item.setTitle(replacement.getName());
                    item.setActivityMinutes(proposed.getActivityMinutes());
                }

                item.setRelativeStartMinutes(proposed.getRelativeStartMinutes());
                item.setRelativeEndMinutes(proposed.getRelativeEndMinutes());
                item.setStartsAt(prepared.startsAt().plusMinutes(proposed.getRelativeStartMinutes()));
                item.setEndsAt(prepared.startsAt().plusMinutes(proposed.getRelativeEndMinutes()));
                item.setWaitingMinutes(proposed.getWaitingMinutes());
                proposedItems.add(item);
            }

            int nextSortOrder = prepared.items().stream().mapToInt(PlanItemDtoOut::getSortOrder).max().orElse(-1) + 1;
            List<PlanItemDtoOut> additionItems = new ArrayList<>();
            for (AiPlanItemDtoOut addition : additions) {
                if (addition.getRelativeStartMinutes() < currentMinute) throw new ApiException("A new activity would already be in the past; request a fresh proposal");
                PlanItem item = convertAiActivityToPlanItem(addition, plan, nextSortOrder++, current.priorities().getOrDefault(addition.getRideId(), "OPTIONAL"), false, false);
                item.setStartsAt(prepared.startsAt().plusMinutes(addition.getRelativeStartMinutes()));
                item.setEndsAt(prepared.startsAt().plusMinutes(addition.getRelativeEndMinutes()));
                PlanItemDtoOut additionItem = convertPlanItemToDtoOut(item);
                additionItems.add(additionItem);
                proposedItems.add(additionItem);
            }

            PlanApproval approval = planApprovalRepository.findPlanApprovalByVisitPlan_Id(plan.getId());

            if (approval == null || !"APPROVED".equals(approval.getStatus()) || !"GROUP".equals(approval.getApprovalScope())) {
                throw new ApiException("The plan's approval changed while preparing the proposal");
            }

            List<Object> baselineApproval = java.util.Arrays.asList(approval.getId(), approval.getStatus(), approval.getApprovalScope(), approval.getRequestedByAccount() == null ? null : approval.getRequestedByAccount().getId(), approval.getRequiredApproverAccount() == null ? null : approval.getRequiredApproverAccount().getId(), approval.getApprovedByAccount() == null ? null : approval.getApprovedByAccount().getId(), approval.getRespondedAt());
            String proposalKey = java.util.UUID.randomUUID().toString();
            Account approver = "GROUP".equals(prepared.scope()) ? visit.getOrganizer() : account;
            approval.setApprovalScope(prepared.scope());
            approval.setRequestedByAccount(account);
            approval.setRequiredApproverAccount(approver);
            approval.setApprovedByAccount(null);
            approval.setStatus("PENDING");
            approval.setRespondedAt(null);
            planApprovalRepository.save(approval);

            plan.setPlanApproval(approval);
            plan.setProposedItems(writeGenerationJson(proposedItems));
            plan.setPendingScope(prepared.scope());
            plan.setAffectedMemberIds(writeGenerationJson(prepared.affectedIds()));
            plan.setRequestedByVisitMember(requester);
            plan.setProposalReason(reason);
            plan.setProposedAt(proposedAt);
            plan.setProposalBaseSnapshot(writeGenerationJson(Map.of("execution", readGenerationJson(prepared.executionJson()), "planning", generationSemanticInput(current.inputJson()), "startsAt", prepared.startsAt().toString(), "editableIndexes", prepared.editableIndexes().stream().sorted().toList(), "baselineApproval", baselineApproval, "proposalKey", proposalKey, "executionRules", executionRules, "additionSnapshot", additionItems)));
            visitPlanRepository.save(plan);

            if (delayNotification != null) {

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("reason", sourceDelayReason);
                response.put("adjustPlan", true);
                response.put("visitPlanId", plan.getId());
                response.put("planApprovalId", approval.getId());
                response.put("proposalKey", proposalKey);
                response.put("proposalStatus", "PENDING");
                delayNotification.setResponse(writeGenerationJson(response));
                delayNotification.setRespondedAt(proposedAt);
                delaySession.setDelayReason(sourceDelayReason);
                delaySession.setDelayRespondedAt(proposedAt);
                activitySessionRepository.save(delaySession);
                notificationRepository.save(delayNotification);
            }

            notificationEventService.notifyReplanRequested(plan, proposalKey);
            return new ReplanProposalDtoOut(plan.getId(), approval.getId(), prepared.scope(), "PENDING", accountId, approver.getId(), prepared.affectedIds(), proposalKey);
        });
    }




    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 60)
    public void respondToReplanProposal(Integer accountId, PlanApprovalDtoIn dto, int statusInt) {

        if (statusInt != 1 && statusInt != 2) {
            throw new ApiException("Replan approval action must be 1 or 2");
        }
        String status = statusInt == 1 ? "APPROVED" : "REJECTED";


        Account account = validateActiveAccount(accountId);

        if (dto == null || dto.getVisitPlanId() == null || dto.getVisitPlanId() <= 0 || (!"APPROVED".equals(status) && !"REJECTED".equals(status))) {
            throw new ApiException("A positive visit plan ID and an APPROVED or REJECTED response are required");
        }

        VisitPlan reference = visitPlanRepository.findVisitPlanById(dto.getVisitPlanId());

        if (reference == null) {
            throw new ApiException("Visit plan not found");
        }

        Visit visit = visitRepository.findVisitByIdForUpdate(reference.getVisit().getId());

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        entityManager.refresh(visit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        VisitPlan plan = visitPlanRepository.findVisitPlanByIdForUpdate(dto.getVisitPlanId());

        if (plan == null) {
            throw new ApiException("Visit plan not found");
        }

        entityManager.refresh(plan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!plan.getVisit().getId().equals(visit.getId()) || !"GROUP".equals(plan.getPlanKind()) || !Boolean.TRUE.equals(plan.getIsActive()) || !"SCHEDULED".equals(plan.getTimingMode()) || !"IN_PROGRESS".equals(visit.getStatus())) {
            throw new ApiException("A proposal response requires an active scheduled group plan in a visit in progress");
        }

        if (plan.getProposedItems() == null || plan.getPendingScope() == null || plan.getProposalBaseSnapshot() == null || plan.getProposedAt() == null) {
            throw new ApiException("No pending execution proposal was found");
        }

        PlanApproval approval = planApprovalRepository.findPlanApprovalByVisitPlan_Id(plan.getId());

        if (approval == null) {
            throw new ApiException("Plan approval not found");
        }

        entityManager.refresh(approval, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!approval.getVisitPlan().getId().equals(plan.getId()) || !"PENDING".equals(approval.getStatus()) || !Objects.equals(plan.getPendingScope(), approval.getApprovalScope())) {
            throw new ApiException("The execution proposal and its approval do not match");
        }

        if (approval.getRequiredApproverAccount() == null || !approval.getRequiredApproverAccount().getId().equals(accountId)) {
            throw new ApiException("You are not the required approver");
        }

        JsonNode snapshot = readGenerationJson(plan.getProposalBaseSnapshot());

        if (dto.getProposalKey() == null || dto.getProposalKey().isBlank() || !dto.getProposalKey().equals(snapshot.path("proposalKey").asString())) {
            throw new ApiException("The proposal key is missing or no longer matches the pending proposal");
        }

        List<Integer> affectedIds = getProposalAffectedMemberIds(plan);
        VisitMember requester = plan.getRequestedByVisitMember();

        if (requester.getVisitorProfile() == null || approval.getRequestedByAccount() == null || !requester.getVisitorProfile().getAccount().getId().equals(approval.getRequestedByAccount().getId())) {
            throw new ApiException("The proposal requester and requesting account do not match");
        }

        if ("GROUP".equals(plan.getPendingScope())) {

            if (!visit.getOrganizer().getId().equals(accountId) || (!approval.getRequestedByAccount().getId().equals(accountId) && !isTogetherGroupProposalAllowed(visit))) {
                throw new ApiException("Only the organizer can approve or reject a group execution proposal");
            }

        } else if (!"INDIVIDUAL".equals(plan.getPendingScope()) || !requester.getVisitorProfile().getAccount().getId().equals(accountId)) {
            throw new ApiException("Only the requester can respond to their individual proposal");
        }

        JsonNode baseline = snapshot.path("baselineApproval");

        if (!snapshot.isObject() || !snapshot.path("execution").isObject() || !snapshot.path("planning").isObject() || !baseline.isArray() || baseline.size() != 7 || !baseline.get(0).isIntegralNumber() || !baseline.get(0).canConvertToInt() || !approval.getId().equals(baseline.get(0).intValue()) || !"APPROVED".equals(baseline.get(1).asString()) || !"GROUP".equals(baseline.get(2).asString())) {
            throw new ApiException("The proposal's original approval snapshot is invalid");
        }

        List<Account> originalAccounts = new ArrayList<>();

        for (int index = 3; index <= 5; index++) {

            JsonNode node = baseline.get(index);

            if (node.isNull()) {
                originalAccounts.add(null);
                continue;
            }

            if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() <= 0) {
                throw new ApiException("An original approval account ID is invalid");
            }

            Account originalAccount = accountRepository.findAccountById(node.intValue());

            if (originalAccount == null) {
                throw new ApiException("An original approval account no longer exists");
            }

            originalAccounts.add(originalAccount);
        }

        LocalDateTime originalRespondedAt = null;

        if (!baseline.get(6).isNull()) {

            try {
                originalRespondedAt = participantJsonMapper.treeToValue(baseline.get(6), LocalDateTime.class);
            } catch (JacksonException exception) {
                throw new ApiException("The original approval response time is invalid");
            }
        }

        if ("APPROVED".equals(status)) {

            if (visit.getStartedAt() == null || visit.getVisitDate() == null || visit.getPlannedStartTime() == null) {
                throw new ApiException("The visit's execution timing is missing");
            }

            LocalDateTime startsAt = visit.getVisitDate().atTime(visit.getPlannedStartTime());

            if (!startsAt.toString().equals(snapshot.path("startsAt").asString())) {
                throw new ApiException("The arrival time changed; reject this proposal and request a new one");
            }

            List<VisitMember> members = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visit.getId(), "GOING");
            members.sort(Comparator.comparing(VisitMember::getId));
            Set<Integer> memberIds = new HashSet<>();

            for (VisitMember member : members) {

                VisitMember locked = activitySessionRepository.findVisitMemberByIdForActivityUpdate(member.getId());

                if (locked == null) {
                    throw new ApiException("A planning member no longer exists");
                }

                entityManager.refresh(locked, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

                if (!locked.getVisit().getId().equals(visit.getId()) || !"GOING".equals(locked.getRsvpStatus())) {
                    throw new ApiException("Visit membership changed; reject this proposal and request a new one");
                }

                memberIds.add(locked.getId());
            }

            GenerationSnapshot current = collectInitialGenerationSnapshot(visit.getOrganizer().getId(), visit);

            if (!generationSemanticInput(current.inputJson()).equals(snapshot.path("planning"))) {
                throw new ApiException("Planning data changed; reject this proposal and request a new one");
            }

            if (!memberIds.equals(current.memberIds()) || ("GROUP".equals(plan.getPendingScope()) && !memberIds.equals(new HashSet<>(affectedIds)))) {
                throw new ApiException("The proposal's participant scope changed");
            }

            if ("INDIVIDUAL".equals(plan.getPendingScope()) && (current.ages().get(requester.getId()) == null || current.ages().get(requester.getId()) < 18)) {
                throw new ApiException("An individual execution proposal requires an adult requester");
            }

            List<PlanItem> items = getSortedPlanItems(plan);
            Set<Integer> itemIds = new HashSet<>();
            List<PlanItemDtoOut> itemDtos = new ArrayList<>();

            for (PlanItem item : items) {
                entityManager.refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                itemIds.add(item.getId());
                itemDtos.add(convertPlanItemToDtoOut(item));
            }

            List<Integer> sortedMemberIds = new ArrayList<>(memberIds);
            sortedMemberIds.sort(Integer::compareTo);
            Map<Integer, List<VisitRecord>> recordsByMember = new HashMap<>();
            List<VisitRecord> recordsToLock = new ArrayList<>();

            for (Integer memberId : sortedMemberIds) {

                List<VisitRecord> records = visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(memberId);
                recordsByMember.put(memberId, records);

                for (VisitRecord record : records) {

                    if (record.getPlanItem() != null && itemIds.contains(record.getPlanItem().getId())) {
                        recordsToLock.add(record);
                    }
                }
            }

            recordsToLock.sort(Comparator.comparing(VisitRecord::getId));

            for (VisitRecord record : recordsToLock) {
                entityManager.refresh(record, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            }

            List<Object> execution = new ArrayList<>();
            Set<Integer> startedIds = new HashSet<>();

            for (Integer memberId : sortedMemberIds) {

                List<ActivitySession> sessions = activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(memberId);

                for (ActivitySession session : sessions) {

                    if (itemIds.contains(session.getPlanItem().getId())) {
                        entityManager.refresh(session, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                    }
                }

                startedIds.addAll(appendExecutionSnapshotRows(execution, memberId, itemIds, sessions, recordsByMember.get(memberId)));
            }

            JsonNode executionNow = readGenerationJson(writeGenerationJson(Map.of("planId", plan.getId(), "items", itemDtos, "execution", execution)));

            if (!executionNow.equals(snapshot.path("execution"))) {
                throw new ApiException("Activity execution changed; reject this proposal and request a new one");
            }

            List<PlanItemDtoOut> proposedItems;

            try {
                PlanItemDtoOut[] values = participantJsonMapper.readValue(plan.getProposedItems(), PlanItemDtoOut[].class);
                proposedItems = values == null ? List.of() : java.util.Arrays.asList(values);
            } catch (JacksonException exception) {
                throw new ApiException("The proposed activities contain invalid data");
            }

            if (proposedItems.size() < items.size() || proposedItems.isEmpty() || !snapshot.path("editableIndexes").isArray()) {
                throw new ApiException("The proposed activities do not match the original plan");
            }

            Set<Integer> editable = new HashSet<>();

            for (JsonNode index : snapshot.path("editableIndexes")) {

                if (!index.isIntegralNumber() || !index.canConvertToInt() || index.intValue() < 0 || index.intValue() >= items.size() || !editable.add(index.intValue())) {
                    throw new ApiException("The proposal contains invalid editable activity indexes");
                }
            }

            JsonNode executionRules = snapshot.path("executionRules");
            boolean replacementMode = executionRules.path("replaceUnavailableRides").asBoolean(false);
            Set<Integer> replacementIndexes = new HashSet<>();

            if (replacementMode) {

                if (!executionRules.path("replaceableIndexes").isArray() || !executionRules.path("canonicalRideNodes").isArray()) {
                    throw new ApiException("The replacement proposal is missing its server planning context");
                }

                for (JsonNode index : executionRules.path("replaceableIndexes")) {

                    if (!index.isIntegralNumber() || !index.canConvertToInt() || !editable.contains(index.intValue()) || !replacementIndexes.add(index.intValue())) {
                        throw new ApiException("The replacement proposal contains invalid activity indexes");
                    }
                }

                if (replacementIndexes.isEmpty()) {
                    throw new ApiException("The replacement proposal has no unavailable ride to replace");
                }
            }

            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));
            long elapsedSeconds = java.time.Duration.between(startsAt, now).getSeconds();

            if (elapsedSeconds >= (long) current.durationMinutes() * 60) {
                throw new ApiException("The remaining visit time is unavailable; reject the proposal and review the duration");
            }

            int currentMinute = Math.toIntExact((Math.max(0, elapsedSeconds) + 59) / 60);
            List<AiPlanItemDtoOut> candidateActivities = new ArrayList<>();
            List<AiPlanItemDtoOut> editableActivities = new ArrayList<>();
            Map<Integer, List<AiPlanItemDtoOut>> activitiesByMember = new HashMap<>();
            Map<Integer, JsonNode> rides = indexSnapshotResources(readGenerationJson(current.inputJson()).path("rides"));

            for (int index = 0; index < items.size(); index++) {

                PlanItem item = items.get(index);
                PlanItemDtoOut before = itemDtos.get(index);
                PlanItemDtoOut proposed = proposedItems.get(index);

                if (proposed == null || !Objects.equals(item.getId(), proposed.getId()) || !Objects.equals(before.getActivityKind(), proposed.getActivityKind()) || (!replacementIndexes.contains(index) && !Objects.equals(before.getRideId(), proposed.getRideId())) || !Objects.equals(before.getPlaceId(), proposed.getPlaceId()) || !Objects.equals(before.getBreakType(), proposed.getBreakType()) || !Objects.equals(before.getParticipantVisitMemberIds(), proposed.getParticipantVisitMemberIds()) || !Objects.equals(before.getSubgroupKey(), proposed.getSubgroupKey())) {
                    throw new ApiException("The proposal changed activity identity or participant scope");
                }

                if (proposed.getRelativeStartMinutes() == null || proposed.getRelativeEndMinutes() == null || proposed.getWaitingMinutes() == null || !startsAt.plusMinutes(proposed.getRelativeStartMinutes()).equals(proposed.getStartsAt()) || !startsAt.plusMinutes(proposed.getRelativeEndMinutes()).equals(proposed.getEndsAt())) {
                    throw new ApiException("The proposal's relative and scheduled times do not match");
                }

                List<Integer> participants = new ArrayList<>(parseActivityParticipantIds(item.getParticipantVisitMemberIds()));
                participants.sort(Integer::compareTo);
                boolean fixed = Boolean.TRUE.equals(item.getPinned()) || "MEETING_POINT".equals(item.getActivityKind()) || "PRAYER".equals(item.getBreakType()) || "MEAL".equals(item.getBreakType()) || "SNACK".equals(item.getBreakType());
                boolean withinScope = "GROUP".equals(plan.getPendingScope()) || (participants.size() == 1 && participants.contains(requester.getId()));
                boolean canEdit = editable.contains(index) && !fixed && !startedIds.contains(item.getId()) && withinScope;

                if (editable.contains(index) && !canEdit) {
                    throw new ApiException("A proposed editable activity is now fixed, started or outside the approved scope");
                }

                ObjectNode beforeNode = (ObjectNode) participantJsonMapper.valueToTree(before);
                ObjectNode proposedNode = (ObjectNode) participantJsonMapper.valueToTree(proposed);

                if (canEdit) {

                    for (String field : List.of("relativeStartMinutes", "relativeEndMinutes", "startsAt", "endsAt", "waitingMinutes")) {
                        beforeNode.remove(field);
                        proposedNode.remove(field);
                    }

                    if (replacementMode) {
                        beforeNode.remove("walkingMinutes");
                        proposedNode.remove("walkingMinutes");
                    }

                    if (replacementIndexes.contains(index)) {

                        Ride unavailable = item.getRide();
                        JsonNode replacement = rides.get(proposed.getRideId());

                        if (!"RIDE".equals(item.getActivityKind()) || unavailable == null || PlanningAvailability.canPlanRide(unavailable, visit.getVisitDate()) || Objects.equals(before.getRideId(), proposed.getRideId()) || replacement == null || proposed.getActivityMinutes() == null || proposed.getActivityMinutes() != requirePositiveInteger(replacement, "planningActivityMinutes")) {
                            throw new ApiException("The original or replacement ride is no longer valid");
                        }

                        Ride replacementEntity = rideRepository.findRideById(proposed.getRideId());

                        if (replacementEntity == null || !PlanningAvailability.canPlanRide(replacementEntity, visit.getVisitDate()) || !Objects.equals(proposed.getRideName(), replacementEntity.getName()) || !Objects.equals(proposed.getTitle(), replacementEntity.getName())) {
                            throw new ApiException("Replacement ride identity or display data changed");
                        }

                        validateReplacementParticipants(readGenerationJson(current.inputJson()), current.eligibility(), replacement, proposed.getRideId(), participants, memberId -> proposedItems.stream().filter(activity -> "RIDE".equals(activity.getActivityKind()) && proposed.getRideId().equals(activity.getRideId()) && parseActivityParticipantIds(activity.getParticipantVisitMemberIds()).contains(memberId)).count(), true);

                        for (String field : List.of("rideId", "rideName", "title", "activityMinutes")) {
                            beforeNode.remove(field);
                            proposedNode.remove(field);
                        }
                    }

                    if (proposed.getRelativeStartMinutes() < currentMinute) {
                        throw new ApiException("A proposed activity is now in the past; reject it and request a new proposal");
                    }

                    if ("RIDE".equals(item.getActivityKind())) {

                        JsonNode ride = rides.get(proposed.getRideId());

                        if (ride == null || proposed.getWaitingMinutes() != requireNonNegativeInteger(ride, "planningWaitMinutes")) {
                            throw new ApiException("The proposed waiting estimate is no longer valid");
                        }

                    } else if (proposed.getWaitingMinutes() != 0) {
                        throw new ApiException("Non-ride queue estimates are not supported");
                    }
                }

                if (!beforeNode.equals(proposedNode)) {
                    throw new ApiException("The proposal changed a protected activity field");
                }

                AiPlanItemDtoOut candidateItem = convertPlanItemToAiActivity(proposed, participants);

                if (item.getExecutionReleasedAt() != null && !item.getExecutionReleasedAt().isBefore(item.getStartsAt()) && item.getExecutionReleasedAt().isBefore(item.getEndsAt())) {
                    java.time.Duration releaseOffset = java.time.Duration.between(startsAt, item.getExecutionReleasedAt());
                    long releasedSeconds = releaseOffset.getSeconds() + (releaseOffset.getNano() > 0 ? 1 : 0);
                    candidateItem.setExecutionEndMinutes(Math.toIntExact(Math.floorDiv(releasedSeconds + 59, 60)));
                }
                candidateActivities.add(candidateItem);

                if (canEdit) {
                    editableActivities.add(candidateItem);
                }

                for (Integer memberId : participants) {

                    List<AiPlanItemDtoOut> memberActivities = activitiesByMember.computeIfAbsent(memberId, key -> new ArrayList<>());

                    if (!memberActivities.isEmpty() && candidateItem.getRelativeStartMinutes() < (memberActivities.get(memberActivities.size() - 1).getExecutionEndMinutes() == null ? memberActivities.get(memberActivities.size() - 1).getRelativeEndMinutes() : memberActivities.get(memberActivities.size() - 1).getExecutionEndMinutes())) {
                        throw new ApiException("The proposal changed activity order or introduced overlapping activities");
                    }

                    memberActivities.add(candidateItem);
                }
            }

            List<PlanItemDtoOut> newItems = proposedItems.subList(items.size(), proposedItems.size());
            JsonNode expectedAdditions = snapshot.path("additionSnapshot");
            if (!newItems.isEmpty() && (!expectedAdditions.isArray() || !readGenerationJson(writeGenerationJson(newItems)).equals(expectedAdditions))) throw new ApiException("New proposal activities do not match their server snapshot");
            if (expectedAdditions.isArray() && expectedAdditions.size() != newItems.size()) throw new ApiException("The proposed additions changed");
            List<AiPlanItemDtoOut> newActivities = new ArrayList<>();
            for (PlanItemDtoOut proposed : newItems) {
                if (proposed.getId() != null || !"RIDE".equals(proposed.getActivityKind()) || Boolean.TRUE.equals(proposed.getPinned()) || proposed.getRelativeStartMinutes() < currentMinute || !startsAt.plusMinutes(proposed.getRelativeStartMinutes()).equals(proposed.getStartsAt()) || !startsAt.plusMinutes(proposed.getRelativeEndMinutes()).equals(proposed.getEndsAt())) throw new ApiException("New activity scope or timing is invalid");
                newActivities.add(convertPlanItemToAiActivity(proposed, new ArrayList<>(parseActivityParticipantIds(proposed.getParticipantVisitMemberIds()))));
            }
            validateAdditionalRides(readGenerationJson(current.inputJson()), candidateActivities, newActivities, affectedIds);
            candidateActivities.addAll(newActivities);
            editableActivities.addAll(newActivities);
            for (AiPlanItemDtoOut addition : newActivities) for (Integer memberId : addition.getParticipantVisitMemberIds()) activitiesByMember.computeIfAbsent(memberId, key -> new ArrayList<>()).add(addition);

            if (replacementMode) {
                validateReplacementWalking(activitiesByMember, rides, executionRules, readGenerationJson(current.inputJson()));
            }

            AiPlanDtoOut candidate = new AiPlanDtoOut();
            candidate.setFeasible(true);
            candidate.setActivities(candidateActivities);
            candidate.setWarnings(new ArrayList<>());
            aiPlanService.validatePlanCandidateTiming(candidate, current.durationMinutes(), current.memberIds(), current.tripType());
            aiPlanService.validatePlanCandidateSupervision(candidate, current.ages());
            aiPlanService.validatePlanCandidateActivityKinds(candidate);

            if (readGenerationJson(current.inputJson()).path("allowSplit").asBoolean()) {
                validateMinorTimelineCoverage(activitiesByMember, current.ages());
            }

            AiPlanDtoOut editableCandidate = new AiPlanDtoOut();
            editableCandidate.setFeasible(true);
            editableCandidate.setActivities(editableActivities);
            editableCandidate.setWarnings(new ArrayList<>());
            aiPlanService.validatePlanCandidateEligibility(editableCandidate, current.eligibility());
            validateCandidateResources(editableCandidate, visit.getVisitDate());

            for (int index = 0; index < items.size(); index++) {

                if (editable.contains(index)) {
                    PlanItem item = items.get(index);
                    PlanItemDtoOut proposed = proposedItems.get(index);
                    if (replacementMode) {
                        item.setWalkingMinutes(proposed.getWalkingMinutes());
                    }

                    if (replacementIndexes.contains(index)) {
                        item.setRide(rideRepository.findRideById(proposed.getRideId()));
                        item.setTitle(proposed.getTitle());
                        item.setActivityMinutes(proposed.getActivityMinutes());
                    }

                    item.setRelativeStartMinutes(proposed.getRelativeStartMinutes());
                    item.setRelativeEndMinutes(proposed.getRelativeEndMinutes());
                    item.setStartsAt(proposed.getStartsAt());
                    item.setEndsAt(proposed.getEndsAt());
                    item.setWaitingMinutes(proposed.getWaitingMinutes());
                }
            }

            int nextSortOrder = items.stream().mapToInt(PlanItem::getSortOrder).max().orElse(-1) + 1;
            for (int index = 0; index < newActivities.size(); index++) {
                AiPlanItemDtoOut addition = newActivities.get(index);
                PlanItem item = convertAiActivityToPlanItem(addition, plan, nextSortOrder++, current.priorities().getOrDefault(addition.getRideId(), "OPTIONAL"), false, false);
                item.setStartsAt(startsAt.plusMinutes(addition.getRelativeStartMinutes()));
                item.setEndsAt(startsAt.plusMinutes(addition.getRelativeEndMinutes()));
                items.add(item);
            }
            if (!newActivities.isEmpty() && plan.getExcludedRides() != null) {
                List<ExcludedRideDtoOut> exclusions = new ArrayList<>(Arrays.asList(participantJsonMapper.treeToValue(readGenerationJson(plan.getExcludedRides()), ExcludedRideDtoOut[].class)));
                exclusions.removeIf(exclusion -> newActivities.stream().anyMatch(activity -> exclusion.getRideId().equals(activity.getRideId()) && activity.getParticipantVisitMemberIds().contains(exclusion.getVisitMemberId())));
                plan.setExcludedRides(writeGenerationJson(exclusions));
            }
            planItemRepository.saveAll(items);
        }

        notificationEventService.notifyReplanResult(plan, dto.getProposalKey(),
                "APPROVED".equals(status), affectedIds);

        approval.setStatus("APPROVED");
        approval.setApprovalScope("GROUP");
        approval.setRequestedByAccount(originalAccounts.get(0));
        approval.setRequiredApproverAccount(originalAccounts.get(1));
        approval.setApprovedByAccount(originalAccounts.get(2));
        approval.setRespondedAt(originalRespondedAt);
        planApprovalRepository.save(approval);

        plan.setPlanApproval(approval);
        plan.setProposedItems(null);
        plan.setPendingScope(null);
        plan.setAffectedMemberIds(null);
        plan.setRequestedByVisitMember(null);
        plan.setProposalReason(null);
        plan.setProposedAt(null);
        plan.setProposalBaseSnapshot(null);
        visitPlanRepository.save(plan);
        planItemRepository.flush();
        if ("APPROVED".equals(status)) {
            calendarEventPublisher.publishEvent(new CalendarSyncOnApproval.ApprovedVisitChanged(visit.getOrganizer().getId(), visit.getId()));
        }
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateVisitArrival(Integer accountId, Integer visitId, UpdateVisitArrivalDtoIn dto) {

        if (accountId == null || accountId <= 0 || visitId == null || visitId <= 0 || dto == null || dto.getExpectedArrivalTime() == null) {
            throw new ApiException("Positive account and visit IDs and an expected arrival time are required");
        }

        java.time.LocalTime requestedArrival = dto.getExpectedArrivalTime();
        record ArrivalPreparation(LocalDate originalDate, Integer planId, Integer historyId, LocalDateTime arrivalAt, Integer durationMinutes, GenerationSnapshot planning, List<PlanItemDtoOut> items, String itemSnapshot, String headerSnapshot, boolean includePrayer, String resolvedMealPolicy) {
        }

        ArrivalPreparation prepared = runGenerationTransaction(() -> {

            validateActiveAccount(accountId);
            Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

            if (visit == null) {
                throw new ApiException("Visit not found");
            }

            entityManager.refresh(visit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

            if (!visit.getOrganizer().getId().equals(accountId) || !"READY".equals(visit.getStatus()) || visit.getStartedAt() != null) {
                throw new ApiException("Only the organizer can schedule arrival after approval and before the visit starts");
            }

            LocalDate targetDate = dto.getVisitDate() == null ? visit.getVisitDate() : dto.getVisitDate();
            if (targetDate.isBefore(LocalDate.now(ZoneId.of("Asia/Riyadh")))) throw new ApiException("Visit date must be today or later");

            if (visit.getDurationMinutes() == null || visit.getDurationMinutes() <= 0 || visit.getIncludePrayer() == null) {
                throw new ApiException("A positive visit duration and a prayer preference are required");
            }

            Map<String, Object> mealPolicy = visitMealBreakService.getMealBreakPolicy(accountId, visitId);
            boolean mealDeferred = "DEFERRED".equals(mealPolicy.get("choice")) || dto.getMealPreferences() != null;

            if (mealDeferred && dto.getMealPreferences() == null) {
                throw new ApiException("Choose your food preference before scheduling arrival");
            }


            if (mealDeferred) {
                String choice = dto.getMealPreferences().getChoice();
                Map<String, List<String>> choices = Map.of("NONE", List.of(), "SKIPPED", List.of(), "ONE_MEAL", List.of("MEAL"), "TWO_MEALS", List.of("MEAL", "MEAL"), "TWO_SNACKS", List.of("SNACK", "SNACK"), "SNACK", List.of("SNACK"), "MEAL_AND_SNACK", List.of("MEAL", "SNACK"));

                if (choice == null || !choices.containsKey(choice)) {
                    throw new ApiException("Choose a final food preference; it cannot remain deferred");
                }

                List<String> timings = dto.getMealPreferences().getTimings() == null ? List.of() : dto.getMealPreferences().getTimings();

                if (timings.size() != choices.get(choice).size() || timings.stream().anyMatch(timing -> timing == null || !Set.of("START", "MIDDLE", "END").contains(timing))) {
                    throw new ApiException("Provide one valid timing for each food break");
                }

                List<Integer> participantIds = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING").stream().map(VisitMember::getId).sorted().toList();

                if (participantIds.isEmpty()) {
                    throw new ApiException("At least one GOING participant is required");
                }

                List<Map<String, Object>> foodBreaks = new ArrayList<>();
                List<String> breakTypes = choices.get(choice);
                int totalFoodMinutes = 0;

                for (int index = 0; index < breakTypes.size(); index++) {
                    String breakType = breakTypes.get(index);
                    int duration = "MEAL".equals(breakType) ? 60 : 30;
                    totalFoodMinutes += duration;
                    Map<String, Object> foodBreak = new LinkedHashMap<>();
                    foodBreak.put("breakKey", java.util.UUID.randomUUID().toString());
                    foodBreak.put("breakType", breakType);
                    foodBreak.put("timingPreference", timings.get(index));
                    foodBreak.put("durationMinutes", duration);
                    foodBreak.put("earliestStartMinutes", 0);
                    foodBreak.put("latestStartMinutes", visit.getDurationMinutes() - duration);
                    List<Integer> restaurantIds = dto.getMealPreferences().getRestaurantIds();
                    if (restaurantIds != null && !restaurantIds.isEmpty() && restaurantIds.size() != breakTypes.size()) throw new ApiException("Provide one restaurant selection for each food break");
                    Integer restaurantId = restaurantIds == null || restaurantIds.isEmpty() ? null : restaurantIds.get(index);
                    if (restaurantId != null) {
                        Place restaurant = placeRepository.findPlaceById(restaurantId);
                        if (restaurant == null || !"RESTAURANT".equals(restaurant.getKind()) || !PlanningAvailability.canPlanPlace(restaurant)) throw new ApiException("Choose an open restaurant");
                    }
                    foodBreak.put("restaurantId", restaurantId);
                    foodBreak.put("participantVisitMemberIds", participantIds);
                    foodBreaks.add(foodBreak);
                }

                if (totalFoodMinutes > visit.getDurationMinutes()) {
                    throw new ApiException("Food breaks exceed the visit duration");
                }

                mealPolicy = Map.of("ready", true, "choice", choice, "breaks", foodBreaks);
            }

            List<VisitPlan> plans = visitPlanRepository.findAllByVisit_Id(visitId);
            plans.sort(Comparator.comparing(VisitPlan::getId));
            List<VisitPlan> lockedPlans = new ArrayList<>();
            VisitPlan groupPlan = null;

            for (VisitPlan reference : plans) {

                VisitPlan plan = visitPlanRepository.findVisitPlanByIdForUpdate(reference.getId());

                if (plan == null) {
                    throw new ApiException("A visit plan is no longer available");
                }

                entityManager.refresh(plan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

                if (!plan.getVisit().getId().equals(visitId)) {
                    throw new ApiException("A plan no longer belongs to this visit");
                }

                lockedPlans.add(plan);

                if ("GROUP".equals(plan.getPlanKind()) && Boolean.TRUE.equals(plan.getIsActive())) {

                    if (groupPlan != null) {
                        throw new ApiException("Multiple active group plans were found");
                    }

                    groupPlan = plan;
                }
            }

            if (groupPlan == null || groupPlan.getPlanApproval() == null || !"APPROVED".equals(groupPlan.getPlanApproval().getStatus())) {
                throw new ApiException("An active approved group plan is required");
            }

            if (groupPlan.getProposedItems() != null || groupPlan.getPendingScope() != null || groupPlan.getProposalBaseSnapshot() != null) {
                throw new ApiException("Resolve the pending proposal before changing arrival time");
            }

            Integer historyId = getGenerationHistoryId(groupPlan);
            List<VisitPlan> individualHeaders = new ArrayList<>();
            List<Object> headerRows = new ArrayList<>();

            for (VisitPlan plan : lockedPlans) {

                if (!"INDIVIDUAL".equals(plan.getPlanKind()) || !Objects.equals(historyId, getGenerationHistoryId(plan))) {
                    continue;
                }

                if (isFamilyVisit(visit) || plan.getOwnerVisitMember() == null || plan.getOwnerVisitMember().getVisitorProfile() == null || !plan.getOwnerVisitMember().getVisit().getId().equals(visitId)) {
                    throw new ApiException("The current generation contains an invalid individual plan header");
                }

                if (plan.getProposedItems() != null || plan.getPendingScope() != null || plan.getProposalBaseSnapshot() != null || !planItemRepository.findAllByVisitPlan_Id(plan.getId()).isEmpty()) {
                    throw new ApiException("Individual plans must use shared group activities and have no pending proposals");
                }

                individualHeaders.add(plan);
                headerRows.add(java.util.Arrays.asList(plan.getId(), plan.getTimingMode(), plan.getIsActive(), historyId, plan.getOwnerVisitMember().getId()));
            }

            List<PlanItem> items = getSortedPlanItems(groupPlan);
            List<PlanItemDtoOut> itemDtos = new ArrayList<>();

            for (PlanItem item : items) {

                if (item.getRelativeStartMinutes() == null || item.getRelativeEndMinutes() == null || item.getRelativeStartMinutes() < 0 || item.getRelativeEndMinutes() <= item.getRelativeStartMinutes() || item.getRelativeEndMinutes() > visit.getDurationMinutes()) {
                    throw new ApiException("Activities must have valid relative times within the visit duration");
                }

                itemDtos.add(convertPlanItemToDtoOut(item));
            }

            ParkOperatingHours.validateVisitWindow(targetDate, requestedArrival, visit.getDurationMinutes());
            LocalDateTime arrivalAt = targetDate.atTime(requestedArrival);
            LocalDateTime endsAt = arrivalAt.plusMinutes(visit.getDurationMinutes());

            if (!Boolean.TRUE.equals(visit.getIncludePrayer()) && !mealDeferred && targetDate.equals(visit.getVisitDate())) {

                for (PlanItem item : items) {

                    if ("PRAYER".equals(item.getBreakType())) {
                        throw new ApiException("The plan contains prayer activities but the visit prayer preference is disabled");
                    }

                    item.setStartsAt(arrivalAt.plusMinutes(item.getRelativeStartMinutes()));
                    item.setEndsAt(arrivalAt.plusMinutes(item.getRelativeEndMinutes()));
                }

                groupPlan.setTimingMode("SCHEDULED");

                for (VisitPlan header : individualHeaders) {
                    header.setTimingMode("SCHEDULED");
                }

                visit.setExpectedArrivalTime(arrivalAt.toLocalTime());
                visit.setPlannedStartTime(arrivalAt.toLocalTime());
                visit.setPlannedEndTime(endsAt.toLocalTime());
                planItemRepository.saveAll(items);
                visitPlanRepository.save(groupPlan);
                visitPlanRepository.saveAll(individualHeaders);
                visitRepository.save(visit);
                calendarEventPublisher.publishEvent(new CalendarSyncOnApproval.ApprovedVisitChanged(accountId, visitId));
                return null;
            }

            if (requestedArrival.getSecond() != 0 || requestedArrival.getNano() != 0) {
                throw new ApiException("Prayer-aware arrival scheduling requires an HH:mm arrival time");
            }

            if (endsAt.toLocalDate().isAfter(arrivalAt.toLocalDate().plusDays(1))) {
                throw new ApiException("Prayer scheduling supports visits ending no later than the following calendar day");
            }

            if (Boolean.TRUE.equals(visit.getIncludePrayer()) && (parkLatitude == null || !Double.isFinite(parkLatitude) || parkLatitude < -90 || parkLatitude > 90 || parkLongitude == null || !Double.isFinite(parkLongitude) || parkLongitude < -180 || parkLongitude > 180)) {
                throw new ApiException("Configure verified park latitude and longitude before scheduling prayer times");
            }

            Visit planningVisit = new Visit();
            org.springframework.beans.BeanUtils.copyProperties(visit, planningVisit);
            planningVisit.setVisitDate(targetDate);
            GenerationSnapshot planning = collectInitialGenerationSnapshot(accountId, planningVisit);
            return new ArrivalPreparation(visit.getVisitDate(), groupPlan.getId(), historyId, arrivalAt, visit.getDurationMinutes(), planning, itemDtos, writeGenerationJson(itemDtos), writeGenerationJson(headerRows), Boolean.TRUE.equals(visit.getIncludePrayer()), mealDeferred ? writeGenerationJson(mealPolicy) : null);
        });

        if (prepared == null) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ApiException("Prayer time and AI requests must run outside the database transaction");
        }

        LocalDateTime visitEndsAt = prepared.arrivalAt().plusMinutes(prepared.durationMinutes());
        List<Map<String, Object>> prayerEvents = new ArrayList<>();
        Set<Integer> prayerMinutes = new HashSet<>();

        for (LocalDate date = prepared.arrivalAt().toLocalDate(); prepared.includePrayer() && !date.isAfter(visitEndsAt.toLocalDate()); date = date.plusDays(1)) {

            Map<String, LocalDateTime> times = prayerTimeService.getPrayerTimes(date, parkLatitude, parkLongitude);

            for (String name : List.of("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")) {

                LocalDateTime prayerAt = times.get(name);

                if (prayerAt == null || !prayerAt.toLocalDate().equals(date) || prayerAt.getSecond() != 0 || prayerAt.getNano() != 0) {
                    throw new ApiException("The prayer time service returned incomplete or invalid prayer times");
                }

                if (prayerAt.isBefore(prepared.arrivalAt()) || !prayerAt.isBefore(visitEndsAt)) {
                    continue;
                }

                if (prayerAt.plusMinutes(15).isAfter(visitEndsAt)) {
                    throw new ApiException("A prayer break would extend beyond the visit; review the arrival time or duration");
                }

                int minute = Math.toIntExact(java.time.Duration.between(prepared.arrivalAt(), prayerAt).toMinutes());

                if (!prayerMinutes.add(minute)) {
                    throw new ApiException("The prayer service returned duplicate prayer event times");
                }

                prayerEvents.add(Map.of("name", name, "prayerAt", prayerAt.toString(), "relativePrayerMinute", minute, "activityMinutes", 15));
            }
        }

        prayerEvents.sort(Comparator.comparingInt(event -> (Integer) event.get("relativePrayerMinute")));
        List<AiPlanItemDtoOut> approvedActivities = new ArrayList<>();
        List<Object> fixedActivities = new ArrayList<>();
        Map<Integer, List<String>> approvedOrder = new HashMap<>();
        Map<String, java.util.ArrayDeque<PlanItemDtoOut>> available = new LinkedHashMap<>();

        for (PlanItemDtoOut item : prepared.items()) {

            if ("PRAYER".equals(item.getBreakType()) || prepared.resolvedMealPolicy() != null && ("MEAL".equals(item.getBreakType()) || "SNACK".equals(item.getBreakType()))) {
                continue;
            }

            List<Integer> participants = new ArrayList<>(parseActivityParticipantIds(item.getParticipantVisitMemberIds()));
            participants.sort(Integer::compareTo);
            String key = writeGenerationJson(java.util.Arrays.asList(item.getActivityKind(), item.getRideId(), item.getPlaceId(), item.getBreakType(), item.getActivityMinutes(), participants, item.getSubgroupKey()));
            available.computeIfAbsent(key, ignored -> new java.util.ArrayDeque<>()).add(item);

            for (Integer memberId : participants) {
                approvedOrder.computeIfAbsent(memberId, ignored -> new ArrayList<>()).add(key);
            }

            AiPlanItemDtoOut activity = convertPlanItemToAiActivity(item, participants);
            approvedActivities.add(activity);

            if (Boolean.TRUE.equals(item.getPinned()) || "MEETING_POINT".equals(item.getActivityKind())) {
                fixedActivities.add(Map.of("activity", activity, "relativeStartMinutes", item.getRelativeStartMinutes(), "relativeEndMinutes", item.getRelativeEndMinutes()));
            }
        }

        ObjectNode input = (ObjectNode) readGenerationJson(prepared.planning().inputJson());
        if (prepared.resolvedMealPolicy() != null) {
            input.set("mealPolicy", readGenerationJson(prepared.resolvedMealPolicy()));
        }

        Map<Integer, JsonNode> arrivalResources = indexSnapshotResources(input.path("rides"));
        for (AiPlanItemDtoOut activity : approvedActivities) {
            if (activity.getRideId() == null) continue;
            for (Integer memberId : activity.getParticipantVisitMemberIds()) {
                if (!"NOT_ELIGIBLE".equals(input.path("eligibilityByRideAndMember").path(String.valueOf(activity.getRideId())).path(String.valueOf(memberId)).asString())) continue;
                String memberName = "أحد المشاركين";
                for (JsonNode member : input.path("members")) if (member.path("visitMemberId").asInt() == memberId) memberName = member.path("name").asString(memberName);
                JsonNode ride = arrivalResources.get(activity.getRideId());
                String rideName = ride == null ? "إحدى الألعاب المعتمدة" : ride.path("name").asString("إحدى الألعاب المعتمدة");
                throw new ApiException("اللعبة «" + rideName + "» لا تناسب «" + memberName + "» حسب بياناته الحالية. راجع بيانات المشارك أو اطلب خطة بديلة مناسبة للجميع؛ خطتك السابقة محفوظة.");
            }
        }

        GenerationSnapshot arrivalPlanning = new GenerationSnapshot(writeGenerationJson(input), prepared.planning().durationMinutes(), prepared.planning().tripType(), prepared.planning().memberIds(), prepared.planning().ages(), prepared.planning().eligibility(), prepared.planning().priorities(), prepared.planning().pinnedRideIds(), prepared.planning().rateablePlaceIds());

        input.put("operationType", "ARRIVAL");
        input.put("arrivalTimeKnown", true);
        input.put("arrivalAt", prepared.arrivalAt().toString());
        input.put("prayerTimesVerified", true);
        input.set("prayerEvents", participantJsonMapper.valueToTree(prayerEvents));
        input.set("approvedActivities", participantJsonMapper.valueToTree(approvedActivities));
        input.set("fixedActivities", participantJsonMapper.valueToTree(fixedActivities));
        input.put("preserveAllNonPrayerActivities", true);
        input.put("preservePerMemberActivityOrder", true);
        AiPlanDtoOut candidate;
        boolean scheduleApprovedRidesDirectly = !prepared.includePrayer() && prayerEvents.isEmpty() && fixedActivities.isEmpty() && input.path("allowSplit").isBoolean() && !input.path("allowSplit").asBoolean() && !approvedActivities.isEmpty();

        for (AiPlanItemDtoOut activity : approvedActivities) {
            if (!"RIDE".equals(activity.getActivityKind()) || activity.getBreakType() != null || activity.getPlaceId() != null || !new HashSet<>(activity.getParticipantVisitMemberIds()).equals(prepared.planning().memberIds())) {
                scheduleApprovedRidesDirectly = false;
            }
        }

        for (JsonNode definition : input.path("mealPolicy").path("breaks")) {
            if (!definition.path("restaurantId").isNull() || !new HashSet<>(readPositiveIntegerArray(definition.path("participantVisitMemberIds"), "Meal participants", false)).equals(prepared.planning().memberIds())) {
                scheduleApprovedRidesDirectly = false;
            }
        }

        if (scheduleApprovedRidesDirectly) {
            // Retain the approved ride order and calculate arrival timing from trusted snapshots.
            List<AiPlanItemDtoOut> arrivalActivities = new ArrayList<>();
            approvedActivities.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes).thenComparing(AiPlanItemDtoOut::getRelativeEndMinutes));

            for (int position = 0; position <= approvedActivities.size(); position++) {
                for (JsonNode definition : input.path("mealPolicy").path("breaks")) {
                    String timing = requireText(definition, "timingPreference");
                    int insertionPosition;
                    if ("START".equals(timing)) {
                        insertionPosition = 0;
                    } else if ("END".equals(timing)) {
                        insertionPosition = approvedActivities.size();
                    } else if ("MIDDLE".equals(timing)) {
                        if (approvedActivities.size() < 2) {
                            throw new ApiException("A middle food break requires an approved activity before and after it");
                        }
                        insertionPosition = (approvedActivities.size() + 1) / 2;
                    } else {
                        throw new ApiException("Invalid food timing preference");
                    }

                    if (position != insertionPosition) {
                        continue;
                    }

                    AiPlanItemDtoOut food = new AiPlanItemDtoOut();
                    food.setActivityKind("BREAK");
                    food.setBreakType(requireText(definition, "breakType"));
                    food.setPlaceId(definition.path("restaurantId").isNull() ? null : requirePositiveInteger(definition, "restaurantId"));
                    food.setActivityMinutes(requirePositiveInteger(definition, "durationMinutes"));
                    food.setWalkingMinutes(0);
                    food.setWaitingMinutes(0);
                    food.setParticipantVisitMemberIds(readPositiveIntegerArray(definition.path("participantVisitMemberIds"), "Meal participants", false));
                    food.setSubgroupKey(approvedActivities.get(0).getSubgroupKey());
                    food.setRelativeStartMinutes(requireNonNegativeInteger(definition, "earliestStartMinutes"));
                    food.setRelativeEndMinutes(requireNonNegativeInteger(definition, "latestStartMinutes"));
                    arrivalActivities.add(food);
                }

                if (position < approvedActivities.size()) {
                    arrivalActivities.add(approvedActivities.get(position));
                }
            }

            Map<Integer, JsonNode> arrivalRides = indexSnapshotResources(input.path("rides"));
            Map<String, Integer> arrivalRoutes = new HashMap<>();
            for (JsonNode route : input.path("walking").path("routes")) {
                String routeKey = walkingRouteKey(requireText(route, "fromNodeKey"), requireText(route, "toNodeKey"));
                int minutes = requireNonNegativeInteger(route, "minutes");
                Integer previousMinutes = arrivalRoutes.putIfAbsent(routeKey, minutes);
                if (previousMinutes != null && previousMinutes != minutes) {
                    throw new ApiException("Conflicting walking routes in the arrival snapshot");
                }
            }

            String previousNode = requireText(input.path("walking"), "entranceNodeKey");
            long nextStart = 0;
            for (AiPlanItemDtoOut activity : arrivalActivities) {
                if ("RIDE".equals(activity.getActivityKind())) {
                    JsonNode ride = arrivalRides.get(activity.getRideId());
                    if (ride == null || !"OPEN".equals(ride.path("status").asString())) {
                        throw new ApiException("An approved ride is no longer available; review the plan before arrival");
                    }
                    String nextNode = requireText(ride, "nodeKey");
                    Integer walking = previousNode.equals(nextNode) ? Integer.valueOf(0) : arrivalRoutes.get(walkingRouteKey(previousNode, nextNode));
                    if (walking == null) {
                        throw new ApiException("No supplied walking route for an approved arrival activity");
                    }
                    if (!Objects.equals(activity.getActivityMinutes(), requirePositiveInteger(ride, "planningActivityMinutes"))) {
                        throw new ApiException("An approved ride duration has changed; review the plan before arrival");
                    }
                    activity.setWalkingMinutes(walking);
                    activity.setWaitingMinutes(requireNonNegativeInteger(ride, "planningWaitMinutes"));
                    previousNode = nextNode;
                } else {
                    if (activity.getPlaceId() != null) {
                        String nextNode = serverItineraryService.placeNode(input, activity.getPlaceId());
                        Integer walk = serverItineraryService.walking(input, previousNode, nextNode);
                        if (walk == null) throw new ApiException("No supplied walking route to the selected restaurant");
                        activity.setWalkingMinutes(walk);
                        previousNode = nextNode;
                    }
                    nextStart = Math.max(nextStart, activity.getRelativeStartMinutes());
                    if (nextStart > activity.getRelativeEndMinutes()) {
                        throw new ApiException("The requested food break cannot fit its allowed start window");
                    }
                }

                long end = nextStart + activity.getWalkingMinutes() + activity.getWaitingMinutes() + activity.getActivityMinutes();
                if (end > prepared.durationMinutes()) {
                    throw new ApiException("The approved activities and food breaks exceed the visit duration; increase the duration or review the plan");
                }
                activity.setRelativeStartMinutes(Math.toIntExact(nextStart));
                activity.setRelativeEndMinutes(Math.toIntExact(end));
                nextStart = end;
            }

            candidate = new AiPlanDtoOut();
            candidate.setFeasible(true);
            candidate.setActivities(arrivalActivities);
            candidate.setWarnings(new ArrayList<>());
        } else {
            candidate = arrivalPrayerScheduleService.scheduleArrivalWithMealsAndPrayer(input, approvedActivities, prepared.resolvedMealPolicy() != null);
            boolean scheduleSimplePrayerVisit = candidate == null && prepared.includePrayer() && !prayerEvents.isEmpty() && fixedActivities.isEmpty() && input.path("allowSplit").isBoolean() && !input.path("allowSplit").asBoolean() && input.path("mealPolicy").path("breaks").isArray() && input.path("mealPolicy").path("breaks").isEmpty() && !approvedActivities.isEmpty();
            for (AiPlanItemDtoOut activity : approvedActivities) {
                if (!"RIDE".equals(activity.getActivityKind()) || activity.getPlaceId() != null || activity.getBreakType() != null || !new HashSet<>(activity.getParticipantVisitMemberIds()).equals(prepared.planning().memberIds())) {
                    scheduleSimplePrayerVisit = false;
                }
            }
            if (scheduleSimplePrayerVisit) {
                Map<Integer, JsonNode> prayerVisitRides = indexSnapshotResources(input.path("rides"));
                Map<String, Integer> prayerVisitRoutes = new HashMap<>();
                for (JsonNode route : input.path("walking").path("routes")) {
                    prayerVisitRoutes.put(walkingRouteKey(requireText(route, "fromNodeKey"), requireText(route, "toNodeKey")), requireNonNegativeInteger(route, "minutes"));
                }
                String prayerVisitNode = requireText(input.path("walking"), "entranceNodeKey");
                List<AiPlanItemDtoOut> prayerVisitActivities = new ArrayList<>();
                List<AiPlanItemDtoOut> prayerVisitApprovedOrder = new ArrayList<>(approvedActivities);
                prayerVisitApprovedOrder.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes).thenComparing(AiPlanItemDtoOut::getRelativeEndMinutes));
                long prayerVisitAvailable = 0;
                for (AiPlanItemDtoOut original : prayerVisitApprovedOrder) {
                    JsonNode ride = prayerVisitRides.get(original.getRideId());
                    if (ride == null || !"OPEN".equals(ride.path("status").asString())) {
                        throw new ApiException("An approved ride is unavailable at arrival; review the plan");
                    }
                    String destination = requireText(ride, "nodeKey");
                    Integer walk = prayerVisitNode.equals(destination) ? Integer.valueOf(0) : prayerVisitRoutes.get(walkingRouteKey(prayerVisitNode, destination));
                    if (walk == null) {
                        throw new ApiException("No supplied arrival walking route to approved ride " + original.getRideId());
                    }
                    int wait = requireNonNegativeInteger(ride, "planningWaitMinutes");
                    int activityMinutes = requirePositiveInteger(ride, "planningActivityMinutes");
                    if (!Objects.equals(original.getActivityMinutes(), activityMinutes)) {
                        throw new ApiException("Approved ride duration changed; review the plan before arrival");
                    }
                    long end = prayerVisitAvailable + walk + wait + activityMinutes;
                    if (end > prepared.durationMinutes()) {
                        scheduleSimplePrayerVisit = false;
                        break;
                    }
                    AiPlanItemDtoOut timed = new AiPlanItemDtoOut();
                    timed.setActivityKind("RIDE");
                    timed.setRideId(original.getRideId());
                    timed.setParticipantVisitMemberIds(new ArrayList<>(original.getParticipantVisitMemberIds()));
                    timed.setSubgroupKey(original.getSubgroupKey());
                    timed.setRelativeStartMinutes(Math.toIntExact(prayerVisitAvailable));
                    timed.setRelativeEndMinutes(Math.toIntExact(end));
                    timed.setWalkingMinutes(walk);
                    timed.setWaitingMinutes(wait);
                    timed.setActivityMinutes(activityMinutes);
                    prayerVisitActivities.add(timed);
                    prayerVisitAvailable = end;
                    prayerVisitNode = destination;
                }
                JsonNode selectedPrayerRoom = null;
                Integer selectedPrayerWalk = null;
                int firstPrayerMinute = (Integer) prayerEvents.get(0).get("relativePrayerMinute");
                if (scheduleSimplePrayerVisit) {
                    for (JsonNode place : input.path("places")) {
                        if (!"PRAYER_ROOM".equals(place.path("kind").asString()) || !"OPEN".equals(place.path("status").asString())) {
                            continue;
                        }
                        String destination = requireText(place, "nodeKey");
                        Integer walk = prayerVisitNode.equals(destination) ? Integer.valueOf(0) : prayerVisitRoutes.get(walkingRouteKey(prayerVisitNode, destination));
                        if (walk != null && prayerVisitAvailable + walk <= firstPrayerMinute && (selectedPrayerWalk == null || walk < selectedPrayerWalk)) {
                            selectedPrayerRoom = place;
                            selectedPrayerWalk = walk;
                        }
                    }
                    if (selectedPrayerRoom == null) {
                        scheduleSimplePrayerVisit = false;
                    }
                }
                if (scheduleSimplePrayerVisit) {
                    for (Map<String, Object> event : prayerEvents) {
                        int minute = (Integer) event.get("relativePrayerMinute");
                        int walk = prayerVisitActivities.size() == prayerVisitApprovedOrder.size() ? selectedPrayerWalk : 0;
                        long start = (long) minute - walk;
                        long end = (long) minute + 15;
                        if (start < prayerVisitAvailable || end > prepared.durationMinutes()) {
                            scheduleSimplePrayerVisit = false;
                            break;
                        }
                        AiPlanItemDtoOut prayer = new AiPlanItemDtoOut();
                        prayer.setActivityKind("BREAK");
                        prayer.setPlaceId(requirePositiveInteger(selectedPrayerRoom, "id"));
                        prayer.setBreakType("PRAYER");
                        prayer.setParticipantVisitMemberIds(new ArrayList<>(prayerVisitApprovedOrder.get(0).getParticipantVisitMemberIds()));
                        prayer.setSubgroupKey(prayerVisitApprovedOrder.get(0).getSubgroupKey());
                        prayer.setRelativeStartMinutes(Math.toIntExact(start));
                        prayer.setRelativeEndMinutes(Math.toIntExact(end));
                        prayer.setWalkingMinutes(walk);
                        prayer.setWaitingMinutes(0);
                        prayer.setActivityMinutes(15);
                        prayerVisitActivities.add(prayer);
                        prayerVisitAvailable = end;
                    }
                }
                if (scheduleSimplePrayerVisit) {
                    candidate = new AiPlanDtoOut();
                    candidate.setFeasible(true);
                    candidate.setActivities(prayerVisitActivities);
                    candidate.setWarnings(new ArrayList<>());
                }
            }
            if (candidate == null) {
                candidate = aiPlanService.generatePlanCandidate(writeGenerationJson(input));
                if (!Boolean.TRUE.equals(candidate.getFeasible()) && candidate.getInfeasibilityReason() != null && candidate.getInfeasibilityReason().contains("UNKNOWN")) {
                    input.put("validationFeedback", "Review the rejection once. Explicit UNKNOWN eligibility is provisionally allowed for existing approved rides and is not a boarding approval. Do not reject solely for UNKNOWN. Keep all NOT_ELIGIBLE, missing-data, prayer, meal, walking and duration constraints. If a real constraint remains, return infeasible with that specific reason; never invent a feasible schedule.");
                    candidate = aiPlanService.generatePlanCandidate(writeGenerationJson(input));
                }
            }
        }

        if (!Boolean.TRUE.equals(candidate.getFeasible())) {
            throw new ApiException(candidate.getInfeasibilityReason() == null || candidate.getInfeasibilityReason().isBlank() ? "The approved activities and actual prayer times cannot fit; review arrival time or duration" : candidate.getInfeasibilityReason());
        }

        aiPlanService.validatePlanCandidateTiming(candidate, prepared.durationMinutes(), prepared.planning().memberIds(), prepared.planning().tripType());
        aiPlanService.validatePlanCandidateSupervision(candidate, prepared.planning().ages());
        aiPlanService.validatePlanCandidateActivityKinds(candidate);
        aiPlanService.validatePlanCandidateEligibility(candidate, prepared.planning().eligibility());
        mergeGenerationWarnings(candidate, input);
        validateInitialCandidateAgainstSnapshot(candidate, arrivalPlanning);

        List<AiPlanItemDtoOut> activities = new ArrayList<>(candidate.getActivities());
        activities.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes).thenComparing(AiPlanItemDtoOut::getRelativeEndMinutes));
        Map<Integer, List<String>> proposedOrder = new HashMap<>();
        Map<Integer, Set<Integer>> prayerParticipants = new HashMap<>();
        Map<AiPlanItemDtoOut, Integer> sourceIds = new java.util.IdentityHashMap<>();
        Map<AiPlanItemDtoOut, String> prayerNames = new java.util.IdentityHashMap<>();
        Map<Integer, String> namesByMinute = new HashMap<>();

        for (Map<String, Object> event : prayerEvents) {
            namesByMinute.put((Integer) event.get("relativePrayerMinute"), (String) event.get("name"));
        }

        for (AiPlanItemDtoOut activity : activities) {

            List<Integer> participants = new ArrayList<>(activity.getParticipantVisitMemberIds());
            participants.sort(Integer::compareTo);

            if ("PRAYER".equals(activity.getBreakType())) {

                int prayerMinute = Math.addExact(activity.getRelativeStartMinutes(), activity.getWalkingMinutes());

                if (!"BREAK".equals(activity.getActivityKind()) || activity.getWaitingMinutes() != 0 || activity.getActivityMinutes() != 15 || !prayerMinutes.contains(prayerMinute) || activity.getRelativeEndMinutes() != prayerMinute + 15) {
                    throw new ApiException("A prayer activity does not match an actual prayer event and its 15-minute duration");
                }

                Set<Integer> covered = prayerParticipants.computeIfAbsent(prayerMinute, ignored -> new HashSet<>());

                for (Integer memberId : participants) {

                    if (!covered.add(memberId)) {
                        throw new ApiException("A participant has duplicate activities for the same prayer");
                    }
                }

                prayerNames.put(activity, namesByMinute.get(prayerMinute));
                continue;
            }

            String key = writeGenerationJson(java.util.Arrays.asList(activity.getActivityKind(), activity.getRideId(), activity.getPlaceId(), activity.getBreakType(), activity.getActivityMinutes(), participants, activity.getSubgroupKey()));
            java.util.ArrayDeque<PlanItemDtoOut> matches = available.get(key);

            if (prepared.resolvedMealPolicy() != null && ("MEAL".equals(activity.getBreakType()) || "SNACK".equals(activity.getBreakType()))) {
                if (!"BREAK".equals(activity.getActivityKind()) || activity.getRideId() != null) {
                    throw new ApiException("A food break must preserve its activity kind");
                }
                continue;
            }

            if (matches == null || matches.isEmpty()) {
                throw new ApiException("Arrival scheduling cannot add, replace or change participants of an approved non-prayer activity");
            }

            PlanItemDtoOut original = matches.removeFirst();
            sourceIds.put(activity, original.getId());

            if ((Boolean.TRUE.equals(original.getPinned()) || "MEETING_POINT".equals(original.getActivityKind())) && (!Objects.equals(original.getRelativeStartMinutes(), activity.getRelativeStartMinutes()) || !Objects.equals(original.getRelativeEndMinutes(), activity.getRelativeEndMinutes()))) {
                throw new ApiException("The prayer schedule conflicts with a fixed activity or meeting time");
            }

            for (Integer memberId : participants) {
                proposedOrder.computeIfAbsent(memberId, ignored -> new ArrayList<>()).add(key);
            }
        }

        if (available.values().stream().anyMatch(queue -> !queue.isEmpty()) || !approvedOrder.equals(proposedOrder)) {
            throw new ApiException("Arrival scheduling must preserve every approved non-prayer activity and its order for each participant");
        }

        for (Integer minute : prayerMinutes) {

            if (!prepared.planning().memberIds().equals(prayerParticipants.get(minute))) {
                throw new ApiException("Every participant must have a prayer break for each actual prayer event during the visit");
            }
        }

        AiPlanDtoOut arrivalCandidate = candidate;
        runGenerationTransaction(() -> {

            validateActiveAccount(accountId);
            Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

            if (visit == null) {
                throw new ApiException("Visit not found");
            }

            entityManager.refresh(visit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

            if (!visit.getOrganizer().getId().equals(accountId) || !"READY".equals(visit.getStatus()) || visit.getStartedAt() != null || !visit.getVisitDate().equals(prepared.originalDate()) || !Objects.equals(visit.getDurationMinutes(), prepared.durationMinutes()) || Boolean.TRUE.equals(visit.getIncludePrayer()) != prepared.includePrayer()) {
                throw new ApiException("The visit changed while preparing the prayer schedule");
            }

            List<VisitPlan> plans = visitPlanRepository.findAllByVisit_Id(visitId);
            plans.sort(Comparator.comparing(VisitPlan::getId));
            List<VisitPlan> headers = new ArrayList<>();
            List<Object> headerRows = new ArrayList<>();
            VisitPlan groupPlan = null;
            int activeGroups = 0;

            for (VisitPlan reference : plans) {

                VisitPlan plan = visitPlanRepository.findVisitPlanByIdForUpdate(reference.getId());

                if (plan == null) {
                    throw new ApiException("A plan changed while preparing arrival");
                }

                entityManager.refresh(plan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

                if (!plan.getVisit().getId().equals(visitId)) {
                    throw new ApiException("A plan no longer belongs to this visit");
                }

                if ("GROUP".equals(plan.getPlanKind()) && Boolean.TRUE.equals(plan.getIsActive())) {
                    activeGroups++;
                    groupPlan = plan;
                }

                if ("INDIVIDUAL".equals(plan.getPlanKind()) && Objects.equals(prepared.historyId(), getGenerationHistoryId(plan))) {

                    if (plan.getOwnerVisitMember() == null || plan.getOwnerVisitMember().getVisitorProfile() == null || !plan.getOwnerVisitMember().getVisit().getId().equals(visitId) || plan.getProposedItems() != null || plan.getPendingScope() != null || plan.getProposalBaseSnapshot() != null || !planItemRepository.findAllByVisitPlan_Id(plan.getId()).isEmpty()) {
                        throw new ApiException("An individual header changed while preparing arrival");
                    }

                    headers.add(plan);
                    headerRows.add(java.util.Arrays.asList(plan.getId(), plan.getTimingMode(), plan.getIsActive(), getGenerationHistoryId(plan), plan.getOwnerVisitMember().getId()));
                }
            }

            if (activeGroups != 1 || groupPlan == null || !groupPlan.getId().equals(prepared.planId()) || !Objects.equals(getGenerationHistoryId(groupPlan), prepared.historyId()) || groupPlan.getPlanApproval() == null || !"APPROVED".equals(groupPlan.getPlanApproval().getStatus()) || groupPlan.getProposedItems() != null || groupPlan.getPendingScope() != null || groupPlan.getProposalBaseSnapshot() != null) {
                throw new ApiException("The active approved plan changed while preparing arrival");
            }

            if (!readGenerationJson(writeGenerationJson(headerRows)).equals(readGenerationJson(prepared.headerSnapshot()))) {
                throw new ApiException("Individual plan headers changed while preparing arrival");
            }

            visit.setVisitDate(prepared.arrivalAt().toLocalDate());
            GenerationSnapshot current = collectInitialGenerationSnapshot(visit.getOrganizer().getId(), visit);

            if (!generationSemanticInput(current.inputJson()).equals(generationSemanticInput(prepared.planning().inputJson()))) {
                throw new ApiException("Planning data changed while preparing arrival; retry with current data");
            }

            List<PlanItem> oldItems = getSortedPlanItems(groupPlan);
            List<PlanItemDtoOut> currentItems = new ArrayList<>();
            Map<Integer, PlanItem> byId = new HashMap<>();

            for (PlanItem item : oldItems) {
                entityManager.refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                currentItems.add(convertPlanItemToDtoOut(item));
                byId.put(item.getId(), item);
            }

            if (!readGenerationJson(writeGenerationJson(currentItems)).equals(readGenerationJson(prepared.itemSnapshot()))) {
                throw new ApiException("Approved activities changed while preparing arrival");
            }

            for (Integer memberId : current.memberIds()) {

                for (ActivitySession session : activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(memberId)) {

                    if (byId.containsKey(session.getPlanItem().getId())) {
                        throw new ApiException("Arrival scheduling cannot change activities that already have tracking sessions");
                    }
                }

                for (VisitRecord record : visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(memberId)) {

                    if (record.getPlanItem() != null && byId.containsKey(record.getPlanItem().getId())) {
                        throw new ApiException("Arrival scheduling cannot change activities that already have execution records");
                    }
                }
            }

            aiPlanService.validatePlanCandidateTiming(arrivalCandidate, current.durationMinutes(), current.memberIds(), current.tripType());
            aiPlanService.validatePlanCandidateSupervision(arrivalCandidate, current.ages());
            aiPlanService.validatePlanCandidateActivityKinds(arrivalCandidate);
            aiPlanService.validatePlanCandidateEligibility(arrivalCandidate, current.eligibility());
            ObjectNode currentInput = (ObjectNode) readGenerationJson(current.inputJson());

            if (prepared.resolvedMealPolicy() != null) {
                currentInput.set("mealPolicy", readGenerationJson(prepared.resolvedMealPolicy()));
            }

            currentInput.put("arrivalTimeKnown", true);
            currentInput.put("prayerTimesVerified", true);
            GenerationSnapshot finalPlanning = new GenerationSnapshot(writeGenerationJson(currentInput), current.durationMinutes(), current.tripType(), current.memberIds(), current.ages(), current.eligibility(), current.priorities(), current.pinnedRideIds(), current.rateablePlaceIds());
            arrivalCandidate.setExcludedRides(serverItineraryService.explainRequiredExclusions(currentInput, arrivalCandidate.getActivities()));
            resolveExcludedRideNames(arrivalCandidate);
            validateInitialCandidateAgainstSnapshot(arrivalCandidate, finalPlanning);
            Map<Integer, List<AiPlanItemDtoOut>> foodActivitiesByMember = new HashMap<>();

            for (AiPlanItemDtoOut activity : activities) {
                for (Integer memberId : activity.getParticipantVisitMemberIds()) {
                    foodActivitiesByMember.computeIfAbsent(memberId, ignored -> new ArrayList<>()).add(activity);
                }
            }

            Map<AiPlanItemDtoOut, String> foodKeys = validateSnapshotMeals(arrivalCandidate, currentInput.path("mealPolicy"), foodActivitiesByMember);
            validateCandidateResources(arrivalCandidate, visit.getVisitDate());

            Map<String, String> prayerTitles = Map.of("Fajr", "صلاة الفجر", "Dhuhr", "صلاة الظهر", "Asr", "صلاة العصر", "Maghrib", "صلاة المغرب", "Isha", "صلاة العشاء");
            List<PlanItem> scheduled = new ArrayList<>();

            for (int index = 0; index < activities.size(); index++) {

                AiPlanItemDtoOut activity = activities.get(index);
                PlanItem item;

                if ("PRAYER".equals(activity.getBreakType())) {
                    item = convertAiActivityToPlanItem(activity, groupPlan, index, null, false, false);
                    item.setTitle(prayerTitles.get(prayerNames.get(activity)));
                } else if (prepared.resolvedMealPolicy() != null && foodKeys.containsKey(activity)) {
                    item = convertAiActivityToPlanItem(activity, groupPlan, index, null, false, false);
                } else {
                    item = byId.get(sourceIds.get(activity));
                    item.setRelativeStartMinutes(activity.getRelativeStartMinutes());
                    item.setRelativeEndMinutes(activity.getRelativeEndMinutes());
                    item.setWalkingMinutes(activity.getWalkingMinutes());
                    item.setWaitingMinutes(activity.getWaitingMinutes());
                    item.setSortOrder(index);
                }

                item.setStartsAt(prepared.arrivalAt().plusMinutes(activity.getRelativeStartMinutes()));
                item.setEndsAt(prepared.arrivalAt().plusMinutes(activity.getRelativeEndMinutes()));
                scheduled.add(item);
            }

            if (prepared.resolvedMealPolicy() != null) {
                visitMealBreakRepository.deleteAll(visitMealBreakRepository.findVisitMealBreaksByVisitId(visitId));
                visitMealBreakRepository.flush();
            }
            for (PlanItem item : oldItems) {

                if ("PRAYER".equals(item.getBreakType()) || prepared.resolvedMealPolicy() != null && ("MEAL".equals(item.getBreakType()) || "SNACK".equals(item.getBreakType()))) {
                    planItemRepository.delete(item);
                }
            }

            planItemRepository.saveAll(scheduled);
            if (prepared.resolvedMealPolicy() != null) {
                List<VisitMealBreak> previousMeals = visitMealBreakRepository.findVisitMealBreaksByVisitId(visitId);

                if (previousMeals.stream().anyMatch(meal -> !"DEFERRED".equals(meal.getMealChoice()) || meal.getPlanItem() != null)) {
                    throw new ApiException("Food preferences changed while preparing arrival");
                }

                visitMealBreakRepository.deleteAll(previousMeals);
                Map<Integer, VisitMember> mealMembers = new HashMap<>();

                for (VisitMember member : visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING")) {
                    mealMembers.put(member.getId(), member);
                }

                JsonNode resolvedPolicy = readGenerationJson(prepared.resolvedMealPolicy());

                for (JsonNode definition : resolvedPolicy.path("breaks")) {
                    for (JsonNode participant : definition.path("participantVisitMemberIds")) {
                        VisitMealBreak meal = new VisitMealBreak();
                        meal.setVisit(visit);
                        meal.setVisitMember(mealMembers.get(participant.asInt()));
                        meal.setBreakKey(definition.path("breakKey").asString());
                        meal.setMealChoice(definition.path("breakType").asString());
                        meal.setPreferredTiming(definition.path("timingPreference").asString());
                        visitMealBreakRepository.save(meal);
                    }
                }

                if (resolvedPolicy.path("breaks").isEmpty()) {
                    String breakKey = java.util.UUID.randomUUID().toString();
                    for (VisitMember member : mealMembers.values()) {
                        VisitMealBreak meal = new VisitMealBreak();
                        meal.setVisit(visit);
                        meal.setVisitMember(member);
                        meal.setBreakKey(breakKey);
                        meal.setMealChoice(resolvedPolicy.path("choice").asString());
                        visitMealBreakRepository.save(meal);
                    }
                }

                for (int index = 0; index < activities.size(); index++) {
                    String foodKey = foodKeys.get(activities.get(index));
                    if (foodKey != null) {
                        visitMealBreakService.bindGeneratedBreak(accountId, visitId, foodKey, scheduled.get(index).getId());
                    }
                }
            }

            groupPlan.setPlanItems(new HashSet<>(scheduled));
            groupPlan.setTimingMode("SCHEDULED");

            for (VisitPlan header : headers) {
                header.setTimingMode("SCHEDULED");
            }

            visit.setExpectedArrivalTime(prepared.arrivalAt().toLocalTime());
            visit.setPlannedStartTime(prepared.arrivalAt().toLocalTime());
            visit.setPlannedEndTime(visitEndsAt.toLocalTime());
            visitPlanRepository.save(groupPlan);
            visitPlanRepository.saveAll(headers);
            visitRepository.save(visit);
            planItemRepository.flush();
            calendarEventPublisher.publishEvent(new CalendarSyncOnApproval.ApprovedVisitChanged(accountId, visitId));
            return null;
        });
    }



    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void selectMealRestaurant(Integer accountId, Integer visitId, String breakKey, com.example.sikka.DtoIn.SelectMealRestaurantDtoIn dto) {
        if (accountId == null || accountId <= 0 || visitId == null || visitId <= 0 || breakKey == null || breakKey.isBlank() || dto == null || dto.getPlaceId() == null || dto.getPlaceId() <= 0) {
            throw new ApiException("Positive account, visit and restaurant IDs and a break key are required");
        }

        validateActiveAccount(accountId);
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        entityManager.refresh(visit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the organizer can select the restaurant for a shared food break");
        }

        if ((!"READY".equals(visit.getStatus()) && !"IN_PROGRESS".equals(visit.getStatus())) || !LocalDate.now(ZoneId.of("Asia/Riyadh")).equals(visit.getVisitDate())) {
            throw new ApiException("Select the restaurant on the visit date before the food break starts");
        }

        List<VisitPlan> plans = visitPlanRepository.findAllByVisit_Id(visitId);
        plans.sort(Comparator.comparing(VisitPlan::getId));
        VisitPlan groupPlan = null;

        for (VisitPlan reference : plans) {
            VisitPlan plan = visitPlanRepository.findVisitPlanByIdForUpdate(reference.getId());
            if (plan == null) {
                throw new ApiException("A visit plan is no longer available");
            }
            entityManager.refresh(plan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (plan.getProposedItems() != null || plan.getPendingScope() != null || plan.getProposalBaseSnapshot() != null) {
                throw new ApiException("Resolve pending proposals before selecting a restaurant");
            }
            if ("GROUP".equals(plan.getPlanKind()) && Boolean.TRUE.equals(plan.getIsActive())) {
                if (groupPlan != null) {
                    throw new ApiException("Multiple active group plans were found");
                }
                groupPlan = plan;
            }
        }

        if (groupPlan == null || !"SCHEDULED".equals(groupPlan.getTimingMode()) || groupPlan.getPlanApproval() == null || !"APPROVED".equals(groupPlan.getPlanApproval().getStatus())) {
            throw new ApiException("Schedule arrival for an approved active plan before selecting a restaurant");
        }

        List<VisitMealBreak> meals = visitMealBreakRepository.findVisitMealBreaksByVisitIdAndBreakKey(visitId, breakKey);
        if (meals.isEmpty() || meals.get(0).getPlanItem() == null) {
            throw new ApiException("A scheduled food break is required");
        }

        Integer targetId = meals.get(0).getPlanItem().getId();
        Set<Integer> foodParticipants = new HashSet<>();
        for (VisitMealBreak meal : meals) {
            if (meal.getPlanItem() == null || !targetId.equals(meal.getPlanItem().getId()) || (!"MEAL".equals(meal.getMealChoice()) && !"SNACK".equals(meal.getMealChoice())) || !foodParticipants.add(meal.getVisitMember().getId())) {
                throw new ApiException("The shared food break has inconsistent participant records");
            }
        }

        Place restaurant = placeRepository.findPlaceById(dto.getPlaceId());
        if (restaurant == null || !"RESTAURANT".equals(restaurant.getKind()) || !PlanningAvailability.canPlanPlace(restaurant)) {
            throw new ApiException("Choose an open restaurant");
        }

        GenerationSnapshot planning = collectInitialGenerationSnapshot(accountId, visit);
        ObjectNode input = (ObjectNode) readGenerationJson(planning.inputJson());
        boolean matchedFoodPolicy = false;
        for (JsonNode definition : input.path("mealPolicy").path("breaks")) {
            if (breakKey.equals(definition.path("breakKey").asString())) {
                ((ObjectNode) definition).put("restaurantId", restaurant.getId());
                matchedFoodPolicy = true;
            }
        }
        if (!matchedFoodPolicy) {
            throw new ApiException("The food break is not part of the current planning policy");
        }

        Map<Integer, JsonNode> rides = indexSnapshotResources(input.path("rides"));
        Map<Integer, JsonNode> places = indexSnapshotResources(input.path("places"));
        if (!places.containsKey(restaurant.getId())) {
            throw new ApiException("The restaurant needs a verified walking network link");
        }

        Map<String, Integer> routes = new HashMap<>();
        for (JsonNode route : input.path("walking").path("routes")) {
            routes.put(walkingRouteKey(requireText(route, "fromNodeKey"), requireText(route, "toNodeKey")), requireNonNegativeInteger(route, "minutes"));
        }

        List<PlanItem> items = getSortedPlanItems(groupPlan);
        if (items.isEmpty()) {
            throw new ApiException("The active plan has no activities");
        }
        Map<Integer, PlanItem> byId = new HashMap<>();
        for (PlanItem item : items) {
            entityManager.refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            byId.put(item.getId(), item);
        }
        PlanItem target = byId.get(targetId);
        if (target == null || !foodParticipants.equals(parseActivityParticipantIds(target.getParticipantVisitMemberIds())) || (!"MEAL".equals(target.getBreakType()) && !"SNACK".equals(target.getBreakType()))) {
            throw new ApiException("The food break does not match the active shared plan");
        }
        if (target.getStartsAt() == null || target.getEndsAt() == null || target.getRelativeStartMinutes() == null || target.getRelativeEndMinutes() == null) {
            throw new ApiException("The food break has no valid scheduled times");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));
        if (!now.isBefore(target.getEndsAt())) {
            throw new ApiException("This food break has already ended; select a restaurant before it starts");
        }
        LocalDateTime arrivalAt = target.getStartsAt().minusMinutes(target.getRelativeStartMinutes());
        int currentMinute = Math.max(0, Math.toIntExact(java.time.Duration.between(arrivalAt, now).toMinutes()));
        if (arrivalAt.plusMinutes(currentMinute).isBefore(now)) {
            currentMinute++;
        }

        Map<Integer, String> previousNodes = new HashMap<>();
        Map<Integer, Integer> previousEnds = new HashMap<>();
        Set<Integer> affectedMembers = new HashSet<>();
        List<AiPlanItemDtoOut> activities = new ArrayList<>();
        List<PlanItem> changedItems = new ArrayList<>();
        Set<Integer> changedIds = new HashSet<>();
        for (PlanItem item : items) {
            List<Integer> participants = new ArrayList<>(parseActivityParticipantIds(item.getParticipantVisitMemberIds()));
            participants.sort(Integer::compareTo);
            AiPlanItemDtoOut activity = convertPlanItemToAiActivity(convertPlanItemToDtoOut(item), participants);
            if (item.getExecutionReleasedAt() != null && item.getStartsAt() != null && !item.getExecutionReleasedAt().isBefore(item.getStartsAt()) && item.getExecutionReleasedAt().isBefore(item.getEndsAt())) {
                java.time.Duration releaseOffset = java.time.Duration.between(arrivalAt, item.getExecutionReleasedAt());
                long releasedSeconds = releaseOffset.getSeconds() + (releaseOffset.getNano() > 0 ? 1 : 0);
                activity.setExecutionEndMinutes(Math.toIntExact(Math.floorDiv(releasedSeconds + 59, 60)));
            }
            boolean isTarget = targetId.equals(item.getId());
            boolean affected = isTarget || participants.stream().anyMatch(affectedMembers::contains);
            if (isTarget) {
                activity.setPlaceId(restaurant.getId());
            }
            Integer requiredWalking = null;
            int earliestStart = activity.getRelativeStartMinutes();
            if (isTarget) {
                earliestStart = Math.max(earliestStart, currentMinute);
            }
            for (Integer memberId : participants) {
                String previousNode = previousNodes.getOrDefault(memberId, requireText(input.path("walking"), "entranceNodeKey"));
                String nextNode = previousNode;
                if (activity.getRideId() != null) {
                    nextNode = requireText(rides.get(activity.getRideId()), "nodeKey");
                } else if (activity.getPlaceId() != null) {
                    nextNode = requireText(places.get(activity.getPlaceId()), "nodeKey");
                }
                if (affected) {
                    Integer walking = previousNode.equals(nextNode) ? Integer.valueOf(0) : routes.get(walkingRouteKey(previousNode, nextNode));
                    if (walking == null || (requiredWalking != null && !requiredWalking.equals(walking))) {
                        throw new ApiException("The restaurant route cannot be applied to this shared activity; choose another restaurant or replan");
                    }
                    requiredWalking = walking;
                    earliestStart = Math.max(earliestStart, previousEnds.getOrDefault(memberId, 0));
                }
                previousNodes.put(memberId, nextNode);
            }
            if (affected) {
                int end = Math.addExact(earliestStart, Math.addExact(requiredWalking, Math.addExact(activity.getWaitingMinutes(), activity.getActivityMinutes())));
                boolean changed = isTarget || !Objects.equals(item.getWalkingMinutes(), requiredWalking) || !Objects.equals(item.getRelativeStartMinutes(), earliestStart) || !Objects.equals(item.getRelativeEndMinutes(), end);
                if (changed && (Boolean.TRUE.equals(item.getPinned()) || "PRAYER".equals(item.getBreakType()) || "MEETING_POINT".equals(item.getActivityKind()) || (!isTarget && ("MEAL".equals(item.getBreakType()) || "SNACK".equals(item.getBreakType()))))) {
                    throw new ApiException("The restaurant conflicts with a fixed prayer, meal or meeting; choose another restaurant or replan");
                }
                activity.setWalkingMinutes(requiredWalking);
                activity.setRelativeStartMinutes(earliestStart);
                activity.setRelativeEndMinutes(end);
                if (changed) {
                    changedItems.add(item);
                    changedIds.add(item.getId());
                }
                affectedMembers.addAll(participants);
            }
            for (Integer memberId : participants) {
                previousEnds.put(memberId, activity.getExecutionEndMinutes() == null ? activity.getRelativeEndMinutes() : activity.getExecutionEndMinutes());
            }
            activities.add(activity);
        }

        List<ActivitySession> waitingSessions = new ArrayList<>();
        for (Integer memberId : planning.memberIds()) {
            for (ActivitySession reference : activitySessionRepository.findAllByVisitMember_IdOrderByIdDesc(memberId)) {
                if (!changedIds.contains(reference.getPlanItem().getId())) {
                    continue;
                }
                ActivitySession session = activitySessionRepository.findActivitySessionByIdForUpdate(reference.getId());
                if (session == null) {
                    throw new ApiException("An activity session changed; retry selecting the restaurant");
                }
                entityManager.refresh(session, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                if (!"WAITING".equals(session.getStatus()) || session.getEnteredAreaAt() != null || session.getQueueStartedAt() != null) {
                    throw new ApiException("Restaurant selection cannot change a started or completed activity");
                }
                if (targetId.equals(session.getPlanItem().getId())) {
                    waitingSessions.add(session);
                }
            }
            for (VisitRecord record : visitRecordRepository.findAllByVisitMember_IdOrderByCreatedAtDescIdDesc(memberId)) {
                if (record.getPlanItem() != null && changedIds.contains(record.getPlanItem().getId())) {
                    throw new ApiException("Restaurant selection cannot change an activity with execution records");
                }
            }
        }

        AiPlanDtoOut candidate = new AiPlanDtoOut();
        candidate.setFeasible(true);
        candidate.setActivities(activities);
        candidate.setWarnings(new ArrayList<>());
        aiPlanService.validatePlanCandidateTiming(candidate, planning.durationMinutes(), planning.memberIds(), planning.tripType());
        aiPlanService.validatePlanCandidateSupervision(candidate, planning.ages());
        aiPlanService.validatePlanCandidateActivityKinds(candidate);
        GenerationSnapshot updatedPlanning = new GenerationSnapshot(writeGenerationJson(input), planning.durationMinutes(), planning.tripType(), planning.memberIds(), planning.ages(), planning.eligibility(), planning.priorities(), planning.pinnedRideIds(), planning.rateablePlaceIds());
        validateInitialCandidateAgainstSnapshot(candidate, updatedPlanning);

        for (int index = 0; index < items.size(); index++) {
            PlanItem item = items.get(index);
            if (!changedIds.contains(item.getId())) {
                continue;
            }
            AiPlanItemDtoOut activity = activities.get(index);
            item.setRelativeStartMinutes(activity.getRelativeStartMinutes());
            item.setRelativeEndMinutes(activity.getRelativeEndMinutes());
            item.setWalkingMinutes(activity.getWalkingMinutes());
            item.setStartsAt(arrivalAt.plusMinutes(activity.getRelativeStartMinutes()));
            item.setEndsAt(arrivalAt.plusMinutes(activity.getRelativeEndMinutes()));
        }
        target.setPlace(restaurant);
        target.setTitle(restaurant.getName());
        target.setIsRateable(true);
        for (VisitMealBreak meal : meals) {
            meal.setPlace(restaurant);
        }
        for (ActivitySession session : waitingSessions) {
            session.setPlace(restaurant);
        }
        planItemRepository.saveAll(changedItems);
        visitMealBreakRepository.saveAll(meals);
        activitySessionRepository.saveAll(waitingSessions);
    }

    private DelayProposalSource lockAndValidateDelayProposalSource(Integer sourceDelayNotificationId, Integer accountId, Integer visitId, Visit visit, VisitPlan plan) {

        Notification delayNotification = null;
        ActivitySession delaySession = null;
        String delaySnapshot = null;

        if (sourceDelayNotificationId != null) {

            delayNotification = notificationRepository.findNotificationByIdForUpdate(sourceDelayNotificationId);

            if (delayNotification == null) {
                throw new ApiException("Source delay notification not found");
            }

            entityManager.refresh(delayNotification, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

            if (!delayNotification.getAccount().getId().equals(accountId) || !"IN_APP".equals(delayNotification.getChannel()) || !"SENT".equals(delayNotification.getStatus()) || !"ACTIVITY_DELAY".equals(delayNotification.getType()) || !"RESPOND_DELAY".equals(delayNotification.getActionType()) || delayNotification.getRespondedAt() != null || delayNotification.getVisit() == null || !delayNotification.getVisit().getId().equals(visitId) || delayNotification.getActivitySession() == null) {
                throw new ApiException("The source delay notification is unavailable or already answered");
            }

            delaySession = activitySessionRepository.findActivitySessionByIdForUpdate(delayNotification.getActivitySession().getId());

            if (delaySession == null) {
                throw new ApiException("Source delay activity session not found");
            }

            entityManager.refresh(delaySession, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            VisitMember delayedMember = delaySession.getVisitMember();
            boolean ownSession = delayedMember.getVisitorProfile() != null && delayedMember.getVisitorProfile().getAccount().getId().equals(accountId);
            boolean managedFamilySession = visit.getOrganizer().getId().equals(accountId) && delayedMember.getVisitorProfile() == null && delayedMember.getFamilyMember() != null && delayedMember.getFamilyMember().getOwnerAccount().getId().equals(accountId);

            if ((!ownSession && !managedFamilySession) || !"GOING".equals(delayedMember.getRsvpStatus()) || !delayedMember.getVisit().getId().equals(visitId) || !delaySession.getPlanItem().getVisitPlan().getId().equals(plan.getId()) || (!"WAITING".equals(delaySession.getStatus()) && !"ACTIVE".equals(delaySession.getStatus())) || (delayNotification.getPlanItem() != null && !delayNotification.getPlanItem().getId().equals(delaySession.getPlanItem().getId()))) {
                throw new ApiException("This delay event no longer matches a current activity for the recipient");
            }

            delaySnapshot = writeGenerationJson(java.util.Arrays.asList(delayNotification.getId(), delaySession.getId(), delaySession.getPlanItem().getId(), delayedMember.getId(), delayNotification.getEventKey()));
        }

        return new DelayProposalSource(delayNotification, delaySession, delaySnapshot);
    }

    private record DelayProposalSource(Notification notification, ActivitySession session, String snapshot) {
    }

    private Set<Integer> appendExecutionSnapshotRows(List<Object> execution, Integer memberId, Set<Integer> itemIds, List<ActivitySession> sessions, List<VisitRecord> records) {

        Set<Integer> startedIds = new HashSet<>();

        for (ActivitySession session : sessions) {

            if (itemIds.contains(session.getPlanItem().getId())) {
                startedIds.add(session.getPlanItem().getId());
                execution.add(java.util.Arrays.asList("SESSION", memberId, session.getId(), session.getPlanItem().getId(), session.getStatus(), session.getEnteredAreaAt(), session.getQueueStartedAt(), session.getCompletedAt(), session.getExpectedWaitMinutes(), session.getExpectedRideMinutes(), session.getBufferMinutes()));
            }
        }

        for (VisitRecord record : records) {

            if (record.getPlanItem() != null && itemIds.contains(record.getPlanItem().getId())) {
                startedIds.add(record.getPlanItem().getId());
                execution.add(java.util.Arrays.asList("RECORD", memberId, record.getId(), record.getPlanItem().getId(), record.getStatus(), record.getCompletedAt(), record.getSkippedAt(), record.getCorrectedAt()));
            }
        }

        return startedIds;
    }

    private void validateReplacementParticipants(JsonNode input, Map<Integer, Map<Integer, String>> eligibility, JsonNode ride, Integer rideId, List<Integer> participants, java.util.function.Function<Integer, Long> countOccurrences, boolean atApproval) {

        Set<String> features = new HashSet<>(readTextArray(ride.path("features"), "Replacement ride features"));

        for (Integer memberId : participants) {

            if (!"ELIGIBLE".equals(eligibility.getOrDefault(rideId, Map.of()).get(memberId))) {
                throw new ApiException(atApproval ? "The replacement no longer has confirmed eligibility for every participant" : "An automatic replacement requires confirmed eligibility for every participant");
            }

            String preference = input.path("ridePreferencesByRideAndMember").path(String.valueOf(rideId)).path(String.valueOf(memberId)).asString("NEUTRAL");

            if ("AVOID".equals(preference)) {
                throw new ApiException(atApproval ? "The replacement conflicts with an avoided ride" : "The replacement conflicts with a participant's avoided ride");
            }

            for (JsonNode member : input.path("members")) {

                if (member.path("visitMemberId").asInt() == memberId && member.path("dislikedFeaturesKnown").asBoolean()) {

                    for (String feature : readTextArray(member.path("dislikedFeatures"), "Disliked features")) {

                        if (features.contains(feature)) {
                            throw new ApiException(atApproval ? "The replacement conflicts with avoidance preferences" : "The replacement conflicts with a participant's avoidance preferences");
                        }
                    }
                }
            }

            if (countOccurrences.apply(memberId) > 1) {
                throw new ApiException(atApproval ? "The replacement repeats a ride without permission" : "The replacement would repeat a ride without explicit permission");
            }
        }
    }

    private void validateReplacementWalking(Map<Integer, List<AiPlanItemDtoOut>> activitiesByMember, Map<Integer, JsonNode> rides, JsonNode executionRules, JsonNode input) {

        Map<Integer, JsonNode> walkingRides = new HashMap<>(rides);

        for (JsonNode node : executionRules.path("canonicalRideNodes")) {
            walkingRides.putIfAbsent(requirePositiveInteger(node, "id"), node);
        }

        Map<Integer, JsonNode> walkingPlaces = indexSnapshotResources(input.path("places"));

        for (List<AiPlanItemDtoOut> memberActivities : activitiesByMember.values()) {

            for (AiPlanItemDtoOut activity : memberActivities) {

                if (activity.getPlaceId() != null && !walkingPlaces.containsKey(activity.getPlaceId())) {
                    throw new ApiException("A fixed place is missing from the verified walking context");
                }
            }
        }

        validateSnapshotWalking(activitiesByMember, walkingRides, walkingPlaces, input.path("walking"));
    }

    private AiPlanItemDtoOut convertPlanItemToAiActivity(PlanItemDtoOut item, List<Integer> participants) {

        AiPlanItemDtoOut activity = new AiPlanItemDtoOut();
        activity.setActivityKind(item.getActivityKind());
        activity.setRideId(item.getRideId());
        activity.setPlaceId(item.getPlaceId());
        activity.setRelativeStartMinutes(item.getRelativeStartMinutes());
        activity.setRelativeEndMinutes(item.getRelativeEndMinutes());
        activity.setWalkingMinutes(item.getWalkingMinutes());
        activity.setWaitingMinutes(item.getWaitingMinutes());
        activity.setActivityMinutes(item.getActivityMinutes());
        activity.setBreakType(item.getBreakType());
        activity.setParticipantVisitMemberIds(participants);
        activity.setSubgroupKey(item.getSubgroupKey());

        return activity;
    }

    private VisitPlan saveGeneratedGroupActivities(Visit visit, PlanHistory history, AiPlanDtoOut candidate, Map<Integer, String> ridePriorities, Set<Integer> pinnedRideIds, Set<Integer> rateablePlaceIds, String inputJson) {

        VisitPlan groupPlan = new VisitPlan();

        groupPlan.setVisit(visit);
        groupPlan.setPlanKind("GROUP");
        groupPlan.setTimingMode("RELATIVE");
        groupPlan.setIsActive(false);
        groupPlan.setGeneratedByHistory(history);
        groupPlan.setExcludedRides(writeGenerationJson(participantJsonMapper.valueToTree(candidate.getExcludedRides() == null ? List.of() : candidate.getExcludedRides())));

        groupPlan = visitPlanRepository.save(groupPlan);

        List<AiPlanItemDtoOut> activities = new ArrayList<>(candidate.getActivities());

        activities.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes).thenComparing(AiPlanItemDtoOut::getRelativeEndMinutes));

        List<PlanItem> items = new ArrayList<>();
        int sortOrder = 0;

        for (AiPlanItemDtoOut activity : activities) {
            boolean isRide = "RIDE".equals(activity.getActivityKind());
            String priority = isRide ? ridePriorities.get(activity.getRideId()) : null;
            boolean pinned = isRide && pinnedRideIds.contains(activity.getRideId());
            boolean isFoodBreak = "MEAL".equals(activity.getBreakType()) || "SNACK".equals(activity.getBreakType());
            boolean rateable = isFoodBreak || (activity.getPlaceId() != null && rateablePlaceIds.contains(activity.getPlaceId()));

            PlanItem item = convertAiActivityToPlanItem(activity, groupPlan, sortOrder, priority, pinned, rateable);

            items.add(item);
            sortOrder++;
        }

        List<PlanItem> savedItems = planItemRepository.saveAll(items);

        groupPlan.setPlanItems(new HashSet<>(savedItems));

        JsonNode savedInput = readGenerationJson(inputJson);
        Map<Integer, List<AiPlanItemDtoOut>> activitiesByMember = new HashMap<>();

        for (AiPlanItemDtoOut activity : activities) {

            for (Integer memberId : activity.getParticipantVisitMemberIds()) {
                activitiesByMember.computeIfAbsent(memberId, key -> new ArrayList<>()).add(activity);
            }
        }

        Map<AiPlanItemDtoOut, String> mealKeys = validateSnapshotMeals(candidate, savedInput.path("mealPolicy"), activitiesByMember);

        for (int index = 0; index < activities.size(); index++) {
            String breakKey = mealKeys.get(activities.get(index));

            if (breakKey != null) {
                visitMealBreakService.bindGeneratedBreak(history.getAccount().getId(), visit.getId(), breakKey, savedItems.get(index).getId());
            }
        }

        return groupPlan;
    }

}
