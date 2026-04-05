# Docker H2 Local Testing Setup - Complete

## ✅ Setup Complete

Everything is configured and ready to test. Here's what was created:

### Files Created

```
backend/
├── Dockerfile                    ✅ Multi-stage build for Spring Boot
└── docker-entrypoint.sh         ✅ Startup script with validation

frontend/
└── Dockerfile.dev               ✅ Development image with hot reload

root/
├── docker-compose.h2.yml        ✅ Local H2 testing orchestration
├── docker.h2.sh                 ✅ Helper script (all in one)
├── DOCKER_H2_TESTING_GUIDE.md   ✅ Comprehensive guide
├── DOCKER_H2_QUICK_REFERENCE.md ✅ Quick cheat sheet
└── [this file]                  ✅ Setup summary
```

---

## 🚀 Quick Start

### Option 1: Using the Helper Script (Recommended)

```bash
cd /home/kali/Project/web_scanner_anti

# Start everything
./docker.h2.sh start

# Wait ~15 seconds for services to boot
sleep 15

# Test the API
./docker.h2.sh test-api

# Open in browser:
# - Frontend: http://127.0.0.1:5173
# - Backend: http://127.0.0.1:8080
# - H2 Console: http://127.0.0.1:8080/h2-console
```

### Option 2: Using Docker Compose Directly

```bash
cd /home/kali/Project/web_scanner_anti

# Build images
docker-compose -f docker-compose.h2.yml build

# Start services
docker-compose -f docker-compose.h2.yml up -d

# View logs
docker-compose -f docker-compose.h2.yml logs -f backend
```

---

## 📋 What This Setup Includes

### Backend (Spring Boot in Docker)

✅ **Multi-stage Docker build**:
- Compiles Java code in Maven container
- Creates lightweight runtime image  
- Includes scanning tools: httpx, whatweb, nuclei, ffuf, nikto, sqlmap

✅ **Local H2 Profile**:
- File-based database at `./backend/data/scannerx`
- Queue mode: local (no RabbitMQ)
- Perfect replica of phone deployment

✅ **Health Checks**:
- Auto-restart if unhealthy
- API endpoint: http://127.0.0.1:8080/actuator/health

### Frontend (Vite Dev Server in Docker)

✅ **Development Image**:
- Hot module reload (change code → instant refresh)
- Volume-mounted source for real-time editing
- Connected to backend at http://127.0.0.1:8080/api

✅ **Environment Setup**:
- VITE_API_BASE_URL: http://127.0.0.1:8080/api
- VITE_WS_BASE_URL: http://127.0.0.1:8080

### Database (H2 File)

✅ **Persistent**:
- Files saved to disk in `./backend/data/`
- Survives container restarts
- Same DB structure as phone deployment

✅ **H2 Console**:
- GUI at http://127.0.0.1:8080/h2-console
- Query data directly
- Verify persistence

---

## 🧪 Testing Before Phone Deployment

### Recommended Test Flow

1. **Fresh User Registration**
   ```
   Register → Login → Dashboard loads ✓
   ```

2. **Target Creation**
   ```
   Add Target → Appears in list ✓
   ```

3. **Scan Launch** (Key test for auth fix)
   ```
   Click "Scan Now" → No false "Authentication required" ✓
   ```

4. **Session Persistence**
   ```
   Navigate around → Stay logged in ✓
   ```

5. **Database Persistence**
   ```
   Stop & restart → Data still there ✓
   ```

6. **H2 Console**
   ```
   View data directly → Confirms persistence ✓
   ```

---

## 📺 Available Endpoints

Once running, access:

| Service | URL | Purpose |
|---------|-----|---------|
| **Frontend** | http://127.0.0.1:5173 | Web UI |
| **Backend API** | http://127.0.0.1:8080 | REST API |
| **Health Check** | http://127.0.0.1:8080/actuator/health | Backend status |
| **H2 Console** | http://127.0.0.1:8080/h2-console | Database GUI |

---

## 🛠️ Helper Script Commands

```bash
./docker.h2.sh start           # Build and start everything
./docker.h2.sh stop            # Stop services
./docker.h2.sh restart         # Quick restart (no rebuild)
./docker.h2.sh logs            # View backend logs
./docker.h2.sh logs-frontend   # View frontend logs
./docker.h2.sh clean           # Clear database and stop
./docker.h2.sh status          # Show service status
./docker.h2.sh test-api        # Run health checks
./docker.h2.sh shell-backend   # Shell into backend container
./docker.h2.sh shell-frontend  # Shell into frontend container
```

---

