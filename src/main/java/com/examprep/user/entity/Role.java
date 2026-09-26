package com.examprep.user.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A role is data (Admin Portal 2.0): built-in roles are seeded by Flyway and flagged
 * {@code system}; admins can add custom roles and edit permissions from the UI.
 * Codes are upper-snake-case (see {@link Roles} for the built-in ones).
 */
@Getter
@Setter
@Entity
@Table(name = "roles")
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Short id;

    @Column(nullable = false, unique = true, length = 32)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    private String description;

    /** Built-in: cannot be renamed or deleted. */
    @Column(nullable = false)
    private boolean system;

    /** May use the admin portal (false only for STUDENT). */
    @Column(nullable = false)
    private boolean staff = true;

    /** Implicitly holds every permission, including ones added later (SUPER_ADMIN). */
    @Column(name = "all_permissions", nullable = false)
    private boolean allPermissions;

    /** Content access is limited to the user's assigned subjects (TEACHER). */
    @Column(name = "subject_scoped", nullable = false)
    private boolean subjectScoped;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    private Set<String> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
