package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.PlanItemDtoIn;
import com.example.sikka.DtoOut.PlanItemDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.PlanItem;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitPlan;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.PlanItemRepository;
import com.example.sikka.Repository.VisitPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanItemService {

    private final PlanItemRepository planItemRepository;
    private final VisitPlanRepository visitPlanRepository;
    private final AccountRepository accountRepository;
    private final VisitPlanService visitPlanService;
    private final PaymentOrderService paymentOrderService;

    @Transactional(readOnly = true)
    public List<PlanItemDtoOut> getPlanItems(Integer accountId, Integer visitPlanId) {
        return visitPlanService.getVisitPlanDetails(accountId, visitPlanId).getPlanItems();
    }

    @Transactional(readOnly = true)
    public PlanItemDtoOut getPlanItemDetails(Integer accountId, Integer planItemId) {

        PlanItem planItem = visitPlanService.getCanonicalExecutionPlanItem(accountId, planItemId);

        Integer rideId = null;
        String rideName = null;
        Integer placeId = null;
        String placeName = null;

        if (planItem.getRide() != null) {
            rideId = planItem.getRide().getId();
            rideName = planItem.getRide().getName();
        }

        if (planItem.getPlace() != null) {
            placeId = planItem.getPlace().getId();
            placeName = planItem.getPlace().getName();
        }

        boolean isRide = "RIDE".equals(planItem.getActivityKind());
        boolean isPrayer = "PRAYER".equals(planItem.getBreakType());
        boolean isRateable = !isRide && !isPrayer && Boolean.TRUE.equals(planItem.getIsRateable());

        return new PlanItemDtoOut(planItem.getId(), planItem.getActivityKind(), planItem.getTitle(), rideId, rideName, placeId, placeName, planItem.getRelativeStartMinutes(), planItem.getRelativeEndMinutes(), planItem.getStartsAt(), planItem.getEndsAt(), planItem.getWalkingMinutes(), planItem.getWaitingMinutes(), planItem.getActivityMinutes(), planItem.getBreakType(), isRateable, planItem.getSortOrder(), planItem.getPriority(), planItem.getPinned(), planItem.getParticipantVisitMemberIds(), planItem.getSubgroupKey());
    }

    @Transactional
    public void updatePlanItemPreferences(Integer accountId, PlanItemDtoIn planItemDtoIn) {

        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        if (planItemDtoIn == null || planItemDtoIn.getPlanItemId() == null || planItemDtoIn.getPlanItemId() <= 0) {
            throw new ApiException("A positive plan item ID is required");
        }

        PlanItem planItem = planItemRepository.findPlanItemById(planItemDtoIn.getPlanItemId());

        if (planItem == null) {
            throw new ApiException("Plan item not found");
        }

        VisitPlan visitPlan = visitPlanRepository.findVisitPlanByIdForUpdate(planItem.getVisitPlan().getId());

        if (visitPlan == null) {
            throw new ApiException("Visit plan not found");
        }

        Visit visit = visitPlan.getVisit();
        if (visit.getReusedFromVisitId() != null && !paymentOrderService.hasPaidVisit(visit.getOrganizer().getId(), visit.getId())) throw new ApiException("Pay before editing repeated visit plan items");

        if ("GROUP".equals(visitPlan.getPlanKind())) {

            if (!visit.getOrganizer().getId().equals(accountId)) {
                throw new ApiException("Only the visit organizer can change group activity preferences");
            }

        } else if ("INDIVIDUAL".equals(visitPlan.getPlanKind())) {

            VisitMember owner = visitPlan.getOwnerVisitMember();

            if (owner == null) {
                throw new ApiException("Individual plan owner not found");
            }

            if (!owner.getVisit().getId().equals(visit.getId())) {
                throw new ApiException("The plan owner does not belong to this visit");
            }

            if (owner.getVisitorProfile() == null || !owner.getVisitorProfile().getAccount().getId().equals(accountId)) {
                throw new ApiException("Only the plan owner can change individual activity preferences");
            }

            if (!"GOING".equals(owner.getRsvpStatus())) {
                throw new ApiException("The plan owner must be GOING");
            }

            if (visitPlanRepository.existsByVisit_IdAndPlanKind(visit.getId(), "GROUP")) {
                throw new ApiException("Use replanning to change preferences after the group plan is created");
            }

        } else {
            throw new ApiException("Invalid plan kind");
        }

        if (!"PLANNING".equals(visit.getStatus())) {
            throw new ApiException("Activity preferences cannot be changed in the current visit status");
        }

        if (Boolean.TRUE.equals(visitPlan.getIsActive())) {
            throw new ApiException("Active plans must be changed through replanning");
        }

        if (visitPlan.getPlanApproval() != null) {
            throw new ApiException("Use the approval flow before changing a submitted plan");
        }

        if (visitPlan.getProposedItems() != null) {
            throw new ApiException("A plan with a pending proposal cannot be edited directly");
        }

        String priority = planItemDtoIn.getPriority();

        if (priority != null && !"MUST_RIDE".equals(priority) && !"PREFERRED".equals(priority) && !"OPTIONAL".equals(priority)) {
            throw new ApiException("Priority must be MUST_RIDE, PREFERRED or OPTIONAL");
        }

        if (priority != null && !"RIDE".equals(planItem.getActivityKind())) {
            throw new ApiException("Priority can only be assigned to ride activities");
        }

        if (planItemDtoIn.getPinned() == null) {
            throw new ApiException("Pinned status is required");
        }

        planItem.setPriority(priority);
        planItem.setPinned(planItemDtoIn.getPinned());

        planItemRepository.save(planItem);
    }

}