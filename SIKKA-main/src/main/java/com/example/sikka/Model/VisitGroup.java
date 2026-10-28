package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class VisitGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "varchar(150) not null")
    private String name;

    @Column(columnDefinition = "boolean not null default true")
    private Boolean invitationsEnabled = true;

    @Column(columnDefinition = "varchar(255) unique")
    private String inviteCodeHash;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;



    @ManyToOne
    @JoinColumn(name = "organizer_account_id", nullable = false)
    @JsonIgnore
    private Account organizer;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "group")
    private Visit visit;
}