package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateRideWaitDtoIn {

    @NotNull(message = "Waiting time is required")
    @PositiveOrZero(message = "Waiting time cannot be negative")
    private Integer waitMinutes;

    @NotBlank(message = "Wait source is required")
    @Size(max = 255, message = "Wait source must not exceed 255 characters")
    private String waitSource;

    @NotNull(message = "Observation time is required")
    @PastOrPresent(message = "Observation time cannot be in the future")
    private LocalDateTime waitObservedAt;
}
