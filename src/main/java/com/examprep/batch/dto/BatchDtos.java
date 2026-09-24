package com.examprep.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BatchDtos {

    private BatchDtos() {
    }

    public record BatchDto(UUID id, String code, String name, String description, UUID examId, LocalDate startDate,
                           LocalDate endDate, boolean active, long memberCount, Instant createdAt) {
    }

    public record CreateBatchRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,49}$",
                    message = "must be 2-50 letters, digits, '-' or '_'") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String description,
            UUID examId,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record UpdateBatchRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String description,
            UUID examId,
            LocalDate startDate,
            LocalDate endDate,
            boolean active) {
    }

    /** Add by user id and/or by email (handy when pasting a class list). */
    public record AddMembersRequest(@Size(max = 1000) List<UUID> userIds, @Size(max = 1000) List<String> emails) {
    }

    public record AddMembersResult(int added, int alreadyMembers, List<String> unknownEmails,
                                   List<UUID> unknownUserIds) {
    }

    public record BatchMemberDto(UUID userId, String fullName, String email, String phone, Instant joinedAt) {
    }
}
