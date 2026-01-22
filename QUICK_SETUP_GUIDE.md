# 🚨 CRITICAL: Out of Memory Error (Exit Code 137)

## 🔴 **The Problem:**

```
Container elasticsearch exited (137)
```

**Exit code 137 = Out of Memory (OOM Kill)**
- Your EC2 instance doesn't have enough RAM
- Elasticsearch container starts but gets killed immediately
- Linux kernel is killing the process to protect the system

---

## 🔍 **Root Cause:**

Elasticsearch is a memory-intensive application. Your EC2 instance likely has:
- **Too little RAM** (probably t2.micro with 1GB or t3.small with 2GB)
- **Other processes using memory**
- **Not enough swap space**

---

## ✅ **Solutions (Choose ONE):**

### **🎯 Option 1: Reduce Memory Usage (RECOMMENDED - Quick Fix)**

I've already updated your `docker-compose.yml` with minimal memory settings:

**Changes Made:**
```yaml
# Elasticsearch - Reduced from 512m to 256m
ES_JAVA_OPTS=-Xms256m -Xmx256m
deploy:
  resources:
    limits:
      memory: 512m
    reservations:
      memory: 256m

# Backend - Reduced from 1024m to 512m
JAVA_OPTS=-Xms256m -Xmx512m
deploy:
  resources:
    limits:
      memory: 1024m
    reservations:
      memory: 512m
```

**Total Memory Required:**
- Elasticsearch: ~512 MB
- Backend: ~1024 MB
- System: ~512 MB
- **Total: ~2 GB minimum**

---

### **🎯 Option 2: Upgrade EC2 Instance (BEST for Production)**

| Instance Type | vCPU | RAM | Cost/month | Recommendation |
|---------------|------|-----|------------|----------------|
| t2.micro | 1 | 1 GB | ~$8 | ❌ Too small |
| t3.small | 2 | 2 GB | ~$15 | ⚠️ Minimum (might work) |
| **t3.medium** | 2 | 4 GB | ~$30 | ✅ **RECOMMENDED** |
| t3.large | 2 | 8 GB | ~$60 | ✅ Best for production |

**To upgrade EC2:**
1. Go to AWS Console → EC2
2. Select your instance
3. Actions → Instance Settings → Change Instance Type
4. Select `t3.medium`
5. Start instance

---

### **🎯 Option 3: Add Swap Space (Temporary Solution)**

This uses disk as virtual memory (slower but works):

```bash
# SSH to EC2
ssh -i "your-key.pem" ec2-user@YOUR_EC2_IP

# Check current memory
free -h

# Create 2GB swap file
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# Make it permanent
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Verify
free -h
```

---

## 🚀 **Quick Fix Steps (Do This Now!):**

### **Step 1: Update docker-compose.yml on EC2**

```bash
# SSH to EC2
ssh -i "your-key.pem" ec2-user@YOUR_EC2_IP

# Go to directory
cd /home/ec2-user/armoire

# Backup current file
cp docker-compose.yml docker-compose.yml.backup

# Download updated file from your repo (after you push)
# OR manually edit:
nano docker-compose.yml
```

**Find this line (around line 11):**
```yaml
- "ES_JAVA_OPTS=-Xms512m -Xmx512m"
```

**Change to:**
```yaml
- "ES_JAVA_OPTS=-Xms256m -Xmx256m"
```

**Add after line 13 (after ports section):**
```yaml
    deploy:
      resources:
        limits:
          memory: 512m
        reservations:
          memory: 256m
```

**Find backend JAVA_OPTS (around line 89):**
```yaml
- JAVA_OPTS=-Xms512m -Xmx1024m
```

**Change to:**
```yaml
- JAVA_OPTS=-Xms256m -Xmx512m
```

**Add after that:**
```yaml
    deploy:
      resources:
        limits:
          memory: 1024m
        reservations:
          memory: 512m
```

Save: `Ctrl+X`, `Y`, `Enter`

### **Step 2: Add Swap Space (If Instance Has Less Than 4GB RAM)**

```bash
# Check memory
free -h

# If total is less than 4GB, add swap:
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Verify
free -h
# Should now show swap space
```

### **Step 3: Configure System for Elasticsearch**

```bash
# Set vm.max_map_count (required for Elasticsearch)
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

# Set memory overcommit (helps with OOM)
sudo sysctl -w vm.overcommit_memory=1
echo "vm.overcommit_memory=1" | sudo tee -a /etc/sysctl.conf
```

### **Step 4: Stop All Containers**

```bash
docker-compose down

# Clean up any orphaned containers
docker container prune -f
docker volume prune -f
```

### **Step 5: Start Services**

```bash
docker-compose up -d
```

### **Step 6: Monitor Startup**

```bash
# Watch logs
docker-compose logs -f

# In another terminal, check memory usage
watch -n 2 free -h

# Check container status
docker-compose ps
```

