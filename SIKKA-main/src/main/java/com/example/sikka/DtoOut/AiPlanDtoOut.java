package com.example.sikka.DtoOut;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AiPlanDtoOut {

    @NotNull(message = "Plan feasibility is required")
    private Boolean feasible;

    @NotNull(message = "Plan activities are required")
    @Valid
    private List<@NotNull AiPlanItemDtoOut> activities;

    @NotNull(message = "Warnings are required")
    private List<@NotBlank @Size(max = 500) String> warnings;

    @Size(max = 1000, message = "Infeasibility reason must not exceed 1000 characters")
    private String infeasibilityReason;

    private List<ExcludedRideDtoOut> excludedRides;
}