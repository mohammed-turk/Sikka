package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlanApprovalDtoIn {

    @NotNull(message = "Visit plan ID is required")
    @Positive(message = "Visit plan ID must be positive")
    private Integer visitPlanId;

    @jakarta.validation.constraints.Size(max = 36, message = "Proposal key must not exceed 36 characters")
    private String proposalKey;
}
