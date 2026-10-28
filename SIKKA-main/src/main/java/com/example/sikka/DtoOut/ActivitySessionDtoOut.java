package com.example.sikka.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ActivitySessionDtoOut {

    private Integer id;

    private Integer visitMemberId;

    private Integer planItemId;

    private Integer rideId;

    private Integer placeId;

    private String status;

    private String trackingMode;

    private LocalDateTime enteredAreaAt;

    private LocalDateTime queueStartedAt;

    private LocalDateTime completedAt;

    private Integer expectedWaitMinutes;

    private Integer expectedRideMinutes;

    private Integer bufferMinutes;

    private LocalDateTime lastLocationAt;

    private String delayReason;

    private LocalDateTime delayRespondedAt;

    private Boolean isRateable;

    private Integer rating;

    private String reviewNote;
}