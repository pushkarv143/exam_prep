package com.examprep.user.service;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.security.TokenStore;
import com.examprep.user.dto.ChangePasswordRequest;
import com.examprep.user.dto.UpdateProfileRequest;
import com.examprep.user.dto.UserDto;
import com.examprep.user.entity.User;
import com.examprep.user.mapper.UserMapper;
import com.examprep.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Self-service profile operations. Admin-side user management lives in the admin module. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    @Transactional(readOnly = true)
    public UserDto getProfile(UUID userId) {
        return userMapper.toDto(load(userId));
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = load(userId);
        if (request.phone() != null && !request.phone().equals(user.getPhone())) {
            if (userRepository.existsByPhoneAndIdNot(request.phone(), userId)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Phone number is already registered");
            }
            user.setPhone(request.phone());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        if (request.targetExamCode() != null) {
            user.setTargetExamCode(request.targetExamCode());
        }
        if (request.city() != null) {
            user.setCity(request.city());
        }
        if (request.state() != null) {
            user.setState(request.state());
        }
        return userMapper.toDto(user);
    }

    /**
     * Changes the password and revokes ALL sessions, including the current one. The
     * client must log in again. This ensures a leaked token dies with the old password.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        tokenStore.revokeAllSessions(userId);
    }

    // ------------------------------------------------------------------ lookups for other modules

    /** Maps lower-cased email to user id for the emails that exist. */
    @Transactional(readOnly = true)
    public Map<String, UUID> findIdsByEmails(Collection<String> emails) {
        List<String> normalized = emails.stream().filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase()).distinct().toList();
        if (normalized.isEmpty()) {
            return Map.of();
        }
        return userRepository.findIdsByNormalizedEmails(normalized).stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (UUID) r[1]));
    }

    @Transactional(readOnly = true)
    public Set<UUID> findExistingIds(Collection<UUID> ids) {
        return ids.isEmpty() ? Set.of() : Set.copyOf(userRepository.findExistingIds(ids));
    }

    /** Full names by id, for leaderboards and admin lists. */
    @Transactional(readOnly = true)
    public Map<UUID, String> findNames(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getFullName));
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID userId) {
        return userMapper.toDto(load(userId));
    }

    private User load(UUID userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }
}
