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
public class UpdateRideStatusDtoIn {

    @NotBlank(message = "Ride status is required")
    @Pattern(regexp = "^(OPEN|CLOSED|UNAVAILABLE)$", message = "Status must be OPEN, CLOSED or UNAVAILABLE")
    private String status;
}
