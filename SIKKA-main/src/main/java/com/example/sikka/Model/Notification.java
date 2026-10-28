package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_notification_account_channel_event", columnNames = {"account_id", "channel", "event_key"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Notification channel is required")
    @Pattern(regexp = "^(IN_APP|EMAIL|WHATSAPP)$", message = "Channel must be IN_APP, EMAIL or WHATSAPP")
    @Column(columnDefinition = "VARCHAR(20) not null")
    private String channel;

    @NotBlank(message = "Notification type is required")
    @Size(max = 60, message = "Notification type must not exceed 60 characters")
    @Column(columnDefinition = "VARCHAR(60) not null")
    private String type;

    @Size(max = 180, message = "Title must not exceed 180 characters")
    @Column(columnDefinition = "VARCHAR(180)")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @NotNull(message = "Notification status is required")
    @Pattern(regexp = "^(PENDING|SENT|FAILED)$", message = "Status must be PENDING, SENT or FAILED")
    @Column(columnDefinition = "VARCHAR(20) not null")
    private String status = "PENDING";

    private LocalDateTime readAt;

    private LocalDateTime scheduledAt;

    @Size(max = 150, message = "Event key must not exceed 150 characters")
    @Column(name = "event_key", length = 150)
    private String eventKey;

    @Size(max = 60, message = "Action type must not exceed 60 characters")
    @Column(length = 60)
    private String actionType;

    @Column(columnDefinition = "JSON")
    private String response;

    private LocalDateTime respondedAt;

    private LocalDateTime sentAt;

    @Size(max = 255, message = "Provider reference must not exceed 255 characters")
    @Column(length = 255)
    private String providerReference;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

     @ManyToOne
     @JoinColumn(name = "account_id", nullable = false)
     @JsonIgnore
     private Account account;

     @ManyToOne
     @JoinColumn(name = "visit_id")
     @JsonIgnore
     private Visit visit;

    @ManyToOne
    @JoinColumn(name = "activity_session_id")
    @JsonIgnore
    private ActivitySession activitySession;

    @ManyToOne
    @JoinColumn(name = "plan_item_id")
    @JsonIgnore
    private PlanItem planItem;

}