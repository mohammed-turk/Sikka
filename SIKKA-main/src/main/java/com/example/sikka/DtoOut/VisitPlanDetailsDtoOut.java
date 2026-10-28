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
public class VisitPlanDetailsDtoOut {

    private VisitPlanDtoOut plan;

    private String approvalStatus;

    private List<PlanItemDtoOut> planItems;

    private List<PlanItemDtoOut> proposedPlanItems;

    private ReplanProposalDtoOut pendingProposal;

    private List<ExcludedRideDtoOut> excludedRides;
}