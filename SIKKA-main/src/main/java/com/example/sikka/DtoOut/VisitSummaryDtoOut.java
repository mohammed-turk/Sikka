package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VisitSummaryDtoOut {

    private Integer visitId;

    private Integer visitMemberId;

    private Integer completedRidesCount;

    private Integer uniqueCompletedRidesCount;

    private Integer repeatedRidesCount;

    private Integer skippedRidesCount;

    private Integer ratingsCount;

    private Double averageRating;

    private Integer completedOtherActivitiesCount;

    private Integer skippedOtherActivitiesCount;

    private Integer otherActivitiesRatingsCount;

    private Double averageOtherActivitiesRating;
}