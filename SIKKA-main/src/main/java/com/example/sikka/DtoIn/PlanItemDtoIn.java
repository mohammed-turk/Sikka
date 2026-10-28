package com.example.sikka.DtoIn;

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
public class PlanItemDtoIn {

    @NotNull(message = "Plan item ID is required")
    @Positive(message = "Plan item ID must be positive")
    private Integer planItemId;

    @Pattern(regexp = "^(MUST_RIDE|PREFERRED|OPTIONAL)$", message = "Priority must be MUST_RIDE, PREFERRED or OPTIONAL")
    private String priority;

    @NotNull(message = "Pinned status is required")
    private Boolean pinned;
}