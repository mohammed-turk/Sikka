package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDtoOut {
    private Integer id;
    private String name;
    private String email;
    private String role;
    private String status;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private LocalDateTime createdAt;
}