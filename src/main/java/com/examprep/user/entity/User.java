package com.examprep.user.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    /** Always stored lower-cased; uniqueness is enforced by {@code ux_users_email_lower}. */
    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "target_exam_code")
    private String targetExamCode;

    private String city;

    private String state;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public Set<RoleName> roleNames() {
        return roles.stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasRole(RoleName role) {
        return roles.stream().anyMatch(r -> r.getName() == role);
    }
}
