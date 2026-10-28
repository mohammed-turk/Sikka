package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReplanProposalDtoOut {

    private Integer visitPlanId;

    private Integer planApprovalId;

    private String approvalScope;

    private String status;

    private Integer requestedByAccountId;

    private Integer requiredApproverAccountId;

    private List<Integer> affectedMemberIds;
    private String proposalKey;
}
