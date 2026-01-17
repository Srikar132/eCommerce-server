# S3 Image Cleanup Strategy

## Overview
This document outlines when and how to delete images from S3 storage to prevent orphaned files and manage storage costs.

---

## 📁 S3 Folder Structure

```
your-bucket/
├── assets/                    # Product images, banners (permanent)
│   └── {uuid}.jpg
└── customizations/            # User customization previews (temporary)
    └── {userId}/
        └── {customizationId}.jpg
```

---

## 🗑️ When to Delete Images

### 1. **Assets Folder** (Managed in `ImageAsset` table)

| Event | Action | S3 Path | DB Record |
|-------|--------|---------|-----------|
| **Admin deletes product image** | Delete from S3 + DB | `assets/{uuid}.jpg` | Delete from `image_assets` |
| **Admin replaces image** | Delete old from S3 + DB, upload new | Old: `assets/{old-uuid}.jpg`<br>New: `assets/{new-uuid}.jpg` | Update record |
| **Product deleted** | Delete all associated images | `assets/{uuid}.jpg` | Delete cascade |

#### Implementation:
```java
// In ImageService or ProductService
@Transactional
public void deleteProductImage(UUID imageId) {
    ImageAsset imageAsset = imageAssetRepository.findById(imageId)
        .orElseThrow(() -> new NotFoundException("Image not found"));
    
    // Delete from S3
    s3ImageService.deleteImage(imageId);
    
    // DB record deleted by s3ImageService.deleteImage()
}
```

---

### 2. **Customizations Folder** (User-generated previews)

| Event | Action | S3 Path | DB Record |
|-------|--------|---------|-----------|
| **User deletes design** | Delete from S3 + DB | `customizations/{userId}/{customizationId}.jpg` | Delete from `customizations` |
| **Customization updated** | Replace in S3(same filename) | `customizations/{userId}/{customizationId}.jpg` | Update `preview_image_url` |
| **User account deleted** | Delete entire folder | `customizations/{userId}/` | Cascade delete |
| **Cart item removed** | ✅ Delete from S3 | `customizations/{userId}/{customizationId}.jpg` | Keep in `customizations` |
| **Cart cleared** | ✅ Delete all item previews from S3 | `customizations/{userId}/*.jpg` | Keep in `customizations` |
| **Abandoned uploads** | Cleanup after 24 hours | Orphaned files | No DB record |

#### Implementation:
```java
// In CustomizationService
@Transactional
public void deleteCustomization(String customizationId, UUID userId) {
    Customization customization = customizationRepository.findById(UUID.fromString(customizationId))
        .orElseThrow(() -> new NotFoundException("Customization not found"));
    
    // Verify ownership
    if (!customization.getUserId().equals(userId)) {
        throw new ForbiddenException("Not authorized");
    }
    
    // Extract S3 key from preview URL
    String s3Key = extractS3KeyFromUrl(customization.getPreviewImageUrl());
    
    // Delete from S3
    s3ImageService.deleteCustomizationPreview(s3Key);
    
    // Delete from database
    customizationRepository.delete(customization);
}

private String extractS3KeyFromUrl(String url) {
    // Extract key from: https://bucket.s3.region.amazonaws.com/customizations/userId/file.jpg
    // Or: https://cdn.domain.com/customizations/userId/file.jpg
    if (url.contains("amazonaws.com/")) {
        return url.substring(url.indexOf("customizations/"));
    } else if (url.contains("customizations/")) {
        return url.substring(url.indexOf("customizations/"));
    }
    return null;
}
```

#### Cart Item Cleanup (✅ IMPLEMENTED):
```java
// In CartService
private CartResponse removeItem(Cart cart, UUID itemId) {
    CartItem item = findCartItem(cart, itemId);
    
    // Delete customization preview from S3 if exists
    deleteCustomizationPreviewIfExists(item);
    
    cart.removeItem(item);
    cartRepository.save(cart);
    
    log.info("Removed item - itemId: {}", itemId);
    return cartMapper.toCartResponse(cart);
}

public CartResponse clearCart(User user) {
    Cart cart = getOrCreateCart(user);
    
    // Delete all customization previews from S3 before clearing
    cart.getItems().forEach(this::deleteCustomizationPreviewIfExists);
    
    cart.clearItems();
    cartRepository.save(cart);
    
    return cartMapper.toCartResponse(cart);
}

private void deleteCustomizationPreviewIfExists(CartItem item) {
    if (item.getCustomization() == null) {
        return;
    }
    
    String previewImageUrl = item.getCustomization().getPreviewImageUrl();
    if (previewImageUrl == null || previewImageUrl.isEmpty()) {
        return;
    }
    
    try {
        String s3Key = extractS3KeyFromUrl(previewImageUrl);
        
        if (s3Key != null && !s3Key.isEmpty()) {
            s3ImageService.deleteCustomizationPreview(s3Key);
            log.info("Deleted customization preview - customizationId: {}, s3Key: {}", 
                    item.getCustomization().getId(), s3Key);
        }
    } catch (Exception e) {
        // Log but don't fail the cart operation
        log.warn("Failed to delete customization preview - customizationId: {}, url: {}", 
                item.getCustomization().getId(), previewImageUrl, e);
    }
}
```

