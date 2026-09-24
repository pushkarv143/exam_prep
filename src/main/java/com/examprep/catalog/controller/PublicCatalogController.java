package com.examprep.catalog.controller;

import com.examprep.catalog.dto.CatalogDtos.ExamDto;
import com.examprep.catalog.dto.CatalogTreeDto;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Catalog (public)", description = "Exams and their subject/chapter/topic tree; Redis-cached")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/public/catalog")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final CatalogQueryService catalog;

    @Operation(summary = "List active exams")
    @GetMapping("/exams")
    public ApiResponse<List<ExamDto>> exams() {
        return ApiResponse.ok(catalog.listActiveExams().exams());
    }

    @Operation(summary = "Active subject -> chapter -> topic tree of an exam (e.g. JEE_MAIN)")
    @GetMapping("/exams/{examCode}/tree")
    public ApiResponse<CatalogTreeDto> tree(@PathVariable String examCode) {
        return ApiResponse.ok(catalog.getActiveTree(examCode.toUpperCase()));
    }
}
