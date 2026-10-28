package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlanningAccessDtoOut {

    private Integer freeRemaining;

    private Boolean paymentRequired;

    private BigDecimal priceSar;

    private String entitlementStatus;

    private Boolean generationAllowed;
}