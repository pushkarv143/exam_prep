package com.examprep.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Superclass of every aggregate root. It provides:
 * <ul>
 *   <li>a UUIDv7 primary key,</li>
 *   <li>optimistic locking through {@code @Version}. A null version also tells
 *       Spring Data "this entity is new", which avoids a SELECT before INSERT,</li>
 *   <li>audit columns filled by Spring Data JPA auditing ({@code JpaAuditingConfig}).</li>
 * </ul>
 * Equality is id-based and Hibernate-proxy safe.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @UuidV7
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public final int hashCode() {
        // Constant per class. The id is assigned only on persist, so an id-based hash
        // would change and break HashSet membership.
        return Hibernate.getClass(this).hashCode();
    }
}
