package com.example.sikka.DtoIn;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordDtoIn {

    @NotEmpty(message = "email is required")
    @Email(message = "invalid email format")
    private String email;

    @NotEmpty(message = "code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "code must be 6 digits")
    private String code;

    @NotEmpty(message = "new password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String newPassword;
}