package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VisitMealBreakDtoOut {

    private Integer id;
    private String breakKey;
    private String mealChoice;
    private String preferredTiming;
    private Integer visitId;
    private Integer visitMemberId;
    private Integer placeId;
    private Integer planItemId;
}