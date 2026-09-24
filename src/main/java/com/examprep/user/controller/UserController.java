package com.examprep.user.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.security.AuthUser;
import com.examprep.user.dto.ChangePasswordRequest;
import com.examprep.user.dto.UpdateProfileRequest;
import com.examprep.user.dto.UserDto;
import com.examprep.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Current user's profile")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get my profile")
    @GetMapping("/me")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(userService.getProfile(user.id()));
    }

    @Operation(summary = "Update my profile (partial)")
    @PatchMapping("/me")
    public ApiResponse<UserDto> updateMe(@AuthenticationPrincipal AuthUser user,
                                         @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(user.id(), request));
    }

    @Operation(summary = "Change my password (logs out all sessions)")
    @PostMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.id(), request);
        return ApiResponse.ok();
    }
}
