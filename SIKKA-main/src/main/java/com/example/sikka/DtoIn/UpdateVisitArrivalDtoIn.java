package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateVisitArrivalDtoIn {

    @NotNull(message = "Expected arrival time is required")
    private LocalTime expectedArrivalTime;

    @Valid
    private MealPreferencesDtoIn mealPreferences;

    private java.time.LocalDate visitDate;

    public UpdateVisitArrivalDtoIn(LocalTime expectedArrivalTime, MealPreferencesDtoIn mealPreferences) {
        this.expectedArrivalTime = expectedArrivalTime;
        this.mealPreferences = mealPreferences;
    }
}