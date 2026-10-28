package com.example.sikka.DtoIn;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MealPreferencesDtoIn {
    @NotBlank
    @Pattern(regexp = "^(NONE|ONE_MEAL|TWO_MEALS|TWO_SNACKS|SNACK|MEAL_AND_SNACK|DEFERRED|SKIPPED)$")
    private String choice;

    private List<@Positive Integer> restaurantIds;

    public MealPreferencesDtoIn(String choice, List<String> timings) {
        this.choice = choice;
        this.timings = timings;
    }

    private List<@Pattern(regexp = "^(START|MIDDLE|END)$") String> timings;
}
