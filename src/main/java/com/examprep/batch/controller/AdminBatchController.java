package com.examprep.batch.controller;

import com.examprep.batch.dto.BatchDtos.AddMembersRequest;
import com.examprep.batch.dto.BatchDtos.AddMembersResult;
import com.examprep.batch.dto.BatchDtos.BatchDto;
import com.examprep.batch.dto.BatchDtos.BatchMemberDto;
import com.examprep.batch.dto.BatchDtos.CreateBatchRequest;
import com.examprep.batch.dto.BatchDtos.UpdateBatchRequest;
import com.examprep.batch.service.BatchService;
import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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

import java.util.UUID;

@Tag(name = "Batches (admin)")
@RestController
@RequestMapping("/api/v1/admin/batches")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBatchController {

    private final BatchService batchService;

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @GetMapping
    public ApiResponse<PageResponse<BatchDto>> search(@RequestParam(required = false) UUID examId,
                                                      @RequestParam(required = false) String q,
                                                      @ParameterObject @PageableDefault(sort = "createdAt")
                                                      Pageable pageable) {
        return ApiResponse.ok(batchService.search(examId, q, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @GetMapping("/{id}")
    public ApiResponse<BatchDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(batchService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BatchDto> create(@Valid @RequestBody CreateBatchRequest request) {
        return ApiResponse.ok(batchService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BatchDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateBatchRequest request) {
        return ApiResponse.ok(batchService.update(id, request));
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @GetMapping("/{id}/members")
    public ApiResponse<PageResponse<BatchMemberDto>> members(@PathVariable UUID id,
                                                             @ParameterObject @PageableDefault(size = 50)
                                                             Pageable pageable) {
        return ApiResponse.ok(batchService.members(id, pageable));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<AddMembersResult> addMembers(@PathVariable UUID id,
                                                    @Valid @RequestBody AddMembersRequest request) {
        return ApiResponse.ok(batchService.addMembers(id, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        batchService.removeMember(id, userId);
        return ApiResponse.ok();
    }
}
