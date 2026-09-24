package com.examprep.test.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.security.SecurityUtils;
import com.examprep.test.dto.SeriesDtos.PublicSeriesDetailDto;
import com.examprep.test.dto.SeriesDtos.PublicSeriesDto;
import com.examprep.test.service.TestSeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Storefront. Works anonymously. When a valid token is sent, each series also carries
 * {@code myAccess} and each test carries {@code accessible} (the JWT filter still runs
 * on permitAll routes).
 */
@Tag(name = "Test series (public)")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/public/series")
@RequiredArgsConstructor
public class PublicSeriesController {

    private final TestSeriesService seriesService;

    @Operation(summary = "Browse published test series")
    @GetMapping
    public ApiResponse<PageResponse<PublicSeriesDto>> list(
            @RequestParam(required = false) String examCode, @RequestParam(required = false) Boolean free,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(seriesService.listPublic(examCode, free, q, pageable,
                SecurityUtils.currentUser().orElse(null)));
    }

    @Operation(summary = "Series detail with its tests")
    @GetMapping("/{slug}")
    public ApiResponse<PublicSeriesDetailDto> detail(@PathVariable String slug) {
        return ApiResponse.ok(seriesService.getPublic(slug, SecurityUtils.currentUser().orElse(null)));
    }
}
