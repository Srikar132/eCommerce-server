# Redis Setup Documentation

## Overview
Redis has been successfully integrated into the Armoire eCommerce application for OTP storage and caching functionality.

## Configuration Changes

### 1. Redis Configuration Class ✅
**File:** `src/main/java/com/nala/armoire/config/RedisConfig.java`

The configuration includes:
- **RedisTemplate**: For general Redis operations with JSON serialization
- **CacheManager**: For Spring Cache abstraction with 10-minute default TTL
- **@EnableCaching**: Enables Spring's annotation-driven cache management

### 2. Application Properties ✅

#### Local Development (`application.properties`)
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=
spring.data.redis.timeout=60000
spring.data.redis.jedis.pool.max-active=8
spring.data.redis.jedis.pool.max-idle=8
spring.data.redis.jedis.pool.min-idle=0
```

#### Docker Environment (`application-docker.properties`)
```properties
spring.data.redis.host=${REDIS_HOST:redis}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.timeout=60000
spring.data.redis.jedis.pool.max-active=8
spring.data.redis.jedis.pool.max-idle=8
spring.data.redis.jedis.pool.min-idle=0
```

### 3. Environment Variables (`.env`)
```properties
# ==========================
# Redis Configuration
# ==========================
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
```

### 4. Docker Compose Configuration ✅

#### Redis Service Added
- **Image**: redis:7-alpine (lightweight Redis 7)
- **Port**: 6379
- **Persistence**: Enabled with AOF (Append Only File)
- **Volume**: `redis-data` for data persistence
- **Health Check**: Redis PING command every 10 seconds

#### Backend Service Updated
- Added Redis environment variables
- Added dependency on Redis service with health check
- Backend waits for Redis to be healthy before starting

## Docker Services

### Redis Service
```yaml
redis:
  image: redis:7-alpine
  container_name: armoire-redis
  ports:
    - "6379:6379"
  command: redis-server --appendonly yes
  volumes:
    - redis-data:/data
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### Volume Configuration
```yaml
volumes:
  redis-data:
    driver: local
```

## How to Use

### Starting the Application

#### With Docker (Recommended)
```bash
# Start all services (Redis + Backend)
docker compose up -d

# View logs
docker compose logs -f

# Check Redis is running
docker compose ps redis

# Stop all services
docker compose down
```

#### Local Development
```bash
# Start Redis locally
redis-server

# Or with Docker (Redis only)
docker run -d -p 6379:6379 --name redis redis:7-alpine

# Run Spring Boot application
./mvnw spring-boot:run
```

### Testing Redis Connection

```bash
# Connect to Redis CLI (if running locally)
redis-cli

# In Docker
docker exec -it armoire-redis redis-cli

# Test commands
PING  # Should return PONG
KEYS *  # List all keys
```

## Cache Usage in Code

### Enable Caching on Methods
```java
@Service
public class SomeService {
    
    @Cacheable(value = "cacheName", key = "#id")
    public Data getData(String id) {
        // Method result will be cached
        return fetchData(id);
    }
    
    @CacheEvict(value = "cacheName", key = "#id")
    public void updateData(String id, Data data) {
        // Cache will be evicted
        saveData(id, data);
    }
}
```

### Direct Redis Operations
```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;

// Store OTP
redisTemplate.opsForValue().set("otp:" + userId, otpCode, 5, TimeUnit.MINUTES);

// Get OTP
String otp = (String) redisTemplate.opsForValue().get("otp:" + userId);

// Delete OTP
redisTemplate.delete("otp:" + userId);
```

## Benefits

✅ **Fast OTP Storage**: In-memory storage for quick access  
✅ **Automatic Expiration**: TTL support for temporary data  
✅ **Caching**: Improve application performance  
✅ **Persistence**: AOF enabled for data durability  
✅ **Health Checks**: Automatic monitoring and recovery  
✅ **Scalable**: Easy to scale with Redis Cluster if needed  

## Connection Pool Configuration

- **Max Active Connections**: 8
- **Max Idle Connections**: 8
- **Min Idle Connections**: 0
- **Timeout**: 60 seconds

## Security Notes

⚠️ **For Production:**
1. Set a strong Redis password in `.env`:
   ```
   REDIS_PASSWORD=your-strong-password-here
   ```
2. Update docker-compose.yml:
   ```yaml
   command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes
   ```
3. Consider using Redis over TLS
4. Restrict Redis port access in production

## Troubleshooting

### Redis Connection Issues
```bash
# Check if Redis is running
docker compose ps redis

# View Redis logs
docker compose logs redis

# Restart Redis
docker compose restart redis
```

### Clear Redis Cache
```bash
# Connect to Redis
docker exec -it armoire-redis redis-cli

# Clear all keys
FLUSHALL
```

### Backend Can't Connect to Redis
1. Ensure Redis service is healthy
2. Check environment variables in `.env`
3. Verify network connectivity between services
4. Check application logs for connection errors

## Next Steps

1. ✅ Redis dependency added to `pom.xml`
2. ✅ RedisConfig.java created
3. ✅ Application properties configured
4. ✅ Docker Compose updated with Redis service
5. ✅ Environment variables added
6. 🔄 Implement OTP storage using Redis
7. 🔄 Add caching to frequently accessed data
8. 🔄 Test Redis integration

## Resources

- [Spring Data Redis Documentation](https://spring.io/projects/spring-data-redis)
- [Redis Documentation](https://redis.io/documentation)
- [Redis Commands Reference](https://redis.io/commands)
