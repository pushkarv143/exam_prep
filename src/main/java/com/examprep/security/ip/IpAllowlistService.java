package com.examprep.security.ip;

import com.examprep.audit.service.AuditContext;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.redis.ClusterEvents;
import com.examprep.common.util.Uuids;
import com.examprep.security.AppSecurityProperties;
import com.examprep.security.AuthUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Optional allow-list of networks (CIDR) that may use {@code /api/v1/admin/**}. The rules
 * are cached in memory and dropped cluster-wide on change. To prevent self-lockout, the
 * list cannot be enabled unless the current IP matches it, and the last entry cannot be
 * removed while it is enabled. {@code ADMIN_IP_ALLOWLIST_FORCE_DISABLED=true} is the break-glass switch.
 */
@Slf4j
@Service
public class IpAllowlistService {

    public static final String EVENT_TOPIC = "ip-allowlist";
    private static final String SETTING = "security.admin-ip-allowlist.enabled";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final ClusterEvents events;
    private final AppSecurityProperties props;
    private final Clock clock;
    private volatile Snapshot snapshot;

    public IpAllowlistService(JdbcTemplate jdbc, ClusterEvents events, AppSecurityProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.props = props;
        this.clock = clock;
        events.on(EVENT_TOPIC, () -> snapshot = null);
    }

    public record IpAllowlistEntry(UUID id, String cidr, String label, Instant createdAt) {
    }

    public record IpAllowlistState(boolean enabled, boolean forceDisabled, String yourIp, boolean yourIpAllowed,
                        List<IpAllowlistEntry> entries) {
    }

    private record Snapshot(boolean enabled, List<IpAddressMatcher> matchers, Instant loadedAt) {
    }

    /** Hot path (every admin request): memory only. */
    public boolean isAllowed(String ip) {
        if (props.adminIpAllowlist().forceDisabled()) {
            return true;
        }
        Snapshot s = current();
        return !s.enabled || matches(s.matchers, ip);
    }

    public IpAllowlistState state(String yourIp) {
        List<IpAllowlistEntry> entries = entries();
        return new IpAllowlistState(enabledFlag(), props.adminIpAllowlist().forceDisabled(), yourIp,
                matches(entries.stream().map(e -> new IpAddressMatcher(e.cidr())).toList(), yourIp), entries);
    }

    @Transactional
    public IpAllowlistEntry add(String cidr, String label, AuthUser actor) {
        String normalized = validate(cidr);
        UUID id = Uuids.v7();
        jdbc.update("insert into admin_ip_allowlist (id, cidr, label, created_by) values (?, ?, ?, ?)",
                id, normalized, label.trim(), actor.id());
        AuditContext.action("security.ip-allowlist.add");
        AuditContext.entity("IP_ALLOWLIST", normalized);
        changed();
        return entries().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void remove(UUID id) {
        List<IpAllowlistEntry> entries = entries();
        IpAllowlistEntry target = entries.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Entry not found"));
        if (enabledFlag() && entries.size() == 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Disable the allow-list before removing its last entry");
        }
        jdbc.update("delete from admin_ip_allowlist where id = ?", id);
        AuditContext.action("security.ip-allowlist.remove");
        AuditContext.entity("IP_ALLOWLIST", target.cidr());
        changed();
    }

    @Transactional
    public IpAllowlistState setEnabled(boolean enabled, String currentIp, AuthUser actor) {
        if (enabled) {
            List<IpAddressMatcher> matchers = entries().stream().map(e -> new IpAddressMatcher(e.cidr())).toList();
            if (!matches(matchers, currentIp)) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Add your current IP (" + currentIp + ") to the list first, or you would lock yourself out");
            }
        }
        jdbc.update("""
                insert into system_settings (key, value, updated_by, updated_at) values (?, ?::jsonb, ?, now())
                on conflict (key) do update set value = excluded.value, updated_by = excluded.updated_by,
                    updated_at = now()
                """, SETTING, Boolean.toString(enabled), actor.id());
        AuditContext.action(enabled ? "security.ip-allowlist.enable" : "security.ip-allowlist.disable");
        AuditContext.entity("SETTING", SETTING);
        changed();
        return state(currentIp);
    }

    // ------------------------------------------------------------------ helpers

    private List<IpAllowlistEntry> entries() {
        return jdbc.query("select id, cidr, label, created_at from admin_ip_allowlist order by created_at",
                (rs, i) -> new IpAllowlistEntry(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant()));
    }

    private boolean enabledFlag() {
        List<String> v = jdbc.queryForList("select value::text from system_settings where key = ?", String.class,
                SETTING);
        return !v.isEmpty() && "true".equals(v.getFirst());
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s == null || s.loadedAt.plus(TTL).isBefore(clock.instant())) {
            List<IpAddressMatcher> matchers = entries().stream().map(e -> new IpAddressMatcher(e.cidr())).toList();
            s = new Snapshot(enabledFlag(), matchers, clock.instant());
            snapshot = s;
        }
        return s;
    }

    private void changed() {
        events.publish(EVENT_TOPIC);
    }

    private static boolean matches(List<IpAddressMatcher> matchers, String ip) {
        if (ip == null) {
            return false;
        }
        for (IpAddressMatcher m : matchers) {
            try {
                if (m.matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // an unparsable client address never matches
            }
        }
        return false;
    }

    /** Accepts "203.0.113.7", "203.0.113.0/24" or IPv6 forms; a bare address becomes /32 or /128. */
    static String validate(String cidr) {
        String value = cidr == null ? "" : cidr.trim();
        if (!value.matches("^[0-9A-Fa-f:.]+(/\\d{1,3})?$")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Enter an IP address or CIDR range, e.g. 203.0.113.0/24");
        }
        try {
            new IpAddressMatcher(value).matches(value.contains("/") ? value.substring(0, value.indexOf('/')) : value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid IP address or CIDR range");
        }
        if (!value.contains("/")) {
            value += value.contains(":") ? "/128" : "/32";
        }
        return value;
    }
}
