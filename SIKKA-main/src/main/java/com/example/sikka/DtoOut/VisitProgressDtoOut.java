package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VisitProgressDtoOut {

    private Integer visitId;

    private Integer plannedRidesCount;

    private Integer completedPlannedRidesCount;

    private Integer remainingRidesCount;

    private Double completionPercentage;

    private Integer plannedActivitiesCount;

    private Integer completedActivitiesCount;

    private Integer skippedActivitiesCount;

    private Integer remainingActivitiesCount;

    private Double activityCompletionPercentage;
}