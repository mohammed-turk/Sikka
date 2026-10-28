package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class VisitorProfileDtoOut {
    public VisitorProfileDtoOut(Integer id, Integer accountId, String name, LocalDate birthDate, BigDecimal heightCm, BigDecimal weightKg, String gender, String thrillPreference, String preferredRideTypes, String dislikedRideFeatures, String foodPreferences, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.birthDate = birthDate;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.gender = gender;
        this.thrillPreference = thrillPreference;
        this.preferredRideTypes = preferredRideTypes;
        this.dislikedRideFeatures = dislikedRideFeatures;
        this.foodPreferences = foodPreferences;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private Integer id;
    private Integer accountId;
    private String name;
    private LocalDate birthDate;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private String gender;
    private String thrillPreference;
    private String preferredRideTypes;
    private String dislikedRideFeatures;
    private String foodPreferences;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String healthData;
}