package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VisitMealBreakDtoIn {

    @NotBlank(message = "Meal choice is required")
    @Pattern(
            regexp = "^(MEAL|SNACK|DEFERRED|SKIPPED|NONE)$",
            message = "Meal choice must be MEAL, SNACK, DEFERRED, SKIPPED or NONE"
    )
    private String mealChoice;

    @Pattern(
            regexp = "^(START|MIDDLE|END)$",
            message = "Preferred timing must be START, MIDDLE or END"
    )
    private String preferredTiming;
}