package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SelectedRideDtoIn {

    @NotNull(message = "ride id is required")
    @Positive(message = "ride id must be positive")
    private Integer rideId;

    @NotEmpty(message = "priority is required")
    @Pattern(regexp = "^(MUST_RIDE|PREFERRED|OPTIONAL)$", message = "priority must be MUST_RIDE, PREFERRED or OPTIONAL")
    private String priority;
}
