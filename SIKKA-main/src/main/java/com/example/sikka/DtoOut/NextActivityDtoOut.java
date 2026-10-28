package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NextActivityDtoOut {

    private Integer visitId;

    private String activityStatus;

    private String timingMode;

    private PlanItemDtoOut activity;

    private ActivitySessionDtoOut activitySession;
}