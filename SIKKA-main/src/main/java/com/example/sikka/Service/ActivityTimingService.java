package com.example.sikka.Service;

import com.example.sikka.Model.ActivitySession;
import com.example.sikka.Model.PlanItem;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitPlan;
import com.example.sikka.Model.VisitRecord;
import com.example.sikka.Repository.ActivitySessionRepository;
import com.example.sikka.Repository.PlanItemRepository;
import com.example.sikka.Repository.VisitRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ActivityTimingService {

    private final ActivitySessionRepository activitySessionRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitRecordRepository visitRecordRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void advanceRemainingActivitiesAfterCompletion(Visit visit, PlanItem completedItem, LocalDateTime completionNow, Function<String, HashSet<Integer>> participantParser) {

        VisitPlan executionPlan = completedItem.getVisitPlan();

        if (!Boolean.TRUE.equals(completedItem.getPinned()) && !"MEETING_POINT".equals(completedItem.getActivityKind()) && !"PRAYER".equals(completedItem.getBreakType())  && "IN_PROGRESS".equals(visit.getStatus()) && Boolean.TRUE.equals(executionPlan.getIsActive()) && "SCHEDULED".equals(executionPlan.getTimingMode()) && executionPlan.getProposedItems() == null && executionPlan.getPendingScope() == null && executionPlan.getPlanApproval() != null && "APPROVED".equals(executionPlan.getPlanApproval().getStatus()) && completedItem.getStartsAt() != null && completedItem.getEndsAt() != null && completionNow.isBefore(completedItem.getEndsAt())) {

            java.util.Set<Integer> completedParticipants = participantParser.apply(completedItem.getParticipantVisitMemberIds());
            boolean everyoneFinished = !completedParticipants.isEmpty();
            LocalDateTime releasedAt = completedItem.getStartsAt();

            for (Integer participantId : completedParticipants) {

                LocalDateTime participantCompletedAt = null;
                VisitRecord completedRecord = visitRecordRepository.findVisitRecordByVisitMember_IdAndPlanItem_Id(participantId, completedItem.getId());

                if (completedRecord != null && "COMPLETED".equals(completedRecord.getStatus())) {
                    participantCompletedAt = completedRecord.getCompletedAt();
                }

                if (!"RIDE".equals(completedItem.getActivityKind())) {

                    for (ActivitySession completedSession : activitySessionRepository.findAllByVisitMember_IdAndPlanItem_IdOrderByIdAsc(participantId, completedItem.getId())) {

                        if ("COMPLETED".equals(completedSession.getStatus()) && completedSession.getCompletedAt() != null) {
                            participantCompletedAt = completedSession.getCompletedAt();
                        }
                    }
                }

                if (participantCompletedAt == null || participantCompletedAt.isBefore(completedItem.getStartsAt()) || participantCompletedAt.isAfter(completionNow)) {
                    everyoneFinished = false;
                    break;
                }

                if (participantCompletedAt.isAfter(releasedAt)) {
                    releasedAt = participantCompletedAt;
                }
            }

            List<PlanItem> executionItems = planItemRepository.findAllByVisitPlan_Id(executionPlan.getId());
            executionItems.sort(Comparator.comparing(PlanItem::getSortOrder).thenComparing(PlanItem::getId));
            boolean unifiedTimeline = everyoneFinished;

            for (PlanItem executionItem : executionItems) {

                if (!participantParser.apply(executionItem.getParticipantVisitMemberIds()).equals(completedParticipants)) {
                    unifiedTimeline = false;
                    break;
                }
            }

            if (unifiedTimeline && completedItem.getExecutionReleasedAt() == null && visit.getPlannedStartTime() != null) {

                LocalDateTime arrivalAt = visit.getVisitDate().atTime(visit.getPlannedStartTime());
                java.time.Duration sinceArrival = java.time.Duration.between(arrivalAt, completionNow);
                long elapsedSeconds = sinceArrival.getSeconds() + (sinceArrival.getNano() > 0 ? 1 : 0);
                LocalDateTime earliestStart = arrivalAt.plusMinutes(Math.floorDiv(elapsedSeconds + 59, 60));
                long advanceMinutes = java.time.Duration.between(earliestStart, completedItem.getEndsAt()).toMinutes();
                List<PlanItem> movableItems = new ArrayList<>();
                boolean afterCompletedItem = false;

                for (PlanItem executionItem : executionItems) {

                    if (executionItem.getId().equals(completedItem.getId())) {
                        afterCompletedItem = true;
                        continue;
                    }

                    if (!afterCompletedItem) {
                        continue;
                    }

                    if (Boolean.TRUE.equals(executionItem.getPinned()) || "MEETING_POINT".equals(executionItem.getActivityKind()) || "PRAYER".equals(executionItem.getBreakType()) || "MEAL".equals(executionItem.getBreakType()) || "SNACK".equals(executionItem.getBreakType()) || executionItem.getStartsAt() == null || executionItem.getEndsAt() == null || executionItem.getRelativeStartMinutes() == null || executionItem.getRelativeEndMinutes() == null || !arrivalAt.plusMinutes(executionItem.getRelativeStartMinutes()).equals(executionItem.getStartsAt()) || !arrivalAt.plusMinutes(executionItem.getRelativeEndMinutes()).equals(executionItem.getEndsAt())) {
                        break;
                    }

                    boolean alreadyStarted = false;

                    for (Integer participantId : completedParticipants) {

                        if (!activitySessionRepository.findAllByVisitMember_IdAndPlanItem_IdOrderByIdAsc(participantId, executionItem.getId()).isEmpty() || visitRecordRepository.findVisitRecordByVisitMember_IdAndPlanItem_Id(participantId, executionItem.getId()) != null) {
                            alreadyStarted = true;
                            break;
                        }
                    }

                    if (alreadyStarted) {
                        break;
                    }

                    advanceMinutes = Math.min(advanceMinutes, java.time.Duration.between(earliestStart, executionItem.getStartsAt()).toMinutes());
                    movableItems.add(executionItem);
                }

                if (advanceMinutes > 0 && !movableItems.isEmpty()) {

                    completedItem.setExecutionReleasedAt(releasedAt);
                    planItemRepository.save(completedItem);
                    int advance = Math.toIntExact(advanceMinutes);

                    for (PlanItem movableItem : movableItems) {
                        movableItem.setRelativeStartMinutes(movableItem.getRelativeStartMinutes() - advance);
                        movableItem.setRelativeEndMinutes(movableItem.getRelativeEndMinutes() - advance);
                        movableItem.setStartsAt(movableItem.getStartsAt().minusMinutes(advance));
                        movableItem.setEndsAt(movableItem.getEndsAt().minusMinutes(advance));
                    }

                    planItemRepository.saveAll(movableItems);
                }
            }
        }
    }
}
