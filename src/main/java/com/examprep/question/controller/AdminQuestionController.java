package com.examprep.question.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.question.dto.ImportReport;
import com.examprep.question.dto.QuestionDto;
import com.examprep.question.dto.QuestionRequest;
import com.examprep.question.dto.QuestionSearchFilter;
import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.importer.ImportColumns;
import com.examprep.question.importer.QuestionImportService;
import com.examprep.question.service.QuestionService;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Tag(name = "Question bank (admin)")
@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AdminQuestionController {

    private final QuestionService questionService;
    private final QuestionImportService importService;

    @Operation(summary = "Search the question bank",
            description = "Filters: examId, subjectId, chapterId, topicId, parentId, type, difficulty, language, "
                    + "status, tag, q (text search), mine. Sort e.g. sort=createdAt,desc")
    @GetMapping
    public ApiResponse<PageResponse<QuestionSummaryDto>> search(
            @AuthenticationPrincipal AuthUser user,
            @Valid @ParameterObject QuestionSearchFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(questionService.search(filter, pageable, user));
    }

    @Operation(summary = "Get a question with its answer key and solution")
    @GetMapping("/{id}")
    public ApiResponse<QuestionDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(questionService.get(id));
    }

    @Operation(summary = "Create a question")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionDto> create(@Valid @RequestBody QuestionRequest request) {
        return ApiResponse.ok(questionService.create(request));
    }

    @Operation(summary = "Replace a question (answer key frozen once used in a published test)")
    @PutMapping("/{id}")
    public ApiResponse<QuestionDto> update(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.ok(questionService.update(id, request, user));
    }

    @Operation(summary = "Archive (soft-delete) a question")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> archive(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        questionService.archive(id, user);
        return ApiResponse.ok();
    }

    @Operation(summary = "Bulk import from .xlsx/.csv (all-or-nothing)",
            description = "dryRun=true validates everything (including DB constraints) without saving. "
                    + "autoCreateCatalog=true creates missing chapters/topics (never exams/subjects).")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportReport> importQuestions(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean autoCreateCatalog) {
        return ApiResponse.ok(importService.importQuestions(file, dryRun, autoCreateCatalog));
    }

    @Operation(summary = "Download the CSV import template with example rows")
    @GetMapping(value = "/import/template", produces = "text/csv")
    public ResponseEntity<byte[]> importTemplate() {
        // A UTF-8 BOM makes Excel open the file as UTF-8 (Hindi text, special symbols).
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = ImportColumns.TEMPLATE_CSV.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"question-import-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(out);
    }
}
