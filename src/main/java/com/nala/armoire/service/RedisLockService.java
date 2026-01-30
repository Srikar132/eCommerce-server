package com.nala.armoire.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Simple Redis-based distributed locking service
 * 
 * <p>Purpose: Prevents race conditions in distributed environments
 * by using Redis as a centralized lock coordinator.
 * 
 * <p>Features:
 * <ul>
 *   <li>Automatic lock expiration to prevent deadlocks</li>
 *   <li>Unique lock values to prevent accidental unlocking</li>
 *   <li>Simple API for acquire/release operations</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Attempts to acquire a distributed lock
     * 
     * @param lockKey The unique key for the lock (e.g., "cart:lock:user:123")
     * @param timeoutSeconds How long to hold the lock before auto-expiry
     * @return Lock token if successful, null if lock could not be acquired
     */
    public String tryLock(String lockKey, long timeoutSeconds) {
        String lockValue = UUID.randomUUID().toString();
        
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(timeoutSeconds));
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Lock acquired - key: {}, token: {}", lockKey, lockValue);
                return lockValue;
            }
            
            log.debug("Lock not acquired - key: {}", lockKey);
            return null;
            
        } catch (Exception e) {
            log.error("Failed to acquire lock: {}", lockKey, e);
            return null;
        }
    }

    /**
     * Attempts to acquire a lock with retry logic
     * 
     * @param lockKey The unique key for the lock
     * @param timeoutSeconds Lock expiration time
     * @param waitSeconds How long to wait/retry before giving up
     * @return Lock token if successful, null if failed after retries
     */
    public String tryLockWithWait(String lockKey, long timeoutSeconds, long waitSeconds) {
        long startTime = System.currentTimeMillis();
        long waitMillis = TimeUnit.SECONDS.toMillis(waitSeconds);
        
        while (System.currentTimeMillis() - startTime < waitMillis) {
            String lockToken = tryLock(lockKey, timeoutSeconds);
            if (lockToken != null) {
                return lockToken;
            }
            
            try {
                Thread.sleep(100); // Wait 100ms between retries
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Lock acquisition interrupted - key: {}", lockKey);
                return null;
            }
        }
        
        log.warn("Failed to acquire lock after {}s - key: {}", waitSeconds, lockKey);
        return null;
    }

    /**
     * Releases a distributed lock
     * 
     * @param lockKey The lock key
     * @param lockToken The token received when lock was acquired
     * @return true if lock was released, false if lock wasn't held or token mismatch
     */
    public boolean releaseLock(@NonNull String lockKey, String lockToken) {
        if (lockToken == null) {
            return false;
        }
        
        try {
            String currentValue = redisTemplate.opsForValue().get(lockKey);
            
            // Only delete if the lock value matches (prevents accidental unlock)
            if (lockToken.equals(currentValue)) {
                redisTemplate.delete(lockKey);
                log.debug("Lock released - key: {}", lockKey);
                return true;
            }
            
            log.debug("Lock token mismatch - key: {}, expected: {}, actual: {}", 
                    lockKey, lockToken, currentValue);
            return false;
            
        } catch (Exception e) {
            log.error("Failed to release lock: {}", lockKey, e);
            return false;
        }
    }

    /**
     * Generates a standardized lock key for cart operations
     * 
     * @param userId User ID
     * @return Lock key string
     */
    public String getCartLockKey(UUID userId) {
        return "cart:lock:user:" + userId.toString();
    }
}
