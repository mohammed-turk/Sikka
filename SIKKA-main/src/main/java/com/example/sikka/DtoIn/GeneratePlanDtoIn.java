package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GeneratePlanDtoIn {

    @NotBlank(message = "Request key is required")
    @Size(max = 80, message = "Request key must not exceed 80 characters")
    private String requestKey;
}