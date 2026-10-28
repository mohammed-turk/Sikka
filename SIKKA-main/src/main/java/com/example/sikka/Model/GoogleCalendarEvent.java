package com.example.sikka.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "google_calendar_event", uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "visit_id"}))
public class GoogleCalendarEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(optional = false)
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @Column(nullable = false)
    private String googleEventId;
}
