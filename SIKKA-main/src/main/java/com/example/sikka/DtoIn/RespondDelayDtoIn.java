package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RespondDelayDtoIn {

    @Size(max = 500, message = "Delay reason must not exceed 500 characters")
    private String reason;

    @NotNull(message = "Adjust plan choice is required")
    private Boolean adjustPlan;
}