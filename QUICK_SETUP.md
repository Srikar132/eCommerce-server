# 🎯 QUICK FIX GUIDE - 3 Critical Issues

## 🚨 You Have 3 Issues - Here's How to Fix Them

---

## ❌ Issue 1: Missing `ELASTICSEARCH_URIS` in EC2 `.env` File

### **The Problem:**
Your backend expects this variable but it's not in your `.env` file!

### **The Fix:**
```bash
# SSH to EC2
ssh -i "your-key.pem" ec2-user@YOUR_EC2_IP

# Edit .env
cd /home/ec2-user/armoire
nano .env

# Add this line at the end:
ELASTICSEARCH_URIS=http://elasticsearch:9200

# Save: Ctrl+X, then Y, then Enter
```

---

## ❌ Issue 2: Elasticsearch URL Confusion

### **The Problem:**
Your docker-compose.yml maps port `9600:9200`, causing confusion about which URL to use.

### **The Solution:**

**✅ CORRECT URLs:**

| Where | URL | Port |
|-------|-----|------|
| In `.env` file | `http://elasticsearch:9200` | 9200 (internal) |
| From EC2 terminal | `http://localhost:9600` | 9600 (mapped) |
| From your browser | `http://EC2_IP:9600` | 9600 (external) |

**Remember:**
- `.env` file ALWAYS uses: `http://elasticsearch:9200` (Docker internal network)
- Testing from EC2 uses: `http://localhost:9600` (mapped port)

---

## ❌ Issue 3: GitHub Actions Health Check Failing

### **The Problem:**
The health check in `maven.yml` was too aggressive and didn't handle errors properly.

### **The Fix:**
I've already updated `.github/workflows/maven.yml` with:
- ✅ Longer wait time (30 seconds initial)
- ✅ More retries (15 instead of 10)
- ✅ Better error handling
- ✅ Shows backend logs if it fails

---

## 📋 COMPLETE FIX STEPS (Do This Now!)

### Step 1: Update `.env` on EC2 ⚠️ IMPORTANT

```bash
ssh -i "your-key.pem" ec2-user@YOUR_EC2_IP
cd /home/ec2-user/armoire
nano .env
```

**Your COMPLETE `.env` file should be:**

```env
DATABASE_URL=jdbc:postgresql://ep-mute-meadow-a1um972m-pooler.ap-southeast-1.aws.neon.tech/neondb?user=neondb_owner&password=npg_Q5fnBeSO7Isj&sslmode=require&channelBinding=require
DATABASE_USERNAME=neondb_owner
DATABASE_PASSWORD=npg_Q5fnBeSO7Isj
ELASTICSEARCH_URIS=http://elasticsearch:9200
JWT_SECRET=THISISMYSECURESECRETKEYFORJWTECOMMERCEBACKEND123456
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000
JWT_COOKIE_SECURE=false
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=bunnyking828@gmail.com
MAIL_PASSWORD=rwmc rraa adwm ukcq
AWS_ACCESS_KEY=AKIASOLKTCZLEWGLZYRX
AWS_SECRET_KEY=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
AWS_REGION=eu-north-1
AWS_S3_BUCKET=nala-armorie-storage
AWS_CLOUDFRONT_DOMAIN=
FRONTEND_URL=http://localhost:3000
RAZORPAY_KEY_ID=rzp_test_S4z4JEgdwEl5wN
RAZORPAY_KEY_SECRET=q23JmY78mvGAV35LSLs2yxIR
ADMIN_EMAIL=bunnyking828@gmail.com
```

**Key: Look for the line `ELASTICSEARCH_URIS=http://elasticsearch:9200` - make sure it's there!**

Save: `Ctrl+X`, then `Y`, then `Enter`

### Step 2: Restart Containers

```bash
docker-compose down
docker-compose up -d
```

Wait 30 seconds, then:

```bash
docker-compose ps
```

Both containers should show **"Up (healthy)"**

### Step 3: Test Endpoints

```bash
# Test backend health
curl http://localhost:8080/actuator/health

# Expected: {"status":"UP"}

# Test Elasticsearch (use mapped port 9600)
curl http://localhost:9600/_cluster/health

# Expected: {"cluster_name":"docker-cluster","status":"green",...}
```

