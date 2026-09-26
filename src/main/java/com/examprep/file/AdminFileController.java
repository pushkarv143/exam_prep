package com.examprep.file;

import com.examprep.common.api.ApiResponse;
import com.examprep.file.FileDtos.PresignedUrlDto;
import com.examprep.file.FileDtos.StoredFileDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Files (admin)", description = "Upload question images, thumbnails and solution PDFs to S3/MinIO")
@RestController
@RequestMapping("/api/v1/admin/files")
@RequiredArgsConstructor
@PreAuthorize("@perm.has('admin.access')")
public class AdminFileController {

    private final FileStorageService storage;

    @Operation(summary = "Upload a file; returns its key and (for public categories) a permanent URL")
    @PreAuthorize("@perm.has('file.upload')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoredFileDto> upload(@RequestPart("file") MultipartFile file,
                                             @RequestParam FileCategory category) {
        return ApiResponse.ok(storage.upload(file, category));
    }

    @Operation(summary = "Get a short-lived download URL for a stored object")
    @PreAuthorize("@perm.has('file.upload')")
    @GetMapping("/presigned-url")
    public ApiResponse<PresignedUrlDto> presign(@RequestParam String key) {
        return ApiResponse.ok(storage.presignDownload(key));
    }
}
