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
public class NotificationDtoOut {

    private Integer id;

    private Integer visitId;

    private Integer activitySessionId;

    private Integer planItemId;

    private String channel;

    private String type;

    private String title;

    private String message;

    private String status;

    private String eventKey;

    private String actionType;

    private String response;

    private LocalDateTime respondedAt;

    private LocalDateTime scheduledAt;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;

    private LocalDateTime readAt;
}