### Step 4: Check Logs

```bash
# Check backend logs
docker-compose logs backend | grep -i elastic

# Should NOT see connection errors
# Should see something like "Connected to Elasticsearch"
```

### Step 5: Update Local .env (Optional but Recommended)

On your local machine:
```bash
cd E:\Websites\eCommerce\armoire
```

Edit `.env` and add:
```env
ELASTICSEARCH_URIS=http://elasticsearch:9200
```

### Step 6: Commit and Push

```bash
git add .
git commit -m "Fix: Add ELASTICSEARCH_URIS and improve health check"
git push origin main
```

Watch the deployment at: https://github.com/Srikar132/eCommerce-server/actions

---

## ✅ Success Checklist

After following the steps above, verify:

- [ ] `docker-compose ps` shows both containers healthy
- [ ] `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`
- [ ] `curl http://localhost:9600/_cluster/health` returns cluster info
- [ ] Backend logs show no Elasticsearch errors
- [ ] GitHub Actions deployment passes
- [ ] Health check in GitHub Actions succeeds

---

## 🆘 Still Having Issues?

### Backend won't start:
```bash
docker-compose logs backend --tail=50
```
Look for error messages about missing variables or connection failures.

### Elasticsearch connection fails:
```bash
# Verify the environment variable is set
docker exec armoire-backend env | grep ELASTICSEARCH

# Should show: ELASTICSEARCH_URIS=http://elasticsearch:9200
```

### Health check still fails:
```bash
# Check what port your backend is actually running on
docker-compose ps

# Make sure it shows 8080:8080
```

---

## 📚 For More Details:

- **Elasticsearch URL Explanation**: `.github/ELASTICSEARCH_URL_GUIDE.md`
- **Full Issue Details**: `.github/ISSUES_FIXED.md`
- **Production .env Template**: `.github/.env.production`

---

**TL;DR: Add `ELASTICSEARCH_URIS=http://elasticsearch:9200` to your EC2 `.env` file, restart containers, and push your code! 🚀**# 🎯 QUICK FIX GUIDE - 3 Critical Issues

## 🚨 You Have 3 Issues - Here's How to Fix Them

---

## ❌ Issue 1: Missing `ELASTICSEARCH_URIS` in EC2 `.env` File

### **The Problem:**
Your backend expects this variable but it's not in your `.env` file!

### **The Fix:**
```bash
# SSH to EC2
ssh -i "your-key.pem" ec2-user@YOUR_EC2_IP

# Edit .env
cd /home/ec2-user/armoire
nano .env

# Add this line at the end:
ELASTICSEARCH_URIS=http://elasticsearch:9200

# Save: Ctrl+X, then Y, then Enter
```

---

## ❌ Issue 2: Elasticsearch URL Confusion

### **The Problem:**
Your docker-compose.yml maps port `9600:9200`, causing confusion about which URL to use.

### **The Solution:**

**✅ CORRECT URLs:**

| Where | URL | Port |
|-------|-----|------|
| In `.env` file | `http://elasticsearch:9200` | 9200 (internal) |
| From EC2 terminal | `http://localhost:9600` | 9600 (mapped) |
| From your browser | `http://EC2_IP:9600` | 9600 (external) |

**Remember:**
- `.env` file ALWAYS uses: `http://elasticsearch:9200` (Docker internal network)
- Testing from EC2 uses: `http://localhost:9600` (mapped port)

---

## ❌ Issue 3: GitHub Actions Health Check Failing

### **The Problem:**
The health check in `maven.yml` was too aggressive and didn't handle errors properly.

### **The Fix:**
I've already updated `.github/workflows/maven.yml` with:
- ✅ Longer wait time (30 seconds initial)
- ✅ More retries (15 instead of 10)
- ✅ Better error handling
- ✅ Shows backend logs if it fails

---

## 📋 COMPLETE FIX STEPS (Do This Now!)

### Step 1: Update `.env` on EC2 ⚠️ IMPORTANT

```bash
ssh -i "your-key.pem" ec2-user@YOUR_EC2_IP
cd /home/ec2-user/armoire
nano .env
```

