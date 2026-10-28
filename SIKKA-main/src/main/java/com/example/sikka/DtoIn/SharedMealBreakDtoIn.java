package com.example.sikka.DtoIn;

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
public class SharedMealBreakDtoIn {

    @NotEmpty(message = "Visit member IDs are required")
    private List<@NotNull @Positive Integer> visitMemberIds;

    @NotBlank(message = "Meal choice is required")
    @Pattern(
            regexp = "^(MEAL|SNACK)$",
            message = "Shared break must be MEAL or SNACK"
    )
    private String mealChoice;

    @NotBlank(message = "Preferred timing is required")
    @Pattern(
            regexp = "^(START|MIDDLE|END)$",
            message = "Preferred timing must be START, MIDDLE or END"
    )
    private String preferredTiming;
}