package com.example.sikka.Service;

import com.example.sikka.DtoOut.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VisitPlanMapService {
    private final VisitPlanService visitPlanService;
    private final MapService mapService;

    @Transactional(readOnly = true)
    public Map<String, Object> getVisitPlanMapRoute(Integer accountId, Integer visitPlanId) {
        VisitPlanDetailsDtoOut details = visitPlanService.getVisitPlanDetails(accountId, visitPlanId);
        List<PlanItemDtoOut> ordered = new ArrayList<>(details.getPlanItems());
        ordered.sort(Comparator.comparing(PlanItemDtoOut::getRelativeStartMinutes).thenComparing(PlanItemDtoOut::getRelativeEndMinutes));
        List<Map<String, Object>> segments = new ArrayList<>();
        Map<Integer, PlanItemDtoOut> previousByMember = new TreeMap<>();
        for (PlanItemDtoOut item : ordered) {
            String type = "RIDE".equals(item.getActivityKind()) ? "RIDE" : item.getPlaceId() != null ? "PLACE" : null;
            if (type == null) continue;
            List<Integer> participants;
            try {
                participants = Arrays.asList(tools.jackson.databind.json.JsonMapper.builder().build().readValue(item.getParticipantVisitMemberIds(), Integer[].class));
            } catch (Exception invalidParticipants) {
                throw new com.example.sikka.Api.ApiException("Saved activity participants are invalid");
            }
            for (Integer memberId : participants) {
                PlanItemDtoOut previous = previousByMember.get(memberId);
                Map<String, Object> segment = new LinkedHashMap<>();
                segment.put("visitMemberId", memberId);
                segment.put("fromPlanItemId", previous == null ? null : previous.getId());
                segment.put("toPlanItemId", item.getId());
                segment.put("fromEntrance", previous == null);
                segment.put("estimated", true);
                segment.put("entrancesVerified", mapService.getMapData().path("entrancesVerified").asBoolean(false));
                Integer toId = "RIDE".equals(type) ? item.getRideId() : item.getPlaceId();
                try {
                    String fromType = previous == null ? "ENTRANCE" : "RIDE".equals(previous.getActivityKind()) ? "RIDE" : "PLACE";
                    Integer fromId = previous == null ? null : "RIDE".equals(fromType) ? previous.getRideId() : previous.getPlaceId();
                    MapRouteDtoOut route = previous == null ? mapService.getEntranceRoute(type, toId) : mapService.getRoute(fromType, fromId, type, toId);
                    segment.put("route", route);
                    segment.put("available", true);
                } catch (com.example.sikka.Api.ApiException unavailable) {
                    segment.put("available", false);
                    segment.put("reason", "NO_VERIFIED_ROUTE");
                }
                segments.add(segment);
                previousByMember.put(memberId, item);
            }
        }
        return Map.of("visitPlanId", visitPlanId, "planItems", ordered, "segments", segments, "routeScope", "PER_MEMBER", "includesEntranceRoute", true);
    }
}
