package com.examprep.test.dto;

import com.examprep.enrollment.entity.EnrollmentSource;
import com.examprep.test.entity.SeriesStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SeriesDtos {

    private SeriesDtos() {
    }

    /**
     * Create/replace a series. Price must be 0 for free series and greater than 0 for
     * paid ones (enforced in the service).
     */
    public record SeriesRequest(
            @NotNull UUID examId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 10_000) String description,
            @Size(max = 500) String thumbnailUrl,
            @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal price,
            boolean free,
            @Positive @Max(3650) Integer validityDays,
            Instant validUntil,
            boolean batchRestricted,
            @Size(max = 50) Set<UUID> batchIds) {
    }

    public record SeriesDto(UUID id, UUID examId, String name, String slug, String description, String thumbnailUrl,
                            BigDecimal price, String currency, boolean free, Integer validityDays,
                            Instant validUntil, boolean batchRestricted, Set<UUID> batchIds, SeriesStatus status,
                            long testCount, long enrollmentCount, Instant createdAt) {
    }

    /** The caller's relationship to a series; null for anonymous visitors. */
    public record MyAccess(boolean enrolled, boolean hasAccess, EnrollmentSource source, Instant expiresAt) {
    }

    public record PublicSeriesDto(UUID id, UUID examId, String name, String slug, String description,
                                  String thumbnailUrl, BigDecimal price, String currency, boolean free,
                                  Integer validityDays, Instant validUntil, boolean batchRestricted, long testCount,
                                  MyAccess myAccess) {
    }

    public record PublicSeriesDetailDto(PublicSeriesDto series, List<TestDtos.PublicTestDto> tests) {
    }

    public record AdminGrantRequest(@NotNull UUID userId) {
    }
}
