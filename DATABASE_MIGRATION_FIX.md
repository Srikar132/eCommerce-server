# 🔧 Database Schema Validation Error - Quick Fix

## ❌ Error
```
Schema-validation: missing column [replaced_by_token_id] in table [refresh_tokens]
```

## 🎯 Problem

The `refresh_tokens` table in your database is **missing the `replaced_by_token_id` column** that we added to the `RefreshToken` entity for token rotation tracking.

This happens when:
1. The database table already exists
2. We add new fields to the entity
3. Hibernate tries to validate but the column doesn't exist yet

---

## ✅ Solution Options

### Option 1: Run Migration Script (RECOMMENDED) 🚀

**Step 1:** Open your PostgreSQL database (Neon Console or pgAdmin)

**Step 2:** Run this SQL:
```sql
ALTER TABLE refresh_tokens 
ADD COLUMN IF NOT EXISTS replaced_by_token_id VARCHAR(255);
```

**That's it!** Restart your application.

---

### Option 2: Use Full Migration Script 📝

Run the complete migration script: `refresh_token_migration.sql`

```bash
# Connect to your database
psql -h ep-mute-meadow-a1um972m-pooler.ap-southeast-1.aws.neon.tech \
     -U neondb_owner \
     -d neondb \
     -f refresh_token_migration.sql
```

---

### Option 3: Let Hibernate Auto-Update ⚡

**Temporarily change** JPA configuration:

**Edit `application.properties`:**
```properties
# Change from 'validate' to 'update'
spring.jpa.hibernate.ddl-auto=update
```

**Steps:**
1. Stop your application
2. Change `ddl-auto=update` in `application.properties`
3. Start application (Hibernate will add the missing column)
4. Stop application
5. Change back to `ddl-auto=validate` (optional, for production safety)
6. Start application

**Note:** Your current config already has `update`, so just restart the app!

---

### Option 4: Drop and Recreate Table (⚠️ DATA LOSS)

**Only if you don't have important data:**

```sql
-- CAREFUL: This deletes all refresh tokens!
DROP TABLE IF EXISTS refresh_tokens CASCADE;

-- The table will be recreated automatically on next app start
-- Or run the CREATE TABLE statement from refresh_token_migration.sql
```

---

## 🔍 Check Current Table Schema

**Run this to see current columns:**
```sql
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'refresh_tokens' 
ORDER BY ordinal_position;
```

**Expected columns:**
- ✅ `id` (uuid)
- ✅ `token_id` (varchar)
- ✅ `user_id` (uuid)
- ✅ `expires_at` (timestamp)
- ✅ `created_at` (timestamp)
- ✅ `revoked` (boolean)
- ✅ `revoked_at` (timestamp)
- ⚠️ `replaced_by_token_id` (varchar) ← **THIS IS MISSING!**

---

## 🎯 Recommended Fix (Step by Step)

### For Neon Database (Your Setup):

**1. Open Neon Console:**
   - Go to https://console.neon.tech
   - Select your project
   - Click "SQL Editor"

**2. Run this single command:**
```sql
ALTER TABLE refresh_tokens 
ADD COLUMN replaced_by_token_id VARCHAR(255);
```

**3. Verify it was added:**
```sql
SELECT * FROM information_schema.columns 
WHERE table_name = 'refresh_tokens' 
AND column_name = 'replaced_by_token_id';
```

**4. Restart your Spring Boot application**

**Done!** ✅

---

## 🔄 Alternative: Let Spring Boot Handle It

Your `application.properties` already has:
```properties
spring.jpa.hibernate.ddl-auto=update
```

This should **automatically add missing columns**!

**Why it's not working?**
- Database connection might be in read-only mode
- User permissions might not allow ALTER TABLE
- Table might be locked

**Solution:**
Just run the SQL manually (Option 1 above) ✅

---

## 🛠️ If Table Doesn't Exist at All

If the `refresh_tokens` table doesn't exist, Spring Boot will create it automatically on startup.

**Or create it manually:**
```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    replaced_by_token_id VARCHAR(255),
    
    CONSTRAINT fk_refresh_token_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_token_id ON refresh_tokens(token_id);
CREATE INDEX idx_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_expires_at ON refresh_tokens(expires_at);
```

---

## 🧪 Verify Fix

After applying the fix, check if the error is gone:

**1. Start application:**
```bash
./mvnw.cmd spring-boot:run
```

**2. Check logs:**
```
✅ Should see: "Started ArmoireApplication"
❌ Should NOT see: "Schema-validation: missing column"
```

**3. Test refresh token endpoint:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "your-refresh-token"}'
```

---

## 📋 Summary

**Quickest Fix (30 seconds):**
```sql
-- Run this in Neon SQL Editor
ALTER TABLE refresh_tokens 
ADD COLUMN replaced_by_token_id VARCHAR(255);
```

Then restart your app. Done! ✅

---

## 🚨 Common Issues

### Issue 1: Permission Denied
```
ERROR: permission denied for table refresh_tokens
```

**Solution:** Use database admin user or grant permissions:
```sql
GRANT ALTER ON TABLE refresh_tokens TO neondb_owner;
```

---

### Issue 2: Table Doesn't Exist
```
ERROR: relation "refresh_tokens" does not exist
```

**Solution:** The table will be created automatically on next app start, or use the CREATE TABLE statement above.

---

### Issue 3: Still Getting Error After Adding Column
**Solution:** 
1. Clear compiled classes: `./mvnw.cmd clean`
2. Rebuild: `./mvnw.cmd compile`
3. Restart application

---

**Last Updated:** January 28, 2026  
**Status:** ✅ Easy Fix - Just Run SQL ALTER TABLE Statement
