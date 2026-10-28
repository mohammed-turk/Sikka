package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GenerateVisitPlansDtoOut {

    private Integer visitId;
    private String planStatus;
    private Integer individualPlansCount;
    private Integer groupPlanId;
    private Integer planHistoryId;
    private String requestStatus;
    private String failureReason;
    private AiPlanDtoOut generatedResult;

}
