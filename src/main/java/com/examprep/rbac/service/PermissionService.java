package com.examprep.rbac.service;

import com.examprep.common.redis.ClusterEvents;
import com.examprep.security.AuthUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves what a user may do. Role → permission maps live in the database and are cached
 * in memory as one immutable snapshot. The snapshot is dropped on every instance through
 * {@link ClusterEvents} when an admin edits a role, and also expires after a minute as a
 * safety net. A permission check therefore costs no I/O on the hot path.
 *
 * <p>Permissions are never read from the JWT: editing a role takes effect on the next
 * request, without logging anyone out.
 */
@Slf4j
@Service
public class PermissionService {

    public static final String EVENT_TOPIC = "rbac";
    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(60);
    private static final Duration SCOPE_TTL = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final ClusterEvents events;
    private final Clock clock;

    private volatile Snapshot snapshot;
    private final Map<UUID, CachedScope> scopes = new ConcurrentHashMap<>();

    public PermissionService(JdbcTemplate jdbc, ClusterEvents events, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
        events.on(EVENT_TOPIC, this::clearLocal);
    }

    // ------------------------------------------------------------------ queries

    public boolean has(AuthUser user, String permission) {
        return user != null && permissionsOf(user.roles()).contains(permission);
    }

    public boolean hasAll(AuthUser user, Collection<String> permissions) {
        return user != null && permissionsOf(user.roles()).containsAll(permissions);
    }

    /** Effective permissions of a set of roles (union). Unknown roles contribute nothing. */
    public Set<String> permissionsOf(Collection<String> roles) {
        Snapshot s = current();
        Set<String> result = new HashSet<>();
        for (String role : roles) {
            RoleInfo info = s.roles.get(role);
            if (info == null) {
                continue;
            }
            if (info.allPermissions) {
                return s.catalogue;
            }
            result.addAll(info.permissions);
        }
        return result;
    }

    /** Permissions of one role as stored (SUPER_ADMIN: the whole catalogue). */
    public Set<String> permissionsOfRole(String role) {
        return permissionsOf(List.of(role));
    }

    public Set<String> catalogue() {
        return current().catalogue;
    }

    public boolean roleExists(String role) {
        return current().roles.containsKey(role);
    }

    public boolean isAllPermissionsRole(String role) {
        RoleInfo info = current().roles.get(role);
        return info != null && info.allPermissions;
    }

    /**
     * Subjects the user may author, or empty when unrestricted. A user is restricted when
     * every staff role they hold is subject-scoped (e.g. a pure TEACHER). A teacher who is also
     * a CONTENT_MANAGER is unrestricted. A restricted user with no subjects assigned gets an
     * empty set, which means "nothing".
     */
    public Optional<Set<UUID>> subjectScope(AuthUser user) {
        return subjectScope(user.roles(), user.id());
    }

    /** {@link #subjectScope(AuthUser)} for any user, given their role names. */
    public Optional<Set<UUID>> subjectScope(Collection<String> roleNames, UUID userId) {
        Snapshot s = current();
        boolean anyStaff = false;
        boolean allScoped = true;
        for (String role : roleNames) {
            RoleInfo info = s.roles.get(role);
            if (info == null || !info.staff) {
                continue;
            }
            anyStaff = true;
            if (info.allPermissions || !info.subjectScoped) {
                allScoped = false;
            }
        }
        if (!anyStaff || !allScoped) {
            return Optional.empty();
        }
        return Optional.of(subjectsOf(userId));
    }

    public Set<UUID> subjectsOf(UUID userId) {
        Instant now = clock.instant();
        CachedScope cached = scopes.get(userId);
        if (cached != null && cached.loadedAt.plus(SCOPE_TTL).isAfter(now)) {
            return cached.subjects;
        }
        Set<UUID> subjects = Set.copyOf(jdbc.queryForList(
                "select subject_id from user_subject_scopes where user_id = ?", UUID.class, userId));
        scopes.put(userId, new CachedScope(subjects, now));
        return subjects;
    }

    // ------------------------------------------------------------------ invalidation

    /** Call after any change to roles, permissions or scopes: all instances reload. */
    public void invalidate() {
        events.publish(EVENT_TOPIC);
    }

    private void clearLocal() {
        snapshot = null;
        scopes.clear();
        log.debug("RBAC cache cleared");
    }

    // ------------------------------------------------------------------ loading

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s != null && s.loadedAt.plus(SNAPSHOT_TTL).isAfter(clock.instant())) {
            return s;
        }
        synchronized (this) {
            s = snapshot;
            if (s == null || !s.loadedAt.plus(SNAPSHOT_TTL).isAfter(clock.instant())) {
                s = load();
                snapshot = s;
            }
            return s;
        }
    }

    private Snapshot load() {
        Map<String, RoleInfo> roles = new HashMap<>();
        jdbc.query("select name, staff, all_permissions, subject_scoped from roles", rs -> {
            roles.put(rs.getString(1), new RoleInfo(rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4),
                    new HashSet<>()));
        });
        jdbc.query("select r.name, rp.permission_code from role_permissions rp join roles r on r.id = rp.role_id",
                rs -> {
                    RoleInfo info = roles.get(rs.getString(1));
                    if (info != null) {
                        info.permissions.add(rs.getString(2));
                    }
                });
        Set<String> catalogue = new TreeSet<>(jdbc.queryForList("select code from permissions", String.class));
        Map<String, RoleInfo> frozen = new HashMap<>();
        roles.forEach((k, v) -> frozen.put(k, new RoleInfo(v.staff, v.allPermissions, v.subjectScoped,
                Set.copyOf(v.permissions))));
        return new Snapshot(Map.copyOf(frozen), Set.copyOf(catalogue), clock.instant());
    }

    private record RoleInfo(boolean staff, boolean allPermissions, boolean subjectScoped, Set<String> permissions) {
    }

    private record Snapshot(Map<String, RoleInfo> roles, Set<String> catalogue, Instant loadedAt) {
    }

    private record CachedScope(Set<UUID> subjects, Instant loadedAt) {
    }
}
