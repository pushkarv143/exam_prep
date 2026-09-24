package com.examprep.user.service;

import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.security.AuthUser;
import com.examprep.security.TokenStore;
import com.examprep.user.dto.AdminUserDtos.CreateUserRequest;
import com.examprep.user.dto.UserDto;
import com.examprep.user.entity.Role;
import com.examprep.user.entity.RoleName;
import com.examprep.user.entity.User;
import com.examprep.user.entity.UserStatus;
import com.examprep.user.mapper.UserMapper;
import com.examprep.user.repository.RoleRepository;
import com.examprep.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User administration. Status and role changes <b>revoke all sessions</b> of the target
 * user (roles live inside the JWT, so old tokens would otherwise keep the old rights for
 * up to 15 minutes). Admins cannot lock themselves out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;
    private final UserMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String q, UserStatus status, RoleName role, Pageable pageable) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(users.search(query, status, role, pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return mapper.toDto(load(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailNormalized(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An account with this email already exists");
        }
        if (req.phone() != null && users.existsByPhone(req.phone())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An account with this phone already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPhone(req.phone());
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEmailVerified(true);
        user.getRoles().addAll(resolve(req.roles()));
        users.saveAndFlush(user);
        log.info("Admin created user {} with roles {}", user.getId(), req.roles());
        return mapper.toDto(user);
    }

    @Transactional
    public UserDto updateStatus(AuthUser actor, UUID id, UserStatus status) {
        if (actor.id().equals(id) && status != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You cannot deactivate your own account");
        }
        User user = load(id);
        user.setStatus(status);
        if (status != UserStatus.ACTIVE) {
            tokenStore.revokeAllSessions(id);
        }
        return mapper.toDto(user);
    }

    @Transactional
    public UserDto updateRoles(AuthUser actor, UUID id, Set<RoleName> newRoles) {
        if (actor.id().equals(id) && !newRoles.contains(RoleName.ADMIN)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You cannot remove your own ADMIN role");
        }
        User user = load(id);
        user.getRoles().clear();
        user.getRoles().addAll(resolve(newRoles));
        tokenStore.revokeAllSessions(id);
        return mapper.toDto(user);
    }

    private Set<Role> resolve(Set<RoleName> names) {
        return names.stream()
                .map(n -> roles.findByName(n).orElseThrow(() -> new IllegalStateException("Role missing: " + n)))
                .collect(Collectors.toSet());
    }

    private User load(UUID id) {
        return users.findWithRolesById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }
}
