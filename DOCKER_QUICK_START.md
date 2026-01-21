# 🚀 Quick Start - Docker Setup Summary

## ✅ What Was Created

### 1. **Dockerfile** - Multi-stage Docker build
   - Stage 1: Builds your Spring Boot app with Maven
   - Stage 2: Creates lightweight runtime image with JRE only
   - Runs as non-root user for security
   - Optimized for production use

### 2. **.dockerignore** - Excludes unnecessary files
   - Reduces build context size
   - Speeds up builds
   - Prevents sensitive files from being copied

### 3. **application-docker.properties** - Docker-specific config
   - Uses environment variables for flexibility
   - Elasticsearch points to container name
   - Database can be configured via env vars

### 4. **docker-compose.yml** - Enhanced configuration
   - Includes Elasticsearch (already existed)
   - Added your Spring Boot backend service
   - Configured networking between services
   - Health checks for both services

### 5. **.env.example** - Template for environment variables
   - Copy to `.env` and fill with your values
   - Never commit `.env` to Git!

### 6. **docker-start.ps1** - Interactive PowerShell script
   - Easy-to-use menu for common Docker tasks
   - Builds, runs, and manages containers
   - Perfect for beginners!

### 7. **DOCKER_SETUP_GUIDE.md** - Comprehensive documentation
   - Step-by-step instructions
   - Troubleshooting guide
   - Production best practices

---

## 🎯 How to Get Started (3 Simple Options)

### Option 1: Use the PowerShell Script (Easiest!)

1. **Open PowerShell in the armoire directory:**
   ```powershell
   cd E:\Websites\eCommerce\armoire
   ```

2. **Run the script:**
   ```powershell
   .\docker-start.ps1
   ```

3. **Follow the menu options:**
   - Choose option 3 for docker-compose (recommended)
   - Or choose option 2 for a simple docker run

---

### Option 2: Manual Docker Commands

#### Step 1: Build the Image
```powershell
docker build -t armoire-backend:latest .
```

#### Step 2: Run the Container
```powershell
docker run -d `
  --name armoire-backend `
  -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=docker `
  -e DATABASE_URL="your-database-url" `
  -e DATABASE_USERNAME="your-username" `
  -e DATABASE_PASSWORD="your-password" `
  -e JWT_SECRET="your-jwt-secret" `
  armoire-backend:latest
```

#### Step 3: Check Logs
```powershell
docker logs -f armoire-backend
```

---

### Option 3: Using Docker Compose (Recommended)

#### Step 1: Create .env file
```powershell
# Copy the example
Copy-Item .env.example .env

