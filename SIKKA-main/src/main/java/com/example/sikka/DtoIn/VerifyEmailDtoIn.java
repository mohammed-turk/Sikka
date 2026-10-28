package com.example.sikka.DtoIn;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyEmailDtoIn {

    @NotEmpty(message = "email is required")
    @Email(message = "invalid email format")
    private String email;

    @NotEmpty(message = "code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "code must be 6 digits")
    private String code;
}