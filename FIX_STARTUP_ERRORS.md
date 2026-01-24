# Fix Startup Errors Guide

## Errors Encountered

### Error 1: Database Column Constraint Violation
```
ERROR: column "phone_verified" of relation "users" contains null values
```

### Error 2: Missing PasswordEncoder Bean
```
No qualifying bean of type 'org.springframework.security.crypto.password.PasswordEncoder' available
```

---

## Solutions Applied

### ✅ Solution 1: Restored PasswordEncoder Bean

**File:** `SecurityConfig.java`

**Change:** Added back the `PasswordEncoder` bean because `OtpService` uses it to hash OTP codes for security.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Note:** Even though users don't have passwords anymore, OTPs are still hashed in Redis for security.

---

### ⚠️ Solution 2: Database Migration Required

You need to update your existing database records **before restarting Docker**.

#### Option A: Using Docker Exec (Recommended)

1. **Stop the backend container:**
   ```powershell
   docker-compose stop backend
   ```

2. **Connect to PostgreSQL:**
   ```powershell
   docker exec -it armoire-redis redis-cli FLUSHALL  # Clear Redis cache
   ```

3. **Run database migration:**
   Connect to your Neon database (or use a DB client) and run:
   ```sql
   -- Update existing users
   UPDATE users SET phone_verified = false WHERE phone_verified IS NULL;
   
   -- Verify the update
   SELECT id, phone, phone_verified, email FROM users;
   ```

#### Option B: Using psql Command

```powershell
# Replace with your actual database credentials from .env
docker run --rm -it postgres:latest psql -h <your-neon-host> -U <username> -d <database> -c "UPDATE users SET phone_verified = false WHERE phone_verified IS NULL;"
```

#### Option C: Clear All Data (Development Only)

If you're in development and don't need existing users:

```sql
-- Delete all users and start fresh
TRUNCATE TABLE users CASCADE;
TRUNCATE TABLE refresh_tokens CASCADE;
```

---

## Restart Instructions

After applying the database migration:

1. **Rebuild and restart Docker:**
   ```powershell
   docker-compose down
   docker-compose up --build -d
   ```

2. **Check logs:**
   ```powershell
   docker-compose logs -f backend
   ```

3. **Verify startup:**
   Look for:
   ```
   Tomcat started on port 8080
   Started ArmoireApplication
   ```

---

## Testing After Fix

1. **Health check:**
   ```powershell
   curl http://localhost:8080/api/v1/auth/health
   ```

2. **Send OTP:**
   ```powershell
   curl -X POST http://localhost:8080/api/v1/auth/send-otp `
     -H "Content-Type: application/json" `
     -d '{"phone":"+919876543210"}'
   ```

---

## Additional Notes

- **PasswordEncoder Use:** Now only used for OTP hashing, not user passwords
- **Database Schema:** `phone_verified` column defaults to `false` for new users
- **Migration Impact:** Existing users will have `phone_verified = false` and need to verify their phone

---

## If Errors Persist

Check these files for compilation errors:
```powershell
# From armoire directory
./mvnw clean compile
```

If Maven build fails, share the error output.
