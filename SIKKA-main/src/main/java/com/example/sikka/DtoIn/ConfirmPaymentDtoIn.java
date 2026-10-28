package com.example.sikka.DtoIn;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmPaymentDtoIn {

    @NotBlank(message = "payment id is required")
    private String paymentId;
}