> 📝 **See also**: [S3_CUSTOMIZATION_PREVIEW_CLEANUP.md](./S3_CUSTOMIZATION_PREVIEW_CLEANUP.md) for detailed implementation
    // Extract key from: https://bucket.s3.region.amazonaws.com/customizations/userId/file.jpg
    // Or: https://cdn.domain.com/customizations/userId/file.jpg
    if (url.contains("amazonaws.com/")) {
        return url.substring(url.indexOf("customizations/"));
    } else if (url.contains("customizations/")) {
        return url.substring(url.indexOf("customizations/"));
    }
    return null;
}
```

---

## 🧹 Cleanup Jobs (Recommended)

### 1. **Orphaned Customization Images Cleanup**

**Problem**: User uploads preview but never saves customization (cancels, error, etc.)

**Solution**: Daily cron job to delete images without DB records

```java
@Service
@Slf4j
public class S3CleanupService {
    
    private final AmazonS3 amazonS3;
    private final CustomizationRepository customizationRepository;
    
    @Value("${aws.s3.bucket}")
    private String bucketName;
    
    /**
     * Runs daily at 2 AM
     * Deletes customization images older than 24 hours with no DB record
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOrphanedCustomizations() {
        log.info("Starting orphaned customization images cleanup...");
        
        try {
            // List all objects in customizations folder
            ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix("customizations/");
            
            ListObjectsV2Result result;
            int deletedCount = 0;
            
            do {
                result = amazonS3.listObjectsV2(request);
                
                for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
                    String s3Key = objectSummary.getKey();
                    Date lastModified = objectSummary.getLastModified();
                    
                    // Skip if file is less than 24 hours old
                    if (isLessThan24HoursOld(lastModified)) {
                        continue;
                    }
                    
                    // Extract customization ID from path: customizations/{userId}/{customizationId}.jpg
                    UUID customizationId = extractCustomizationId(s3Key);
                    
                    if (customizationId != null) {
                        // Check if customization exists in database
                        boolean exists = customizationRepository.existsById(customizationId);
                        
                        if (!exists) {
                            // Delete orphaned file
                            amazonS3.deleteObject(bucketName, s3Key);
                            deletedCount++;
                            log.info("Deleted orphaned customization: {}", s3Key);
                        }
                    }
                }
                
                request.setContinuationToken(result.getNextContinuationToken());
            } while (result.isTruncated());
            
            log.info("Cleanup completed. Deleted {} orphaned files", deletedCount);
            
        } catch (Exception e) {
            log.error("Error during orphaned customization cleanup", e);
        }
    }
    
    private boolean isLessThan24HoursOld(Date date) {
        long hoursSinceModified = (System.currentTimeMillis() - date.getTime()) / (1000 * 60 * 60);
        return hoursSinceModified < 24;
    }
    
    private UUID extractCustomizationId(String s3Key) {
        try {
            // Extract from: customizations/userId/customizationId.jpg
            String[] parts = s3Key.split("/");
            if (parts.length == 3) {
                String fileName = parts[2].substring(0, parts[2].lastIndexOf("."));
                return UUID.fromString(fileName);
            }
        } catch (Exception e) {
            log.warn("Failed to extract customization ID from: {}", s3Key);
        }
        return null;
    }
}
```

---

### 2. **Old Guest Customizations Cleanup**

**Problem**: Guest users create customizations that are never converted to permanent accounts

**Solution**: Weekly cleanup of guest customizations older than 30 days

```java
@Scheduled(cron = "0 0 3 * * SUN") // Every Sunday at 3 AM
public void cleanupOldGuestCustomizations() {
    log.info("Starting old guest customizations cleanup...");
    
    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
    
    // Find guest customizations older than 30 days
    List<Customization> oldGuestCustomizations = customizationRepository
        .findByUserIdIsNullAndCreatedAtBefore(cutoffDate);
    
    int deletedCount = 0;
    
    for (Customization customization : oldGuestCustomizations) {
        try {
            // Delete S3 image
            String s3Key = extractS3KeyFromUrl(customization.getPreviewImageUrl());
            if (s3Key != null) {
                amazonS3.deleteObject(bucketName, s3Key);
            }
            
            // Delete from database
            customizationRepository.delete(customization);
            deletedCount++;
            
        } catch (Exception e) {
            log.error("Failed to delete guest customization: {}", customization.getId(), e);
        }
    }
    
    log.info("Deleted {} old guest customizations", deletedCount);
}
```

---

### 3. **User Account Deletion Cascade**

**Problem**: When user deletes account, their customizations should be removed

**Solution**: Delete all user customizations in S3 when account is deleted

```java
@Service
public class UserService {
    
    @Transactional
    public void deleteUserAccount(UUID userId) {
        // 1. Get all user customizations
        List<Customization> customizations = customizationRepository.findByUserId(userId);
        
        // 2. Delete all S3 images
        for (Customization customization : customizations) {
            String s3Key = extractS3KeyFromUrl(customization.getPreviewImageUrl());
            if (s3Key != null) {
                s3ImageService.deleteCustomizationPreview(s3Key);
            }
        }
        
        // 3. Delete customizations from DB (cascade)
        customizationRepository.deleteAll(customizations);
        
        // 4. Delete entire user folder in S3 (cleanup any remaining files)
        String userFolderPrefix = "customizations/" + userId + "/";
        deleteS3Folder(userFolderPrefix);
        
        // 5. Delete user account
        userRepository.deleteById(userId);
    }
    
    private void deleteS3Folder(String prefix) {
        ListObjectsV2Request request = new ListObjectsV2Request()
            .withBucketName(bucketName)
            .withPrefix(prefix);
        
        ListObjectsV2Result result = amazonS3.listObjectsV2(request);
        
        for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
            amazonS3.deleteObject(bucketName, objectSummary.getKey());
        }
    }
}
```

---

## 📊 Database Queries

Add these methods to `CustomizationRepository`:

```java
public interface CustomizationRepository extends JpaRepository<Customization, UUID> {
    
    // For guest cleanup
    List<Customization> findByUserIdIsNullAndCreatedAtBefore(LocalDateTime date);
    
    // For user deletion
    List<Customization> findByUserId(UUID userId);
}
```

---

## ⚙️ S3 Lifecycle Policies (Alternative)

### Option: AWS S3 Lifecycle Rules

Instead of manual cleanup jobs, configure S3 lifecycle rules:

```xml
<!-- S3 Lifecycle Configuration -->
<LifecycleConfiguration>
    <Rule>
        <Id>Delete old customizations</Id>
        <Status>Enabled</Status>
        <Prefix>customizations/</Prefix>
        <Expiration>
            <Days>90</Days>
        </Expiration>
    </Rule>
</LifecycleConfiguration>
```

**Pros**: No code needed, automatic
**Cons**: Less control, may delete files that should be kept

---

## 🎯 Best Practices

1. **Always delete S3 files before DB records** - Prevents orphaned S3 files
2. **Log all deletions** - Helps debug issues
3. **Use soft deletes for customizations** - Add `deleted_at` column, keep for 30 days
4. **Monitor S3 storage costs** - Set up CloudWatch alerts
5. **Backup important images** - Use S3 versioning for assets folder
6. **Test cleanup jobs in staging** - Prevent accidental deletions

---

## 📝 Summary

| Cleanup Type | Trigger | Frequency | Implementation |
|--------------|---------|-----------|----------------|
| **Product image deletion** | Admin action | Immediate | `S3ImageService.deleteImage()` |
| **Customization deletion** | User action | Immediate | `CustomizationService.deleteCustomization()` |
| **Orphaned uploads** | Scheduled job | Daily | `S3CleanupService.cleanupOrphanedCustomizations()` |
| **Old guest designs** | Scheduled job | Weekly | `S3CleanupService.cleanupOldGuestCustomizations()` |
| **User account deletion** | User action | Immediate | `UserService.deleteUserAccount()` |

---

## 🚀 Implementation Checklist

- [ ] Add `S3CleanupService` with scheduled jobs
- [ ] Add `extractS3KeyFromUrl()` helper method
- [ ] Add repository methods for cleanup queries
- [ ] Implement cascade delete in `UserService`
- [ ] Add logging for all S3 deletions
- [ ] Set up CloudWatch monitoring for S3 storage
- [ ] Configure S3 lifecycle policies (optional)
- [ ] Test cleanup jobs in staging environment
- [ ] Document cleanup schedule for team

---

**Last Updated**: January 13, 2026
