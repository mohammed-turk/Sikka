package com.example.sikka.DtoIn;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailDtoIn {

    @NotEmpty(message = "email is required")
    @Email(message = "invalid email format")
    private String email;
}