package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.PlanApprovalDtoIn;
import com.example.sikka.DtoOut.PlanApprovalDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.PlanApproval;
import com.example.sikka.Model.PlanItem;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitPlan;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.PlanApprovalRepository;
import com.example.sikka.Repository.VisitPlanRepository;
import com.example.sikka.Repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class PlanApprovalService {

    private final NotificationEventService notificationEventService;
    private final org.springframework.context.ApplicationEventPublisher calendarEventPublisher;
    private final PlanApprovalRepository planApprovalRepository;
    private final VisitPlanRepository visitPlanRepository;
    private final AccountRepository accountRepository;
    private final VisitService visitService;
    private final VisitPlanService visitPlanService;
    private final VisitRepository visitRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly = true)
    public PlanApprovalDtoOut getPlanApproval(Integer accountId, Integer visitPlanId) {

        validateActiveAccount(accountId);

        VisitPlan visitPlan = visitPlanRepository.findVisitPlanById(visitPlanId);

        if (visitPlan == null) {
            throw new ApiException("Visit plan not found");
        }

        PlanApproval approval = planApprovalRepository.findPlanApprovalByVisitPlan_Id(visitPlanId);

        if (approval == null) {
            throw new ApiException("Plan approval not found");
        }

        validateApprovalReadAccess(accountId, visitPlan, approval);

        Integer requestedByAccountId = approval.getRequestedByAccount() == null ? null : approval.getRequestedByAccount().getId();
        Integer requiredApproverAccountId = approval.getRequiredApproverAccount() == null ? null : approval.getRequiredApproverAccount().getId();
        Integer approvedByAccountId = approval.getApprovedByAccount() == null ? null : approval.getApprovedByAccount().getId();

        return new PlanApprovalDtoOut(approval.getId(), approval.getVisitPlan().getId(), approval.getApprovalScope(), requestedByAccountId, requiredApproverAccountId, approvedByAccountId, approval.getStatus(), approval.getRespondedAt());
    }

    @Transactional
    public void createPlanApproval(Integer accountId, Integer visitPlanId) {

        Account account = validateActiveAccount(accountId);

        VisitPlan visitPlan = visitPlanRepository.findVisitPlanByIdForUpdate(visitPlanId);

        if (visitPlan == null) {
            throw new ApiException("Visit plan not found");
        }

        validateInitialGroupApprovalAccess(accountId, visitPlan);
        validatePlanActivities(visitPlan);

        PlanApproval existingApproval = planApprovalRepository.findPlanApprovalByVisitPlan_Id(visitPlanId);

        if (existingApproval != null) {
            throw new ApiException("This plan already has an approval record");
        }

        PlanApproval approval = new PlanApproval();
        approval.setVisitPlan(visitPlan);
        approval.setApprovalScope("GROUP");
        approval.setRequestedByAccount(account);
        approval.setRequiredApproverAccount(visitPlan.getVisit().getOrganizer());
        approval.setApprovedByAccount(null);
        approval.setStatus("PENDING");
        approval.setRespondedAt(null);

        planApprovalRepository.save(approval);

        visitPlan.setPlanApproval(approval);
        notificationEventService.notifyPlanApprovalRequested(approval);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 60)
    public void respondToPlanApproval(Integer accountId, PlanApprovalDtoIn planApprovalDtoIn, int statusInt) {

        if (statusInt != 1 && statusInt != 2) {
            throw new ApiException("Plan approval action must be 1 or 2");
        }
        String status = statusInt == 1 ? "APPROVED" : "REJECTED";


        Account account = validateActiveAccount(accountId);

        if (planApprovalDtoIn == null || planApprovalDtoIn.getVisitPlanId() == null || planApprovalDtoIn.getVisitPlanId() <= 0) {
            throw new ApiException("A positive visit plan ID is required");
        }

        VisitPlan reference = visitPlanRepository.findVisitPlanById(planApprovalDtoIn.getVisitPlanId());

        if (reference == null) {
            throw new ApiException("Visit plan not found");
        }

        Visit visit = visitRepository.findVisitByIdForUpdate(reference.getVisit().getId());

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        entityManager.refresh(visit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        VisitPlan visitPlan = visitPlanRepository.findVisitPlanByIdForUpdate(planApprovalDtoIn.getVisitPlanId());

        if (visitPlan == null) {
            throw new ApiException("Visit plan not found");
        }

        entityManager.refresh(visitPlan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (planApprovalDtoIn.getProposalKey() != null || visitPlan.getProposedItems() != null || visitPlan.getPendingScope() != null || visitPlan.getProposalBaseSnapshot() != null) {
            visitPlanService.respondToReplanProposal(accountId, planApprovalDtoIn, statusInt);
            return;
        }

        validateInitialGroupApprovalAccess(accountId, visitPlan);

        PlanApproval approval = planApprovalRepository.findPlanApprovalByVisitPlan_Id(visitPlan.getId());

        if (approval == null) {
            throw new ApiException("Plan approval not found");
        }

        entityManager.refresh(approval, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!"GROUP".equals(approval.getApprovalScope())) {
            throw new ApiException("Initial group approval must have GROUP scope");
        }

        if (approval.getRequiredApproverAccount() == null || !approval.getRequiredApproverAccount().getId().equals(accountId)) {
            throw new ApiException("You are not the required approver");
        }

        if (!"PENDING".equals(approval.getStatus())) {
            throw new ApiException("This approval has already been answered");
        }

        if ("APPROVED".equals(status)) {

            validatePlanActivities(visitPlan);

            for (VisitPlan previous : visitPlanRepository.findAllByVisit_Id(visit.getId())) {
                if (!previous.getId().equals(visitPlan.getId()) && Boolean.TRUE.equals(previous.getIsActive())) {
                    previous.setIsActive(false);
                    visitPlanRepository.save(previous);
                }
            }
            visitPlan.setIsActive(true);
            visit.setPlannedStartTime(null);
            visit.setPlannedEndTime(null);

            if (!"READY".equals(visit.getStatus())) visitService.changeStatus(visitPlan.getVisit().getId(), "READY");
        }

        approval.setStatus(status);
        approval.setApprovedByAccount(account);
        approval.setRespondedAt(LocalDateTime.now(ZoneId.of("Asia/Riyadh")));

        planApprovalRepository.save(approval);
        notificationEventService.notifyPlanApprovalResult(approval);
        if ("APPROVED".equals(status) && "SCHEDULED".equals(visitPlan.getTimingMode())) calendarEventPublisher.publishEvent(new CalendarSyncOnApproval.ApprovedVisitChanged(accountId, visitPlan.getVisit().getId()));
    }

    private Account validateActiveAccount(Integer accountId) {

        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        return account;
    }

    private void validateInitialGroupApprovalAccess(Integer accountId, VisitPlan visitPlan) {

        Visit visit = visitPlan.getVisit();

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can submit or respond to the initial group approval");
        }

        if (!"GROUP".equals(visitPlan.getPlanKind())) {
            throw new ApiException("Initial approval is only supported for group plans");
        }

        if (!"PLANNING".equals(visit.getStatus()) && !("READY".equals(visit.getStatus()) && visit.getStartedAt() == null)) {
            throw new ApiException("The visit must be PLANNING for initial approval");
        }

        if (visitPlan.getProposedItems() != null || visitPlan.getPendingScope() != null) {
            throw new ApiException("Replanning proposals must use the replanning approval flow");
        }

        if (Boolean.TRUE.equals(visitPlan.getIsActive())) {
            throw new ApiException("This plan is already active");
        }
    }

    private void validateApprovalReadAccess(Integer accountId, VisitPlan visitPlan, PlanApproval approval) {

        if ("INDIVIDUAL".equals(visitPlan.getPlanKind())) {

            VisitMember owner = visitPlan.getOwnerVisitMember();

            if (owner == null || owner.getVisitorProfile() == null) {
                throw new ApiException("Individual plan owner not found");
            }

            if (!owner.getVisit().getId().equals(visitPlan.getVisit().getId())) {
                throw new ApiException("The plan owner does not belong to this visit");
            }

            if (!owner.getVisitorProfile().getAccount().getId().equals(accountId)) {
                throw new ApiException("Only the plan owner can view this individual approval");
            }

            return;
        }

        if (!"GROUP".equals(visitPlan.getPlanKind())) {
            throw new ApiException("Invalid plan kind");
        }

        boolean isOrganizer = visitPlan.getVisit().getOrganizer().getId().equals(accountId);
        boolean isRequester = approval.getRequestedByAccount() != null && approval.getRequestedByAccount().getId().equals(accountId);

        if (!isOrganizer && !isRequester) {
            throw new ApiException("You cannot view this approval");
        }
    }

    private void validatePlanActivities(VisitPlan visitPlan) {

        if (visitPlan.getPlanItems() == null || visitPlan.getPlanItems().isEmpty()) {
            throw new ApiException("An empty plan cannot be submitted or approved");
        }

        String timingMode = visitPlan.getTimingMode();

        if (!"RELATIVE".equals(timingMode) && !"SCHEDULED".equals(timingMode)) {
            throw new ApiException("Invalid plan timing mode");
        }

        for (PlanItem planItem : visitPlan.getPlanItems()) {

            if (planItem.getVisitPlan() == null || !planItem.getVisitPlan().getId().equals(visitPlan.getId())) {
                throw new ApiException("An activity does not belong to this plan");
            }

            if ("RELATIVE".equals(timingMode)) {

                if (planItem.getRelativeStartMinutes() == null || planItem.getRelativeEndMinutes() == null) {
                    throw new ApiException("Relative activities must have start and end offsets");
                }

                if (planItem.getRelativeStartMinutes() < 0 || planItem.getRelativeEndMinutes() <= planItem.getRelativeStartMinutes()) {
                    throw new ApiException("Relative activity end must be after its non-negative start");
                }

                if (planItem.getStartsAt() != null || planItem.getEndsAt() != null) {
                    throw new ApiException("Relative activities must not contain scheduled times");
                }

            } else {

                if (planItem.getStartsAt() == null || planItem.getEndsAt() == null) {
                    throw new ApiException("Scheduled activities must have start and end times");
                }

                if (!planItem.getEndsAt().isAfter(planItem.getStartsAt())) {
                    throw new ApiException("Activity end time must be after its start time");
                }
            }

            String activityKind = planItem.getActivityKind();

            if ("RIDE".equals(activityKind)) {

                if (planItem.getRide() == null) {
                    throw new ApiException("A ride activity must reference a ride");
                }

                if (!PlanningAvailability.canPlanRide(planItem.getRide(), visitPlan.getVisit().getVisitDate())) {
                    throw new ApiException("The plan contains a ride that is not open");
                }

            } else if ("PLACE".equals(activityKind) || "MEETING_POINT".equals(activityKind)) {

                if (planItem.getPlace() == null) {
                    throw new ApiException("A place or meeting activity must reference a place");
                }

            } else if (!"BREAK".equals(activityKind)) {
                throw new ApiException("Invalid activity kind");
            }
        }
    }

}