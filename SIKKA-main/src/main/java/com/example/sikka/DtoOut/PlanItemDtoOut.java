package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlanItemDtoOut {

    private Integer id;

    private String activityKind;

    private String title;

    private Integer rideId;

    private String rideName;

    private Integer placeId;

    private String placeName;

    private Integer relativeStartMinutes;

    private Integer relativeEndMinutes;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private Integer walkingMinutes;

    private Integer waitingMinutes;

    private Integer activityMinutes;

    private String breakType;

    private Boolean isRateable;

    private Integer sortOrder;

    private String priority;

    private Boolean pinned;

    private String participantVisitMemberIds;

    private String subgroupKey;
}