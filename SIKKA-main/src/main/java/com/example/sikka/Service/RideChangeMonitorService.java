package com.example.sikka.Service;

import com.example.sikka.Model.PlanItem;
import com.example.sikka.Repository.PlanItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideChangeMonitorService {

    private final PlanItemRepository planItemRepository;
    private final NotificationService notificationService;

    @Value("${sikka.replan.ride-monitor-enabled:true}")
    private boolean monitorEnabled;

    private Integer nextPlanItemId = 0;

    @Scheduled(fixedDelayString = "${sikka.replan.ride-check-interval-ms:60000}", initialDelayString = "${sikka.replan.ride-initial-delay-ms:45000}")
    public void checkPlannedRideChanges() {
        if (!monitorEnabled) {
            return;
        }
        Integer afterId = nextPlanItemId;
        for (int page = 0; page < 10; page++) {
            List<PlanItem> candidates = planItemRepository.findRideChangeCandidates(afterId, PageRequest.of(0, 100));
            if (candidates.isEmpty()) {
                nextPlanItemId = 0;
                return;
            }
            for (PlanItem item : candidates) {
                afterId = item.getId();
                nextPlanItemId = afterId;
                try {
                    notificationService.notifyActivityRideChange(item.getId());
                } catch (RuntimeException exception) {
                    log.warn("Could not check ride changes for plan item {}", item.getId(), exception);
                }
            }
        }
    }
}
