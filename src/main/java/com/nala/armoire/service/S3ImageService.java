package com.nala.armoire.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.model.dto.response.ImageUploadResponse;
import com.nala.armoire.model.entity.ImageAsset;
import com.nala.armoire.repository.ImageAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * S3 Image Upload Service
 * Handles image upload and deletion
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3ImageService {

    private final AmazonS3 amazonS3;
    private final ImageAssetRepository imageAssetRepository;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.cloudfront.domain:}") // Optional CloudFront domain
    private String cloudFrontDomain;

    // Allowed image types
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/webp");

    // Max file size: 5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * Upload image to S3 (for products, banners, etc.)
     * Saves to: assets/{filename}
     */
    @Transactional
    public ImageUploadResponse uploadImage(MultipartFile file) {
        try {
            // Validate file
            validateFile(file);

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String uniqueFileName = UUID.randomUUID().toString() + extension;

            // S3 path: assets/{uniqueFileName}
            String s3Key = "assets/" + uniqueFileName;

            // Get image dimensions
            BufferedImage image = ImageIO.read(file.getInputStream());
            String dimensions = image.getWidth() + "x" + image.getHeight();

            // Upload to S3
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            amazonS3.putObject(new PutObjectRequest(
                    bucketName, s3Key, file.getInputStream(), metadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead));

            // Generate URLs
            String publicUrl = generatePublicUrl(s3Key);
            String cdnUrl = cloudFrontDomain.isEmpty() ? null : generateCdnUrl(s3Key);

            // Save to database
            ImageAsset imageAsset = ImageAsset.builder()
                    .fileName(originalFilename)
                    .s3Key(s3Key)
                    .s3Url(publicUrl)
                    .cdnUrl(cdnUrl)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .dimensions(dimensions)
                    .build();

            imageAsset = imageAssetRepository.save(imageAsset);

            log.info("Image uploaded successfully: {}", s3Key);

            return ImageUploadResponse.builder()
                    .id(imageAsset.getId())
                    .fileName(originalFilename)
                    .s3Url(publicUrl)
                    .cdnUrl(cdnUrl)
                    .fileSize(file.getSize())
                    .dimensions(dimensions)
                    .build();

        } catch (IOException e) {
            log.error("Failed to upload image", e);
            throw new BadRequestException("Failed to process image: " + e.getMessage());
        }
    }

    /**
     * Upload customization preview (user-generated)
     * Saves to: customizations/{userId}/{customizationId}.{ext}
     */
    @Transactional
    public ImageUploadResponse uploadCustomizationPreview(
            MultipartFile file,
            UUID userId,
            UUID customizationId) {

        try {
            validateFile(file);

            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String fileName = customizationId + extension;

            // S3 path
            String s3Key = String.format("customizations/%s/%s", userId, fileName);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            // ✅ NO ACL
            amazonS3.putObject(
                    new PutObjectRequest(bucketName, s3Key, file.getInputStream(), metadata));

            String publicUrl = generatePublicUrl(s3Key);
            String cdnUrl = cloudFrontDomain.isBlank() ? null : generateCdnUrl(s3Key);

            log.info("Customization preview uploaded: {}", s3Key);

            return ImageUploadResponse.builder()
                    .fileName(fileName)
                    .s3Url(publicUrl)
                    .cdnUrl(cdnUrl)
                    .fileSize(file.getSize())
                    .build();

        } catch (IOException e) {
            log.error("Failed to upload customization preview", e);
            throw new BadRequestException("Failed to upload preview");
        }
    }

    /**
     * Delete image from S3 and database
     */
    @Transactional
    public void deleteImage(UUID imageId) {
        ImageAsset imageAsset = imageAssetRepository.findById(imageId)
                .orElseThrow(() -> new BadRequestException("Image not found"));

        try {
            // Delete from S3
            amazonS3.deleteObject(bucketName, imageAsset.getS3Key());

            // Delete from database
            imageAssetRepository.delete(imageAsset);

            log.info("Image deleted successfully: {}", imageId);

        } catch (Exception e) {
            log.error("Failed to delete image from S3", e);
            throw new BadRequestException("Failed to delete image: " + e.getMessage());
        }
    }

    /**
     * Delete customization preview from S3
     */
    public void deleteCustomizationPreview(String s3Key) {
        try {
            amazonS3.deleteObject(bucketName, s3Key);
            log.info("Customization preview deleted: {}", s3Key);
        } catch (Exception e) {
            log.error("Failed to delete customization preview", e);
            // Don't throw - allow operation to continue
        }
    }

    // ==================== HELPER METHODS ====================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Invalid file type. Only JPG, PNG, WEBP allowed");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds 5MB limit");
        }
    }

    private String generatePublicUrl(String s3Key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                bucketName, amazonS3.getRegionName(), s3Key);
    }

    private String generateCdnUrl(String s3Key) {
        if (cloudFrontDomain.isEmpty()) {
            return null;
        }
        return String.format("https://%s/%s", cloudFrontDomain, s3Key);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
