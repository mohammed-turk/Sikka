package com.example.sikka.DtoIn;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDtoIn {

    @NotEmpty(message = "name is required")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @NotEmpty(message = "email is required")
    @Email(message = "invalid email format")
    private String email;

    @NotEmpty(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String password;
}