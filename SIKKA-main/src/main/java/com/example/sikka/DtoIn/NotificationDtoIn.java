package com.example.sikka.DtoIn;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDtoIn {

    @Positive(message = "Visit ID must be positive")
    private Integer visitId;

    @NotBlank(message = "Notification channel is required")
    @Pattern(regexp = "^(IN_APP|EMAIL|WHATSAPP)$", message = "Channel must be IN_APP, EMAIL or WHATSAPP")
    private String channel;

    @NotBlank(message = "Notification type is required")
    @Size(max = 60, message = "Notification type must not exceed 60 characters")
    private String type;

    @Size(max = 180, message = "Title must not exceed 180 characters")
    private String title;

    @NotBlank(message = "Notification message is required")
    private String message;

    private LocalDateTime scheduledAt;
}