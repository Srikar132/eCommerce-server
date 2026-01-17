# S3 Image Upload Flow - Quick Reference

## 📸 Two Upload Types

### 1. **Product Images** (Admin Only)
```
Folder: assets/
Files: {uuid}.jpg
Database: image_assets table
Lifecycle: Permanent (until admin deletes)
```

### 2. **Customization Previews** (User Generated)
```
Folder: customizations/{userId}/
Files: {customizationId}.jpg
Database: customizations table (preview_image_url field)
Lifecycle: Temporary (auto-cleanup after 24h if orphaned)
```

---

## 🔄 Correct Workflow

### ✅ RIGHT WAY:

```
Frontend:
1. User clicks "Save Design"
2. Generate preview image from Konva canvas
3. Call: uploadCustomizationPreview(file) 
   → Returns: previewUrl
4. Call: saveCustomization({ ..., previewImageUrl: previewUrl })
   → Backend generates customizationId and saves

Backend:
1. POST /upload-preview
   - Generates temp UUID for file naming
   - Uploads to: customizations/{userId}/{tempUUID}.jpg
   - Returns: { s3Url, cdnUrl }
   
2. POST /save
   - Creates customization record
   - Stores previewImageUrl from step 1
   - Returns: { id, previewImageUrl, ... }
```

### ❌ WRONG WAY (Previous):
```
❌ Need customizationId BEFORE upload
❌ Can't upload without existing record
❌ Circular dependency
```

---

## 🗂️ S3 Bucket Structure

```
your-ecommerce-bucket/
│
├── assets/                           # Product images (permanent)
│   ├── a1b2c3d4-e5f6-7890.jpg       # Product image 1
│   ├── b2c3d4e5-f6g7-8901.jpg       # Product image 2
│   └── c3d4e5f6-g7h8-9012.jpg       # Banner image
│
└── customizations/                   # User designs (temporary)
    ├── user-uuid-123/
    │   ├── custom-uuid-001.jpg      # User's design 1
    │   └── custom-uuid-002.jpg      # User's design 2
    │
    └── user-uuid-456/
        ├── custom-uuid-003.jpg      # Another user's design
        └── custom-uuid-004.jpg
```

---

## 🧹 Cleanup Strategy Summary

| Scenario | When | How |
|----------|------|-----|
| **Orphaned upload** | File uploaded but never saved | Cron job (daily): Delete files >24h old with no DB record |
| **User deletes design** | DELETE /customization/{id} | Immediate: Delete S3 + DB record |
| **Old guest designs** | Guest never registers | Cron job (weekly): Delete guest records >30 days |
| **Account deletion** | User closes account | Immediate: Delete entire `customizations/{userId}/` folder |
| **Product image deleted** | Admin removes image | Immediate: Delete from `assets/` + DB record |

---

## 🔗 API Endpoints

### Customization Upload
```
POST /api/v1/customization/upload-preview
Content-Type: multipart/form-data
Body: { file: File }
Response: { s3Url, cdnUrl, fileSize, dimensions }
```

### Customization Save
```
POST /api/v1/customization/save
Content-Type: application/json
Body: {
  productId: string,
  variantId: string,
  designData: string,
  previewImageUrl: string,  ← From upload-preview
  designName: string
}
Response: { id, previewImageUrl, ... }
```

---

## 📋 Frontend Checklist

```typescript
import { customizationApi } from "@/lib/api/customization";

// 1. Generate preview
const dataURL = stage.toDataURL({ mimeType: "image/jpeg", quality: 0.8 });
const blob = await fetch(dataURL).then(r => r.blob());
const file = new File([blob], "preview.jpg", { type: "image/jpeg" });

// 2. Upload preview
const previewUrl = await customizationApi.uploadPreviewImage(file);

// 3. Save with preview URL
await customizationApi.saveCustomization({
  productId: "...",
  variantId: "...",
  designData: JSON.stringify({...}),
  previewImageUrl: previewUrl,  // ← Important!
  designName: "My Design"
});
```

---

## 🚨 Common Mistakes to Avoid

1. ❌ **Uploading after save** → Upload BEFORE save
2. ❌ **Not validating file size** → Check 5MB limit on frontend
3. ❌ **Forgetting to delete S3 on customization delete** → Always cleanup
4. ❌ **Not handling upload errors** → Use try/catch
5. ❌ **Using S3 URL in production** → Use CDN URL when available

---

## ⚙️ Environment Variables

```properties
# application.properties
aws.s3.bucket=your-ecommerce-bucket
aws.s3.region=us-east-1
aws.cloudfront.domain=d123456.cloudfront.net
```

```env
# .env.local (frontend)
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

---

## 🎯 Key Files

**Backend:**
- `S3ImageService.java` - Upload/delete logic
- `CustomizationController.java` - Upload endpoint
- `S3CleanupService.java` - Scheduled cleanup jobs
- `ImageAsset.java` - Entity for assets table

**Frontend:**
- `lib/api/customization.ts` - API calls
- `components/customization/studio.tsx` - Canvas component

**Documentation:**
- `docs/S3_IMAGE_CLEANUP_STRATEGY.md` - Detailed cleanup guide
- `docs/CUSTOMIZATION_UPLOAD_GUIDE.md` - Frontend implementation

---

**Last Updated**: January 13, 2026