**Your COMPLETE `.env` file should be:**

```env
DATABASE_URL=jdbc:postgresql://ep-mute-meadow-a1um972m-pooler.ap-southeast-1.aws.neon.tech/neondb?user=neondb_owner&password=npg_Q5fnBeSO7Isj&sslmode=require&channelBinding=require
DATABASE_USERNAME=neondb_owner
DATABASE_PASSWORD=npg_Q5fnBeSO7Isj
ELASTICSEARCH_URIS=http://elasticsearch:9200
JWT_SECRET=THISISMYSECURESECRETKEYFORJWTECOMMERCEBACKEND123456
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000
JWT_COOKIE_SECURE=false
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=bunnyking828@gmail.com
MAIL_PASSWORD=rwmc rraa adwm ukcq
AWS_ACCESS_KEY=AKIASOLKTCZLEWGLZYRX
AWS_SECRET_KEY=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
AWS_REGION=eu-north-1
AWS_S3_BUCKET=nala-armorie-storage
AWS_CLOUDFRONT_DOMAIN=
FRONTEND_URL=http://localhost:3000
RAZORPAY_KEY_ID=rzp_test_S4z4JEgdwEl5wN
RAZORPAY_KEY_SECRET=q23JmY78mvGAV35LSLs2yxIR
ADMIN_EMAIL=bunnyking828@gmail.com
```

**Key: Look for the line `ELASTICSEARCH_URIS=http://elasticsearch:9200` - make sure it's there!**

Save: `Ctrl+X`, then `Y`, then `Enter`

### Step 2: Restart Containers

```bash
docker-compose down
docker-compose up -d
```

Wait 30 seconds, then:

```bash
docker-compose ps
```

Both containers should show **"Up (healthy)"**

### Step 3: Test Endpoints

```bash
# Test backend health
curl http://localhost:8080/actuator/health

# Expected: {"status":"UP"}

# Test Elasticsearch (use mapped port 9600)
curl http://localhost:9600/_cluster/health

# Expected: {"cluster_name":"docker-cluster","status":"green",...}
```

### Step 4: Check Logs

```bash
# Check backend logs
docker-compose logs backend | grep -i elastic

# Should NOT see connection errors
# Should see something like "Connected to Elasticsearch"
```

### Step 5: Update Local .env (Optional but Recommended)

On your local machine:
```bash
cd E:\Websites\eCommerce\armoire
```

Edit `.env` and add:
```env
ELASTICSEARCH_URIS=http://elasticsearch:9200
```

### Step 6: Commit and Push

```bash
git add .
git commit -m "Fix: Add ELASTICSEARCH_URIS and improve health check"
git push origin main
```

Watch the deployment at: https://github.com/Srikar132/eCommerce-server/actions

---

## ✅ Success Checklist

After following the steps above, verify:

- [ ] `docker-compose ps` shows both containers healthy
- [ ] `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`
- [ ] `curl http://localhost:9600/_cluster/health` returns cluster info
- [ ] Backend logs show no Elasticsearch errors
- [ ] GitHub Actions deployment passes
- [ ] Health check in GitHub Actions succeeds

---

## 🆘 Still Having Issues?

### Backend won't start:
```bash
docker-compose logs backend --tail=50
```
Look for error messages about missing variables or connection failures.

### Elasticsearch connection fails:
```bash
# Verify the environment variable is set
docker exec armoire-backend env | grep ELASTICSEARCH

# Should show: ELASTICSEARCH_URIS=http://elasticsearch:9200
```

### Health check still fails:
```bash
# Check what port your backend is actually running on
docker-compose ps

# Make sure it shows 8080:8080
```

---

## 📚 For More Details:

- **Elasticsearch URL Explanation**: `.github/ELASTICSEARCH_URL_GUIDE.md`
- **Full Issue Details**: `.github/ISSUES_FIXED.md`
- **Production .env Template**: `.github/.env.production`

---

**TL;DR: Add `ELASTICSEARCH_URIS=http://elasticsearch:9200` to your EC2 `.env` file, restart containers, and push your code! 🚀**