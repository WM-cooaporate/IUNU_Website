package com.iunu.realestate.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A development featured on the public "Projects" page.
 *
 * Only {@code title} is mandatory - the remaining descriptive fields are
 * nullable so an admin can save a draft early and fill in the copy later.
 * New projects start unpublished ({@code published = false}) and are
 * invisible to the public API until an admin publishes them.
 */
@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_projects_published_created_at", columnList = "published, createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProjectStatus status;

    /**
     * Free text rather than a number pair ("From 4,500,000 EGP", "On request").
     * Marketing copy for this varies too much to model numerically, and the
     * public site only ever renders it as-is.
     */
    @Column(length = 100)
    private String priceRange;

    /** Set by POST /api/admin/projects/{id}/cover-image, or supplied directly. */
    @Column(length = 500)
    private String coverImageUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
