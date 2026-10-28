package com.example.sikka.DtoIn;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProfileDtoIn {

    @NotEmpty(message = "name is required")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @Past(message = "birth date must be in the past")
    private LocalDate birthDate;

    @Positive(message = "height must be positive")
    private BigDecimal heightCm;

    @Positive(message = "weight must be positive")
    private BigDecimal weightKg;

    @Pattern(regexp = "^(MALE|FEMALE)$", message = "gender must be MALE or FEMALE")
    private String gender;

    @Pattern(regexp = "^(LOW|MILD|EXTREME)$", message = "thrill preference must be LOW, MILD or EXTREME")
    private String thrillPreference;

    private String preferredRideTypes;

    private String dislikedRideFeatures;

    private String foodPreferences;

    private String healthData;
}