package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PreviewDtoOut {

    private Integer visitId;

    private LocalDate visitDate;

    private String tripType;

    private Integer durationMinutes;

    private List<MemberPreview> members;

    private List<String> missingFields;

    private List<String> warnings;

    private Integer freeRemaining;

    private Boolean paymentRequired;

    private BigDecimal priceSar;

    private String entitlementStatus;

    private Boolean readyToGenerate;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemberPreview {

        private Integer visitMemberId;

        private String name;

        private String answerStatus;

        private List<String> missingFields;

        private List<String> warnings;
    }
}