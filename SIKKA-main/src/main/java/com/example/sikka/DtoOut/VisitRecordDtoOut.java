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
public class VisitRecordDtoOut {

    private Integer id;

    private String requestKey;

    private Integer visitMemberId;

    private Integer planItemId;

    private Integer activitySessionId;

    private Integer rideId;

    private String rideName;

    private Integer recordedByAccountId;

    private String status;

    private LocalDateTime completedAt;

    private LocalDateTime skippedAt;

    private Integer rating;

    private String reviewNote;

    private LocalDateTime correctedAt;

    private LocalDateTime createdAt;
}