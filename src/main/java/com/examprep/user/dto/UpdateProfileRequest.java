package com.examprep.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update (PATCH semantics): null fields are left unchanged. */
public record UpdateProfileRequest(
        @Size(min = 2, max = 150) String fullName,
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number") String phone,
        @Size(max = 500) String avatarUrl,
        @Size(max = 32) String targetExamCode,
        @Size(max = 100) String city,
        @Size(max = 100) String state) {
}
