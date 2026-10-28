package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlanHistoryDtoOut {

    private Integer id;

    private Integer visitId;

    private String requestKey;

    private String operationType;

    private String status;

    private String billingMode;

    private Boolean countsTowardQuota;

    private Integer paymentOrderId;

    private Integer sourceVisitId;

    private List<Integer> visitPlanIds;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String failureReason;
}