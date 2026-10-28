package com.example.sikka.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull(message = "Birth date is required")
    @PastOrPresent(message = "Birth date cannot be in the future")
    @Column(nullable = false)
    private LocalDate birthDate;

    @Positive(message = "Height must be positive")
    @Digits(integer = 3, fraction = 2, message = "Invalid height")
    @Column(precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Positive(message = "Weight must be positive")
    @Digits(integer = 4, fraction = 2, message = "Invalid weight")
    @Column(precision = 6, scale = 2)
    private BigDecimal weightKg;

    @NotBlank(message = "Gender is required")
    @Pattern(
            regexp = "^(MALE|FEMALE)$",
            message = "Gender must be MALE or FEMALE"
    )
    @Column(nullable = false, length = 10)
    private String gender;

    @ManyToOne
    @JoinColumn(name = "owner_account_id", nullable = false)
    @JsonIgnore
    private Account ownerAccount;

    @ManyToOne
    @JoinColumn(name = "visitor_profile_id")
    @JsonIgnore
    private VisitorProfile linkedVisitorProfile;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}