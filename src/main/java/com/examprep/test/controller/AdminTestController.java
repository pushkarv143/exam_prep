package com.examprep.test.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.test.dto.BuilderDtos.AddQuestionsRequest;
import com.examprep.test.dto.BuilderDtos.AddQuestionsResult;
import com.examprep.test.dto.BuilderDtos.GenerateFromPatternRequest;
import com.examprep.test.dto.BuilderDtos.GenerateRequest;
import com.examprep.test.dto.BuilderDtos.GenerationReport;
import com.examprep.test.dto.BuilderDtos.ReorderRequest;
import com.examprep.test.dto.BuilderDtos.SectionRequest;
import com.examprep.test.dto.BuilderDtos.UpdateTestQuestionRequest;
import com.examprep.test.dto.BuilderDtos.VersionUpdateResult;
import com.examprep.test.dto.TestDtos.CreateTestRequest;
import com.examprep.test.dto.TestDtos.PatternDto;
import com.examprep.test.dto.TestDtos.SectionDto;
import com.examprep.test.dto.TestDtos.TestDetailDto;
import com.examprep.test.dto.TestDtos.TestDto;
import com.examprep.test.dto.TestDtos.TestQuestionDto;
import com.examprep.test.dto.TestDtos.UpdateTestRequest;
import com.examprep.test.dto.TestDtos.ValidationReport;
import com.examprep.test.entity.TestStatus;
import com.examprep.test.service.TestBuilderService;
import com.examprep.test.service.TestGeneratorService;
import com.examprep.test.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@Tag(name = "Tests & builder (admin)")
@RestController
@RequestMapping("/api/v1/admin/tests")
@RequiredArgsConstructor
@PreAuthorize("@perm.has('admin.access')")
public class AdminTestController {

    private final TestService testService;
    private final TestBuilderService builder;
    private final TestGeneratorService generator;

    // ------------------------------------------------------------------ tests

    @Operation(summary = "Available exam patterns (JEE Main, NEET, JEE Advanced, Custom)")
    @PreAuthorize("@perm.has('test.view')")
    @GetMapping("/patterns")
    public ApiResponse<List<PatternDto>> patterns() {
        return ApiResponse.ok(testService.patterns());
    }

    @PreAuthorize("@perm.has('test.view')")
    @GetMapping
    public ApiResponse<PageResponse<TestDto>> search(
            @RequestParam(required = false) UUID seriesId, @RequestParam(required = false) UUID examId,
            @RequestParam(required = false) TestStatus status, @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(testService.search(seriesId, examId, status, q, pageable));
    }

    @Operation(summary = "Create a test; non-CUSTOM patterns create their sections automatically")
    @PreAuthorize("@perm.has('test.create')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TestDetailDto> create(@Valid @RequestBody CreateTestRequest request) {
        return ApiResponse.ok(testService.create(request));
    }