## 🔄 Workflow: Test → Deploy to Phone

### Step 1: Test Locally (You are here)
```
✓ Verify auth/logout fixes work
✓ Test H2 file persistence
✓ Confirm UI functionality
```

### Step 2: Prepare Phone Deployment
```bash
# Once testing passes:
cd backend
cp .env.phone.example .env.phone
# Edit .env.phone and commit
git add -A && git commit -m "Ready for phone testing"
git push origin main
```

### Step 3: Phone Setup (Next)
```
# On phone (NetHunter):
git clone <your repo>
cd ScannerX/backend
source .env.phone
./mvnw spring-boot:run
```

### Step 4: ngrok Tunnel (Next)
```bash
# On phone:
ngrok http 8080
# Get HTTPS URL: https://xxx-xxx-xxx.ngrok-free.app
```

### Step 5: Vercel Deployment (Next)
```bash
# On laptop:
cd frontend
npx vercel@latest env add VITE_API_BASE_URL production
# Enter: https://xxx-xxx-xxx.ngrok-free.app/api
npx vercel@latest --prod
```

---

## 🐛 Troubleshooting

### Backend won't start
```bash
# Check if port 8080 is in use
sudo lsof -i :8080

# If needed, change port in docker-compose.h2.yml
# Then rebuild: ./docker.h2.sh clean && ./docker.h2.sh start
```

### Frontend can't reach backend
```bash
# Verify backend is healthy
./docker.h2.sh test-api

# Check logs
./docker.h2.sh logs

# Restart if needed
./docker.h2.sh restart
```

### H2 Console won't connect
```
It's normal if database doesn't exist yet.
Make a request to the API first (register/login).
Then retry H2 Console connection.
```

### Need to start fresh
```bash
./docker.h2.sh clean
./docker.h2.sh start
```

---

## 📝 Configuration

Key environment variables (in docker-compose.h2.yml):

```
SPRING_PROFILES_ACTIVE=local          # Use local H2 profile
APP_QUEUE_MODE=local                  # No RabbitMQ
APP_JWT_SECRET=<generated>            # Security key
AI_FINDING_ENRICHMENT_ENABLED=false   # Disable AI for testing
DB_USERNAME=sa                        # H2 default
DB_PASSWORD=                          # H2 default (empty)
```

All these will be the same on the phone!

---

## 💾 Database Persistence

Database files are stored here:
```
./backend/data/
├── scannerx.mv.db    ← Main data
├── scannerx.trace.db ← Error logs  
└── scannerx.lock.db  ← Lock file
```

These are **preserved** when you:
- Stop containers: `./docker.h2.sh stop`
- Restart containers: `./docker.h2.sh restart`

To reset:
```bash
./docker.h2.sh clean
# OR
rm -rf ./backend/data/
```

---

## ✅ Pre-Deployment Checklist

Before moving to phone:

- [ ] `./docker.h2.sh start` completes without errors
- [ ] Can register new user
- [ ] Can login successfully  
- [ ] Can create targets
- [ ] Can launch scans without false auth errors
- [ ] Session persists across page navigation
- [ ] Can view/query H2 database
- [ ] Can logout successfully
- [ ] Restart containers and data persists

---

## 🎯 Key Tests for Auth Fix

These verify the auth/logout stabilization:

1. **No false 401s**:
   ```
   POST /api/scans should return 200, not 401
   ```

2. **Single 401 doesn't logout**:
   ```
   If endpoint fails with 401, but /auth/me succeeds,
   user should stay logged in
   ```

3. **Session survives background failures**:
   ```
   Unread notifications request can fail without logout
   ```

4. **Only confirmed 401 triggers logout**:
   ```
   /auth/me must also return 401 for actual logout
   ```

All these are tested during normal usage! 🎉

---

## 📖 Documentation

- **Quick Start**: [DOCKER_H2_QUICK_REFERENCE.md](DOCKER_H2_QUICK_REFERENCE.md)
- **Full Guide**: [DOCKER_H2_TESTING_GUIDE.md](DOCKER_H2_TESTING_GUIDE.md)
- **Phone Deployment**: [DEPLOYMENT_PHONE_VERCEL_NGROK.md](DEPLOYMENT_PHONE_VERCEL_NGROK.md)

---

## ✨ You're Ready!

Everything is configured. Now:

```bash
cd /home/kali/Project/web_scanner_anti
./docker.h2.sh start
```

Then test as described in [DOCKER_H2_TESTING_GUIDE.md](DOCKER_H2_TESTING_GUIDE.md).

Good luck! 🚀
