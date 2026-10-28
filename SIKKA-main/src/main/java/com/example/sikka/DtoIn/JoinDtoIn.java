package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinDtoIn {

    @NotEmpty(message = "invite code is required")
    private String code;
}