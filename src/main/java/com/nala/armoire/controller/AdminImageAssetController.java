package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.ImageUploadResponse;
import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.model.entity.ImageAsset;
import com.nala.armoire.repository.ImageAssetRepository;
import com.nala.armoire.service.S3ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Admin Image Asset Management Controller
 * Simple interface for managing uploaded images
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminImageAssetController {

    private final S3ImageService s3ImageService;
    private final ImageAssetRepository imageAssetRepository;

    /**
     * POST /api/v1/admin/images/upload
     * Upload image to S3 and save metadata
     * 
     * @param file Image file to upload
     * @return Image upload response with URL
     */
    @PostMapping("/upload")
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file) {

        log.info("Admin: Uploading image - filename: {}", file.getOriginalFilename());

        ImageUploadResponse response = s3ImageService.uploadImage(file);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/images
     * Get all images with pagination
     * 
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @return Paged response of image assets
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ImageAsset>> getAllImages(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        log.info("Admin: Fetching all images - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ImageAsset> images = imageAssetRepository.findAll(pageable);

        PagedResponse<ImageAsset> response = PagedResponse.<ImageAsset>builder()
                .content(images.getContent())
                .page(images.getNumber())
                .size(images.getSize())
                .totalElements(images.getTotalElements())
                .totalPages(images.getTotalPages())
                .first(images.isFirst())
                .last(images.isLast())
                .hasNext(images.hasNext())
                .hasPrevious(images.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/admin/images/{id}
     * Delete image from S3 and database
     * 
     * @param id Image asset ID
     * @return No content response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id) {
        log.info("Admin: Deleting image: {}", id);

        s3ImageService.deleteImage(id);

        return ResponseEntity.noContent().build();
    }
}
