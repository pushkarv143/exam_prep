package com.examprep.user.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.security.AuthUser;
import com.examprep.user.dto.AdminUserDtos.CreateUserRequest;
import com.examprep.user.dto.AdminUserDtos.UpdateRolesRequest;
import com.examprep.user.dto.AdminUserDtos.UpdateStatusRequest;
import com.examprep.user.dto.UserDto;
import com.examprep.user.entity.RoleName;
import com.examprep.user.entity.UserStatus;
import com.examprep.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Users (admin)")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService service;

    @GetMapping
    public ApiResponse<PageResponse<UserDto>> search(
            @RequestParam(required = false) String q, @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) RoleName role,
            @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(service.search(q, status, role, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserDto> status(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                       @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok(service.updateStatus(actor, id, request.status()));
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserDto> roles(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                      @Valid @RequestBody UpdateRolesRequest request) {
        return ApiResponse.ok(service.updateRoles(actor, id, request.roles()));
    }
}
