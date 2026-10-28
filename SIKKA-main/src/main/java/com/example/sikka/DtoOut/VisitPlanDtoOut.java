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
public class VisitPlanDtoOut {

    private Integer id;

    private Integer visitId;

    private String planKind;

    private String timingMode;

    private Integer ownerVisitMemberId;

    private String ownerName;

    private Boolean isActive;

    private Integer generatedByHistoryId;

    private String pendingScope;

    private Integer requestedByVisitMemberId;

    private String affectedMemberIds;

    private LocalDateTime createdAt;
}