    @Operation(summary = "Full test with sections and questions (builder view)")
    @PreAuthorize("@perm.has('test.view')")
    @GetMapping("/{id}")
    public ApiResponse<TestDetailDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(testService.get(id));
    }

    @PreAuthorize("@perm.has('test.update')")
    @PutMapping("/{id}")
    public ApiResponse<TestDetailDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateTestRequest request) {
        return ApiResponse.ok(testService.update(id, request));
    }

    @PreAuthorize("@perm.has('test.delete')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        testService.delete(id);
        return ApiResponse.ok();
    }

    // ------------------------------------------------------------------ lifecycle

    @Operation(summary = "Publish-readiness report (errors block publishing, warnings do not)")
    @PreAuthorize("@perm.has('test.view')")
    @GetMapping("/{id}/validation")
    public ApiResponse<ValidationReport> validate(@PathVariable UUID id) {
        return ApiResponse.ok(testService.validate(id));
    }

    @PreAuthorize("@perm.has('test.publish')")
    @PostMapping("/{id}/publish")
    public ApiResponse<TestDto> publish(@PathVariable UUID id) {
        return ApiResponse.ok(testService.publish(id));
    }

    @PreAuthorize("@perm.has('test.publish')")
    @PostMapping("/{id}/unpublish")
    public ApiResponse<TestDto> unpublish(@PathVariable UUID id) {
        return ApiResponse.ok(testService.unpublish(id));
    }

    @PreAuthorize("@perm.has('test.publish')")
    @PostMapping("/{id}/archive")
    public ApiResponse<TestDto> archive(@PathVariable UUID id) {
        return ApiResponse.ok(testService.archive(id));
    }

    // ------------------------------------------------------------------ builder: sections

    @PreAuthorize("@perm.has('test.update')")
    @PostMapping("/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SectionDto> addSection(@PathVariable UUID id, @Valid @RequestBody SectionRequest request) {
        return ApiResponse.ok(builder.addSection(id, request));
    }

    @PreAuthorize("@perm.has('test.update')")
    @PutMapping("/{id}/sections/{sectionId}")
    public ApiResponse<SectionDto> updateSection(@PathVariable UUID id, @PathVariable UUID sectionId,
                                                 @Valid @RequestBody SectionRequest request) {
        return ApiResponse.ok(builder.updateSection(id, sectionId, request));
    }

    @PreAuthorize("@perm.has('test.update')")
    @DeleteMapping("/{id}/sections/{sectionId}")
    public ApiResponse<Void> deleteSection(@PathVariable UUID id, @PathVariable UUID sectionId) {
        builder.deleteSection(id, sectionId);
        return ApiResponse.ok();
    }

    // ------------------------------------------------------------------ builder: questions

    @Operation(summary = "Add bank questions to a section (paragraphs expand to their children)")
    @PreAuthorize("@perm.has('test.update')")
    @PostMapping("/{id}/sections/{sectionId}/questions")
    public ApiResponse<AddQuestionsResult> addQuestions(@PathVariable UUID id, @PathVariable UUID sectionId,
                                                        @Valid @RequestBody AddQuestionsRequest request) {
        return ApiResponse.ok(builder.addQuestions(id, sectionId, request));
    }

    @Operation(summary = "Reorder a section's questions (drag and drop)")
    @PreAuthorize("@perm.has('test.update')")
    @PutMapping("/{id}/sections/{sectionId}/order")
    public ApiResponse<Void> reorder(@PathVariable UUID id, @PathVariable UUID sectionId,
                                     @Valid @RequestBody ReorderRequest request) {
        builder.reorder(id, sectionId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "Override marks / negative marks / partial marking of one question in this test")
    @PreAuthorize("@perm.has('test.update')")
    @PutMapping("/{id}/questions/{testQuestionId}")
    public ApiResponse<TestQuestionDto> updateQuestion(@PathVariable UUID id, @PathVariable UUID testQuestionId,
                                                       @Valid @RequestBody UpdateTestQuestionRequest request) {
        return ApiResponse.ok(builder.updateQuestion(id, testQuestionId, request));
    }

    @PreAuthorize("@perm.has('test.update')")
    @DeleteMapping("/{id}/questions/{testQuestionId}")
    public ApiResponse<Void> removeQuestion(@PathVariable UUID id, @PathVariable UUID testQuestionId) {
        builder.removeQuestion(id, testQuestionId);
        return ApiResponse.ok();
    }

    @Operation(summary = "Move every question of a draft test to its latest published version",
            description = "Tests pin question versions when questions are added. Published tests never move here.")
    @PreAuthorize("@perm.has('test.update')")
    @PostMapping("/{id}/questions/update-versions")
    public ApiResponse<VersionUpdateResult> updateVersions(@PathVariable UUID id) {
        return ApiResponse.ok(new VersionUpdateResult(builder.updateToLatestVersions(id)));
    }

    // ------------------------------------------------------------------ auto-generation

    @Operation(summary = "Auto-pick random questions by topic/chapter/difficulty/type rules")
    @PreAuthorize("@perm.has('test.update')")
    @PostMapping("/{id}/generate")
    public ApiResponse<GenerationReport> generate(@PathVariable UUID id, @Valid @RequestBody GenerateRequest request) {
        return ApiResponse.ok(generator.generate(id, request));
    }

    @Operation(summary = "Fill every pattern section to its target count with a difficulty mix")
    @PreAuthorize("@perm.has('test.update')")
    @PostMapping("/{id}/generate-from-pattern")
    public ApiResponse<GenerationReport> generateFromPattern(@PathVariable UUID id,
                                                             @Valid @RequestBody GenerateFromPatternRequest request) {
        return ApiResponse.ok(generator.generateFromPattern(id, request));
    }
}
