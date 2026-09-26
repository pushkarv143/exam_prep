package com.examprep.audit.web;

import com.examprep.audit.dto.AuditDtos;
import com.examprep.audit.service.AuditExportJob;
import com.examprep.audit.service.AuditQueryService;
import com.examprep.common.api.ApiResponse;
import com.examprep.jobs.JobDtos;
import com.examprep.jobs.JobService;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Audit log (admin)")
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditQueryService query;
    private final JobService jobs;

    @Operation(summary = "Search the audit log (keyset pagination; default range last 30 days)",
            description = "Filters: from, to (ISO-8601), actorId, actorEmail (contains), action (prefix, e.g. 'test.'), "
                    + "entityType, entityId, outcome (SUCCESS|FAILURE|DENIED), q (text).")
    @PreAuthorize("@perm.has('audit.view')")
    @GetMapping
    public ApiResponse<AuditDtos.AuditPage> search(@ParameterObject AuditDtos.AuditFilter filter,
                                                   @RequestParam(required = false) String cursor,
                                                   @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(query.search(filter, cursor, Math.max(1, Math.min(size, 200))));
    }

    @Operation(summary = "Get one audit entry with its before/after and diff")
    @PreAuthorize("@perm.has('audit.view')")
    @GetMapping("/{id}")
    public ApiResponse<AuditDtos.AuditEntryDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(query.get(id));
    }

    @Operation(summary = "Export matching entries to CSV as a background job (poll /admin/jobs/{id})")
    @PreAuthorize("@perm.has('audit.export')")
    @PostMapping("/export")
    public ApiResponse<JobDtos.JobDto> export(@AuthenticationPrincipal AuthUser user,
                                              @RequestBody AuditDtos.AuditFilter filter) {
        return ApiResponse.ok(jobs.enqueue(AuditExportJob.TYPE, filter, user.id(), null, 2));
    }
}