### **Step 7: Verify**

```bash
# Wait 60 seconds for Elasticsearch to start
sleep 60

# Check Elasticsearch
curl http://localhost:9600/_cluster/health

# Check backend
curl http://localhost:8080/actuator/health

# Check memory usage
docker stats --no-stream
```

---

## 🔍 **Troubleshooting:**

### **Problem: Elasticsearch still exits with 137**

**Check available memory:**
```bash
free -h
```

**If total + swap < 2GB:**
- Add more swap (see Option 3)
- OR upgrade instance (see Option 2)

**Check Docker logs:**
```bash
docker-compose logs elasticsearch | grep -i "memory\|oom\|killed"
```

### **Problem: Backend OOM**

**Reduce backend memory further:**
```yaml
JAVA_OPTS=-Xms128m -Xmx256m
```

### **Problem: System is slow**

This is expected with minimal memory. Solutions:
1. Upgrade to t3.medium (recommended)
2. Reduce memory further (affects performance)
3. Use RDS for database instead of self-hosted

---

## 📊 **Memory Usage Breakdown:**

### **With Original Settings (FAILS on small instances):**
```
Elasticsearch: 512 MB heap + 200 MB overhead = 712 MB
Backend:       1024 MB heap + 200 MB overhead = 1224 MB
System:        ~500 MB
Docker:        ~200 MB
Total:         ~2636 MB = 2.6 GB (needs t3.medium minimum)
```

### **With Reduced Settings (Works on t3.small with swap):**
```
Elasticsearch: 256 MB heap + 200 MB overhead = 456 MB
Backend:       512 MB heap + 200 MB overhead = 712 MB
System:        ~500 MB
Docker:        ~200 MB
Total:         ~1868 MB = 1.8 GB (works with 2GB + swap)
```

---

## ✅ **After Applying Fixes:**

### **Expected Output:**

```bash
$ docker-compose ps

NAME                STATUS
armoire-backend     Up (healthy)
elasticsearch       Up (healthy)
```

```bash
$ docker stats --no-stream

CONTAINER           CPU %     MEM USAGE / LIMIT
armoire-backend     2.5%      450MiB / 1GiB
elasticsearch       5.3%      380MiB / 512MiB
```

---

## 🎯 **Recommended Setup:**

### **For Development/Testing:**
- Instance: t3.small (2GB RAM)
- With swap: 2GB
- Elasticsearch heap: 256m
- Backend heap: 512m

### **For Production:**
- Instance: t3.medium (4GB RAM) or larger
- Elasticsearch heap: 512m - 1g
- Backend heap: 1g - 2g
- No swap needed

---

## 📋 **Complete Fix Checklist:**

- [ ] Check EC2 instance RAM: `free -h`
- [ ] Update docker-compose.yml with reduced memory
- [ ] Add swap space (if RAM < 4GB)
- [ ] Set vm.max_map_count=262144
- [ ] Set vm.overcommit_memory=1
- [ ] Stop all containers: `docker-compose down`
- [ ] Clean up: `docker system prune -af`
- [ ] Start services: `docker-compose up -d`
- [ ] Wait 60 seconds
- [ ] Verify both containers are up: `docker-compose ps`
- [ ] Test Elasticsearch: `curl http://localhost:9600/_cluster/health`
- [ ] Test backend: `curl http://localhost:8080/actuator/health`
- [ ] Check memory usage: `docker stats`
- [ ] Push updated docker-compose.yml
- [ ] Trigger GitHub Actions deployment

---

## 🚀 **Quick Command Summary:**

```bash
# 1. SSH to EC2
ssh -i "your-key.pem" ec2-user@YOUR_EC2_IP

# 2. Check memory
free -h

# 3. Add swap if needed (for instances with < 4GB RAM)
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 4. Configure system
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
sudo sysctl -w vm.overcommit_memory=1
echo "vm.overcommit_memory=1" | sudo tee -a /etc/sysctl.conf

# 5. Update docker-compose.yml (see Step 1 above)
cd /home/ec2-user/armoire
nano docker-compose.yml
# Make the changes described above

# 6. Restart
docker-compose down
docker-compose up -d

# 7. Wait and verify
sleep 60
docker-compose ps
curl http://localhost:9600/_cluster/health
curl http://localhost:8080/actuator/health

# 8. Exit
exit
```

---

## 📚 **Additional Resources:**

- **Docker Memory Management**: https://docs.docker.com/config/containers/resource_constraints/
- **Elasticsearch Memory Settings**: https://www.elastic.co/guide/en/elasticsearch/reference/current/heap-size.html
- **AWS EC2 Instance Types**: https://aws.amazon.com/ec2/instance-types/

---

**TL;DR: Your EC2 doesn't have enough RAM. I've reduced memory settings in docker-compose.yml. Push the changes, add swap space on EC2, and restart containers. Consider upgrading to t3.medium for production! 🚀**