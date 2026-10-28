package com.example.sikka.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "google_calendar_connection")
public class GoogleCalendarConnection {
    @Id
    private Integer accountId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(length = 2048, nullable = false)
    private String encryptedRefreshToken;
}
