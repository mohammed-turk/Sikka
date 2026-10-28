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
public class PlanApprovalDtoOut {

    private Integer id;

    private Integer visitPlanId;

    private String approvalScope;

    private Integer requestedByAccountId;

    private Integer requiredApproverAccountId;

    private Integer approvedByAccountId;

    private String status;

    private LocalDateTime respondedAt;
}