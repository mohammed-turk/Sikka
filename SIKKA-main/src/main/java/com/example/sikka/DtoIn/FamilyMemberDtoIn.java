package com.example.sikka.DtoIn;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FamilyMemberDtoIn {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Birth date is required")
    @PastOrPresent(message = "Birth date cannot be in the future")
    private LocalDate birthDate;

    @Positive(message = "Height must be positive")
    @Digits(integer = 3, fraction = 2, message = "Invalid height")
    private BigDecimal heightCm;

    @Positive(message = "Weight must be positive")
    @Digits(integer = 4, fraction = 2, message = "Invalid weight")
    private BigDecimal weightKg;

    @NotBlank(message = "Gender is required")
    @Pattern(
            regexp = "^(MALE|FEMALE)$",
            message = "Gender must be MALE or FEMALE"
    )
    private String gender;

    @Positive(message = "Linked visitor profile ID must be positive")
    private Integer linkedVisitorProfileId;

}