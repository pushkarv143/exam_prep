package com.examprep.user.repository;

import com.examprep.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Matches the functional index {@code ux_users_email_lower}. The caller passes a lower-cased email. */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where lower(u.email) = :email")
    Optional<User> findByEmailNormalized(@Param("email") String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByPhone(String phone);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") UUID id);

    @Query("select count(u) > 0 from User u where lower(u.email) = :email")
    boolean existsByEmailNormalized(@Param("email") String email);

    boolean existsByPhone(String phone);

    /** Rows of [lower(email), id] for the given lower-cased emails. */
    @Query("select lower(u.email), u.id from User u where lower(u.email) in :emails")
    java.util.List<Object[]> findIdsByNormalizedEmails(@Param("emails") java.util.Collection<String> emails);

    @Query("select u.id from User u where u.id in :ids")
    java.util.List<UUID> findExistingIds(@Param("ids") java.util.Collection<UUID> ids);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    @Query(value = """
            select u from User u
            where (:q is null or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or u.phone like concat('%', cast(:q as string), '%'))
              and (:status is null or u.status = :status)
              and (:role is null or exists (select 1 from u.roles r where r.name = :role))
            """)
    org.springframework.data.domain.Page<User> search(@Param("q") String q,
                                                      @Param("status") com.examprep.user.entity.UserStatus status,
                                                      @Param("role") com.examprep.user.entity.RoleName role,
                                                      org.springframework.data.domain.Pageable pageable);
}
