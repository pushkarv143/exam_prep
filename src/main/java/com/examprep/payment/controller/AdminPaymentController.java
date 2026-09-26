package com.examprep.payment.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.payment.dto.PaymentDtos.PaymentDto;
import com.examprep.payment.entity.PaymentStatus;
import com.examprep.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Payments (admin)")
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("@perm.has('admin.access')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @PreAuthorize("@perm.has('payment.view')")
    @GetMapping
    public ApiResponse<PageResponse<PaymentDto>> search(
            @RequestParam(required = false) PaymentStatus status, @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID seriesId,
            @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(paymentService.search(status, userId, seriesId, pageable));
    }
}
