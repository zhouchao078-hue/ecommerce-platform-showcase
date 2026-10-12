package com.ecommerce.product.controller;

import com.ecommerce.common.result.Result;
import com.ecommerce.product.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final MinioStorageService storageService;

    @PostMapping(value = "/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MinioStorageService.UploadResult> uploadImage(
            @RequestParam("file") MultipartFile file) {
        return Result.success(storageService.uploadImage(file));
    }
}
