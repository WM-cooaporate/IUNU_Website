package com.iunu.realestate.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Submission from the public "Request A Quote" form. */
@Entity
@Table(name = "quote_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 190)
    private String email;

    /** residential | commercial | administrative */
    @Column(nullable = false, length = 30)
    private String project;

    @Column(length = 30)
    private String whatsapp;

    /** apartment | villa | office | commercial */
    @Column(nullable = false, length = 30)
    private String spaceType;

    @Builder.Default
    @Column(nullable = false)
    private boolean handled = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