# Edit with your actual values
notepad .env
```

#### Step 2: Start All Services
```powershell
docker-compose up -d --build
```

#### Step 3: Check Status
```powershell
docker-compose ps
docker-compose logs -f backend
```

---

## 📋 Pre-Flight Checklist

Before building, make sure:

- [ ] Docker Desktop is installed and running
- [ ] You're in the `armoire` directory
- [ ] You have your database credentials ready
- [ ] You have your AWS credentials (if using S3)
- [ ] You have your JWT secret key ready
- [ ] Port 8080 is not being used by another application

---

## 🔍 Quick Verification Steps

### 1. Check if Docker is Running
```powershell
docker --version
docker info
```

### 2. Build the Image
```powershell
docker build -t armoire-backend:latest .
```
**Expected:** Build completes without errors (~2-5 minutes)

### 3. Verify Image Created
```powershell
docker images | Select-String "armoire-backend"
```
**Expected:** You see `armoire-backend` with `latest` tag

### 4. Run Container
```powershell
docker run -d --name test-backend -p 8080:8080 -e SPRING_PROFILES_ACTIVE=docker armoire-backend:latest
```

### 5. Check Logs
```powershell
docker logs test-backend
```
**Look for:** "Started ArmoireApplication" (Spring Boot startup message)

### 6. Test Application
```powershell
# In PowerShell
Invoke-WebRequest -Uri http://localhost:8080/actuator/health
```
**Expected:** Status 200 with JSON response

### 7. Clean Up Test
```powershell
docker stop test-backend
docker rm test-backend
```

---

## 🆘 Common Issues & Quick Fixes

### Issue: "Docker is not running"
**Fix:** Start Docker Desktop from Windows Start menu

### Issue: "Port 8080 already in use"
**Fix:** Either stop the other application or use a different port:
```powershell
docker run -p 9090:8080 armoire-backend:latest
```

### Issue: "Cannot connect to database"
**Fix:** Check your DATABASE_URL environment variable. For external databases, make sure the URL is accessible from Docker.

### Issue: "Build fails at Maven step"
**Fix:** Clear cache and rebuild:
```powershell
docker build --no-cache -t armoire-backend:latest .
```

### Issue: "Container starts then exits immediately"
**Fix:** Check logs for errors:
```powershell
docker logs armoire-backend
```

---

## 🎓 What Each File Does

| File | Purpose |
|------|---------|
| `Dockerfile` | Instructions to build your Docker image |
| `.dockerignore` | Files to exclude from Docker build |
| `application-docker.properties` | Configuration for Docker environment |
| `docker-compose.yml` | Multi-container orchestration |
| `.env` | Your secret environment variables (create from .env.example) |
| `.env.example` | Template for environment variables |
| `docker-start.ps1` | Interactive script to manage Docker |
| `DOCKER_SETUP_GUIDE.md` | Comprehensive documentation |

---

## 📊 Typical Build & Run Timeline

| Step | Time | What Happens |
|------|------|--------------|
| Build (first time) | 3-5 min | Downloads base images, Maven dependencies, compiles code |
| Build (subsequent) | 30-60 sec | Uses cached layers, only rebuilds changed parts |
| Container startup | 20-40 sec | Starts Spring Boot application |
| Ready for requests | ~1 min total | Application fully initialized |

---

## 🔐 Security Reminders

1. **Never commit `.env` file** - Add it to `.gitignore`
2. **Use strong JWT secrets** - Minimum 256 bits
3. **Change default passwords** - Especially for production
4. **Use environment variables** - Don't hardcode secrets
5. **Keep images updated** - Regularly rebuild with latest base images

---

## 🌟 Best Practices You're Already Following

✅ Multi-stage build (smaller images)  
✅ Non-root user (better security)  
✅ Layer caching (faster builds)  
✅ Health checks (container monitoring)  
✅ Environment variables (configuration flexibility)  
✅ Docker Compose (service orchestration)  
✅ .dockerignore (cleaner builds)  

---

## 📚 Learn More

- **Full Documentation:** Read `DOCKER_SETUP_GUIDE.md`
- **Docker Docs:** https://docs.docker.com
- **Spring Boot with Docker:** https://spring.io/guides/gs/spring-boot-docker/

---

## 🎯 Your Next Steps

1. **Test locally:**
   ```powershell
   .\docker-start.ps1
   ```
   Choose option 3 (docker-compose)

2. **Verify everything works:**
   - Check logs: `docker-compose logs -f backend`
   - Test health: http://localhost:8080/actuator/health
   - Test your API endpoints

3. **When ready for production:**
   - Use specific image tags (not `latest`)
   - Set up proper secrets management
   - Configure monitoring and logging
   - Use a proper reverse proxy (nginx)

---

## 💡 Pro Tips

- **Keep builds fast:** Don't change `pom.xml` unless needed (Docker caches dependencies)
- **Use docker-compose for development:** Easier to manage multiple services
- **Check logs frequently:** `docker logs -f container-name`
- **Clean up regularly:** `docker system prune` to free space
- **Tag your images:** Use version tags like `v1.0.0` instead of `latest`

---

## 📞 Quick Help Commands

```powershell
# Is Docker running?
docker info

# What images do I have?
docker images

# What's running now?
docker ps

# See all containers (including stopped)
docker ps -a

# View logs
docker logs -f armoire-backend

# Stop everything
docker-compose down

# Start everything
docker-compose up -d

# Rebuild and start
docker-compose up -d --build

# Free up space
docker system prune -a
```

---

**You're all set! 🎉 Start with the PowerShell script for the easiest experience.**

**Questions? Check `DOCKER_SETUP_GUIDE.md` for detailed explanations!**
