# 🐳 Docker Setup Guide for Spring Boot Backend

## 📋 Table of Contents
1. [Understanding the Dockerfile](#understanding-the-dockerfile)
2. [Building the Docker Image](#building-the-docker-image)
3. [Running the Container](#running-the-container)
4. [Using Docker Compose](#using-docker-compose)
5. [Environment Variables](#environment-variables)
6. [Troubleshooting](#troubleshooting)
7. [Production Best Practices](#production-best-practices)

---

## 🎯 Understanding the Dockerfile

### Multi-Stage Build Approach
Our Dockerfile uses a **multi-stage build** with two stages:

#### **Stage 1: Build Stage**
```dockerfile
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
```
- Uses Maven with Java 17 (matches your pom.xml)
- Compiles the Spring Boot application
- Creates the JAR file in the `target/` directory
- This stage is discarded in the final image (keeps image size small)

#### **Stage 2: Runtime Stage**
```dockerfile
FROM eclipse-temurin:17-jre-alpine
```
- Uses only Java Runtime Environment (JRE), not the full JDK
- Much smaller image size (~150MB vs ~400MB)
- Only includes what's needed to run the application

### Key Benefits
✅ **Small Image Size**: Multi-stage build discards build tools  
✅ **Security**: Runs as non-root user  
✅ **Caching**: Separate layers for dependencies and source code  
✅ **Production-Ready**: Optimized for performance and security

---

## 🔨 Building the Docker Image

### Step 1: Navigate to Project Directory
```powershell
cd E:\Websites\eCommerce\armoire
```

### Step 2: Build the Image
```powershell
docker build -t armoire-backend:latest .
```

**Explanation:**
- `docker build`: Command to build an image
- `-t armoire-backend:latest`: Tag the image with name and version
  - `armoire-backend`: Image name
  - `latest`: Tag/version (you can use `v1.0`, `dev`, etc.)
- `.`: Use current directory as build context

### Step 3: Verify the Image
```powershell
docker images
```

You should see output like:
```
REPOSITORY          TAG       IMAGE ID       CREATED          SIZE
armoire-backend     latest    abc123def456   10 seconds ago   180MB
```

### Alternative: Build with Custom Tag
```powershell
# For development
docker build -t armoire-backend:dev .

# For production
docker build -t armoire-backend:v1.0.0 .
```

---

## 🚀 Running the Container

### Method 1: Basic Run (Testing)
```powershell
docker run -p 8080:8080 armoire-backend:latest
```

**Explanation:**
- `docker run`: Create and start a container
- `-p 8080:8080`: Port mapping (host:container)
  - First `8080`: Port on your computer
  - Second `8080`: Port inside container
- `armoire-backend:latest`: Image to use

### Method 2: Run with Environment Variables
```powershell
docker run -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=docker `
  -e DATABASE_URL="jdbc:postgresql://your-db-host:5432/armoire" `
  -e DATABASE_USERNAME=your_username `
  -e DATABASE_PASSWORD=your_password `
  -e JWT_SECRET=your_secure_secret `
  armoire-backend:latest
```

### Method 3: Run in Detached Mode (Background)
```powershell
docker run -d `
  --name armoire-backend-container `
  -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=docker `
  armoire-backend:latest
```

**Explanation:**
- `-d`: Detached mode (runs in background)
- `--name`: Give container a custom name
- `-e`: Set environment variables

### Useful Container Commands

#### View Running Containers
```powershell
docker ps
```

#### View All Containers (including stopped)
```powershell
docker ps -a
```

#### View Container Logs
```powershell
docker logs armoire-backend-container

# Follow logs in real-time
docker logs -f armoire-backend-container

# View last 100 lines
docker logs --tail 100 armoire-backend-container
```

#### Stop Container
```powershell
docker stop armoire-backend-container
```

#### Start Stopped Container
```powershell
docker start armoire-backend-container
```

#### Remove Container
```powershell
docker rm armoire-backend-container

# Force remove running container
docker rm -f armoire-backend-container
```

#### Execute Commands Inside Container
```powershell
# Open shell in container
docker exec -it armoire-backend-container sh

# Check Java version
docker exec armoire-backend-container java -version
```

---

## 🐋 Using Docker Compose

### Step 1: Use the Enhanced docker-compose.yml
The updated `docker-compose.yml` includes your backend service.

### Step 2: Create Environment File
Create `.env` file in the `armoire` directory:
```env
# Database
DATABASE_URL=jdbc:postgresql://ep-mute-meadow-a1um972m-pooler.ap-southeast-1.aws.neon.tech/neondb?user=neondb_owner&password=npg_Q5fnBeSO7Isj&sslmode=require&channelBinding=require
DATABASE_USERNAME=neondb_owner
DATABASE_PASSWORD=npg_Q5fnBeSO7Isj

# JWT
JWT_SECRET=THISISMYSECURESECRETKEYFORJWTECOMMERCEBACKEND123456
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000

# Mail
MAIL_USERNAME=bunnyking828@gmail.com
MAIL_PASSWORD=rwmc rraa adwm ukcq

# AWS
AWS_ACCESS_KEY=AKIASOLKTCZLEWGLZYRX
AWS_SECRET_KEY=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
AWS_REGION=eu-north-1
AWS_S3_BUCKET=nala-armorie-storage

# Frontend
FRONTEND_URL=http://localhost:3000

# Razorpay
RAZORPAY_KEY_ID=YOUR_RAZORPAY_KEY_ID
RAZORPAY_KEY_SECRET=YOUR_RAZORPAY_KEY_SECRET

# Admin
ADMIN_EMAIL=bunnyking828@gmail.com
```

### Step 3: Start All Services
```powershell
docker-compose up -d
```

**Explanation:**
- Starts both Elasticsearch and your backend
- `-d`: Detached mode (background)

### Step 4: View Logs
```powershell
# All services
docker-compose logs -f

# Only backend
docker-compose logs -f backend

# Only elasticsearch
docker-compose logs -f elasticsearch
```

### Step 5: Stop Services
```powershell
docker-compose down
```

### Step 6: Rebuild and Restart
```powershell
# Rebuild images and restart
docker-compose up -d --build

# Remove volumes (careful: deletes data)
docker-compose down -v
```

---

## 🔐 Environment Variables

### Required Variables
| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile to use | `docker` |
| `DATABASE_URL` | PostgreSQL connection string | `jdbc:postgresql://host:5432/db` |
| `DATABASE_USERNAME` | Database username | `postgres` |
| `DATABASE_PASSWORD` | Database password | `secretpassword` |
| `JWT_SECRET` | JWT signing secret (min 256 bits) | `your-secure-secret-key` |

### Optional Variables
| Variable | Description | Default |
|----------|-------------|---------|
| `ELASTICSEARCH_URIS` | Elasticsearch URL | `http://elasticsearch:9200` |
| `FRONTEND_URL` | Frontend application URL | `http://localhost:3000` |
| `AWS_ACCESS_KEY` | AWS access key for S3 | - |
| `AWS_SECRET_KEY` | AWS secret key for S3 | - |
| `MAIL_USERNAME` | SMTP username | - |
| `MAIL_PASSWORD` | SMTP password | - |

---

## 🔧 Troubleshooting

### Issue 1: Container Starts but Application Crashes
**Check logs:**
```powershell
docker logs armoire-backend-container
```

**Common causes:**
- Database connection failure
- Missing environment variables
- Port already in use

### Issue 2: Cannot Connect to Database
**Solution for external database:**
```powershell
# Use your actual database URL
docker run -p 8080:8080 `
  -e DATABASE_URL="jdbc:postgresql://your-external-db:5432/dbname" `
  armoire-backend:latest
```

**Solution for local PostgreSQL:**
```powershell
# Windows: Use host.docker.internal
docker run -p 8080:8080 `
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/armoire" `
  armoire-backend:latest
```

### Issue 3: Port Already in Use
```powershell
# Use a different host port
docker run -p 9090:8080 armoire-backend:latest
```
Access at: http://localhost:9090

### Issue 4: Build Fails - Maven Dependencies
```powershell
# Clear Maven cache and rebuild
docker build --no-cache -t armoire-backend:latest .
```

### Issue 5: Out of Memory
```powershell
# Increase Java heap size
docker run -p 8080:8080 `
  -e JAVA_OPTS="-Xms1024m -Xmx2048m" `
  armoire-backend:latest
```

### Issue 6: Cannot Access Application
**Check container is running:**
```powershell
docker ps
```

**Check application logs:**
```powershell
docker logs armoire-backend-container
```

**Test connection:**
```powershell
curl http://localhost:8080/actuator/health
```

---

## 🎯 Production Best Practices

### 1. Use Specific Tags (Not `latest`)
```powershell
docker build -t armoire-backend:1.0.0 .
```

### 2. Use Secrets Management
Don't hardcode sensitive data. Use Docker secrets or external secret managers.

```powershell
# Using Docker secrets (Docker Swarm)
docker secret create db_password ./db_password.txt
```

### 3. Health Checks
Uncomment the HEALTHCHECK in Dockerfile and ensure Spring Boot Actuator is configured.

Add to `pom.xml` if not present:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 4. Resource Limits
```powershell
docker run -d `
  --name armoire-backend `
  --memory="1g" `
  --cpus="1.0" `
  -p 8080:8080 `
  armoire-backend:latest
```

### 5. Network Isolation
```powershell
# Create custom network
docker network create armoire-network

# Run container in network
docker run -d `
  --name armoire-backend `
  --network armoire-network `
  -p 8080:8080 `
  armoire-backend:latest
```

### 6. Volume for Logs
```powershell
docker run -d `
  --name armoire-backend `
  -v armoire-logs:/app/logs `
  -p 8080:8080 `
  armoire-backend:latest
```

### 7. Multi-Environment Configs
```powershell
# Development
docker run -e SPRING_PROFILES_ACTIVE=dev armoire-backend:latest

# Production
docker run -e SPRING_PROFILES_ACTIVE=prod armoire-backend:latest
```

---

## 📊 Monitoring and Maintenance

### View Resource Usage
```powershell
docker stats armoire-backend-container
```

### Prune Unused Resources
```powershell
# Remove unused images
docker image prune -a

# Remove unused containers
docker container prune

# Remove everything unused
docker system prune -a --volumes
```

### Backup Container Data
```powershell
# Export container as tar
docker export armoire-backend-container > armoire-backup.tar

# Save image as tar
docker save armoire-backend:latest > armoire-image.tar
```

---

## 🎓 Quick Reference Commands

```powershell
# Build image
docker build -t armoire-backend:latest .

# Run container
docker run -d --name armoire-backend -p 8080:8080 armoire-backend:latest

# View logs
docker logs -f armoire-backend

# Stop container
docker stop armoire-backend

# Remove container
docker rm armoire-backend

# Docker Compose (start all services)
docker-compose up -d

# Docker Compose (stop all services)
docker-compose down

# Docker Compose (rebuild)
docker-compose up -d --build
```

---

## 📞 Need Help?

If you encounter issues:
1. Check logs: `docker logs armoire-backend-container`
2. Verify environment variables
3. Ensure database is accessible
4. Check port conflicts
5. Review application.properties configuration

---

**Happy Dockerizing! 🐳**
