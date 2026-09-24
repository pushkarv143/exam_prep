package com.examprep.catalog.controller;

import com.examprep.catalog.dto.CatalogDtos.ChapterDto;
import com.examprep.catalog.dto.CatalogDtos.ExamDto;
import com.examprep.catalog.dto.CatalogDtos.SubjectDto;
import com.examprep.catalog.dto.CatalogDtos.TopicDto;
import com.examprep.catalog.dto.CatalogRequests.CreateChapterRequest;
import com.examprep.catalog.dto.CatalogRequests.CreateExamRequest;
import com.examprep.catalog.dto.CatalogRequests.CreateSubjectRequest;
import com.examprep.catalog.dto.CatalogRequests.CreateTopicRequest;
import com.examprep.catalog.dto.CatalogRequests.UpdateNodeRequest;
import com.examprep.catalog.dto.CatalogTreeDto;
import com.examprep.catalog.service.CatalogAdminService;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Catalog management. Exams and subjects are ADMIN-only, because they shape the whole
 * platform. Teachers may add chapters and topics while authoring questions.
 * "Delete" is done by PUT with {@code active=false}.
 */
@Tag(name = "Catalog (admin)")
@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AdminCatalogController {

    private final CatalogQueryService query;
    private final CatalogAdminService admin;

    @Operation(summary = "List all exams including inactive")
    @GetMapping("/exams")
    public ApiResponse<List<ExamDto>> exams() {
        return ApiResponse.ok(query.listAllExams());
    }

    @Operation(summary = "Full tree of an exam (optionally including inactive nodes)")
    @GetMapping("/exams/{examId}/tree")
    public ApiResponse<CatalogTreeDto> tree(@PathVariable UUID examId,
                                            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return ApiResponse.ok(query.getTree(examId, includeInactive));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/exams")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExamDto> createExam(@Valid @RequestBody CreateExamRequest request) {
        return ApiResponse.ok(admin.createExam(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/exams/{id}")
    public ApiResponse<ExamDto> updateExam(@PathVariable UUID id, @Valid @RequestBody UpdateNodeRequest request) {
        return ApiResponse.ok(admin.updateExam(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubjectDto> createSubject(@Valid @RequestBody CreateSubjectRequest request) {
        return ApiResponse.ok(admin.createSubject(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/subjects/{id}")
    public ApiResponse<SubjectDto> updateSubject(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateNodeRequest request) {
        return ApiResponse.ok(admin.updateSubject(id, request));
    }

    @PostMapping("/chapters")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChapterDto> createChapter(@Valid @RequestBody CreateChapterRequest request) {
        return ApiResponse.ok(admin.createChapter(request));
    }

    @PutMapping("/chapters/{id}")
    public ApiResponse<ChapterDto> updateChapter(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateNodeRequest request) {
        return ApiResponse.ok(admin.updateChapter(id, request));
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TopicDto> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        return ApiResponse.ok(admin.createTopic(request));
    }

    @PutMapping("/topics/{id}")
    public ApiResponse<TopicDto> updateTopic(@PathVariable UUID id, @Valid @RequestBody UpdateNodeRequest request) {
        return ApiResponse.ok(admin.updateTopic(id, request));
    }
}
