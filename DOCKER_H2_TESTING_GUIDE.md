# ScannerX Local H2 Docker Testing Guide

## Overview

This guide helps you test the phone deployment setup locally using Docker with an H2 file-based database. This simulates exactly what will run on your NetHunter phone before deploying there.

**Why test locally first?**
- Verify the `local` profile configuration works correctly
- Test H2 file database persistence
- Catch issues before deploying to the phone
- Easier debugging with full logs and inspections
- Validate the auth/logout fixes you just implemented

---

## Quick Start

### Prerequisites

- Docker & Docker Compose installed
- `docker` and `docker-compose` commands available
- At least 2GB free disk space
- Ports 8080 (backend), 5173 (frontend) available

### Start in 30 Seconds

```bash
cd /home/kali/Project/web_scanner_anti

# Start the entire stack
./docker.h2.sh start

# Give it a moment to boot up...
sleep 10

# Test the API
./docker.h2.sh test-api
```

**Then open in browser:**
- Frontend: http://127.0.0.1:5173
- Backend API: http://127.0.0.1:8080
- H2 Console: http://127.0.0.1:8080/h2-console

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│         Docker Compose (docker-compose.h2.yml) │
├─────────────────┬───────────────────────────────┤
│   Backend       │   Frontend                    │
│   (Port 8080)   │   (Port 5173)                 │
│                 │                               │
│ Spring Boot:    │ Vite Dev Server:              │
│ - local profile │ - Hot reload enabled          │
│ - H2 file DB    │ - React development           │
│ - Queue: local  │ - Uses backend at :8080       │
│                 │                               │
│ Volume:         │ Volume:                       │
│ ./backend/data/ │ ./frontend/src/ (hot reload)  │
└─────────────────┴───────────────────────────────┘
```

---

## Commands Reference

### Basic Operations

**Start services:**
```bash
./docker.h2.sh start
```
Builds Docker images and starts both backend and frontend.

**Stop services:**
```bash
./docker.h2.sh stop
```
Gracefully stops all containers.

**Restart services:**
```bash
./docker.h2.sh restart
```
Quick restart without rebuilding images.

**View status:**
```bash
./docker.h2.sh status
```
Shows if services are running.

**Clean everything:**
```bash
./docker.h2.sh clean
```
⚠️ Stops services and **deletes the H2 database**. Use to start fresh.

---

### Debugging

**View backend logs (follow):**
```bash
./docker.h2.sh logs
# Press Ctrl+C to exit
```

**View frontend logs:**
```bash
./docker.h2.sh logs-frontend
```

**Shell into backend container:**
```bash
./docker.h2.sh shell-backend
# Exit with: exit
```

**Shell into frontend container:**
```bash
./docker.h2.sh shell-frontend
```

**Test API health:**
```bash
./docker.h2.sh test-api
```

---

## Before Deploying to Phone

### 1. Test Fresh User Registration

```bash
# Open http://127.0.0.1:5173 in browser
# Click "Register"
# Create new user with:
#   Email: test@example.com
#   Password: TestPassword123!
#   Full Name: Test User
# 
# Expected: Should see success message, redirect to login
```

### 2. Test Login

```bash
# Click "Login"
# Enter credentials
# Expected: Dashboard loads, authenticated
```

### 3. Test Target Creation

```bash
# Click "Add Target"
# Fill in:
#   Name: Test Target
#   URL: https://httpbin.org (or safe test URL)
#   Tier: MEDIUM
#   Timeouts: Enabled
# 
# Expected: Target appears in list with status "READY"
```

### 4. Test Scan Launch (Key Test for Auth Fix)

```bash
# Click "Scan Now" on target
# 
# Expected:
#   ✓ Request shows 200 response
#   ✓ NO "Authentication required" error
#   ✓ NO unexpected logout
#   ✓ Redirects to scan detail page
```

### 5. Test H2 Database

```bash
# Visit: http://127.0.0.1:8080/h2-console
# 
# Connection details:
#   Driver: org.h2.Driver
#   URL: jdbc:h2:file:./data/scannerx
#   Username: sa
#   Password: (leave empty)
#
# Click "Test Connection" then "Connect"
# 
# Run query: SELECT COUNT(*) FROM "user";
# Expected: Should see your test user
```

### 6. Test Database Persistence

```bash
# Create another user/target
# Stop services: ./docker.h2.sh stop
# Start again: ./docker.h2.sh start
# 
# Expected: Previous data should still exist
# (proves H2 file persistence is working)
```

### 7. Test Auth/Logout Fixes

```bash
# Test Session Stability:
# 1. Login with your test user
# 2. Create a target
# 3. Launch scan
# 4. Navigate between pages (targets, scans, findings, etc.)
# 
# Expected:
#   ✓ Stay authenticated across navigation
#   ✓ No false "Authentication required" messages
#   ✓ Unread notification polling doesn't cause logout
#   ✓ Session persists for 24 hours
```

### 8. Test Logout

```bash
# Click "Logout" in profile menu
# 
# Expected:
#   ✓ Redirects to login page
#   ✓ Cannot access protected routes
#   ✓ Database has user but they're logged out
```

---

## Database Location

The H2 database files are stored here:

```
./backend/data/
├── scannerx.mv.db    (main data file)
├── scannerx.trace.db (trace/error log)
└── scannerx.lock.db  (lock file)
```

These are **persisted** between container restarts. To reset:

```bash
./docker.h2.sh clean
# OR manually:
rm -rf ./backend/data/
```

---

## Common Issues & Troubleshooting

### Issue: Backend won't start

**Error**: `ERROR: for scannerx-backend-h2: Driver failed programming external connectivity`

**Solution**:
```bash
# Port 8080 is in use
sudo lsof -i :8080
# Kill the process or change port in docker-compose.h2.yml
```

### Issue: Frontend shows "Cannot connect to API"

**Symptoms**: Network errors in browser console, can't login

**Solution**:
```bash
# Check backend is running
docker ps | grep scannerx

# If not running, check logs
./docker.h2.sh logs

# Verify backend health
curl http://127.0.0.1:8080/actuator/health

# Try restarting
./docker.h2.sh restart
```

### Issue: H2 Console won't connect

**Error**: `Database "./data/scannerx" not found`

**Solution**:
```bash
# The database hasn't been created yet - this is ok
# Start the backend and make a request, databases will be created
# Then try H2 Console again
```

### Issue: Database is locked

**Error**: `General error: "java.nio.file.AccessDeniedException"`

**Solution**:
```bash
# Backend is still using the database
./docker.h2.sh stop

# Wait 30 seconds
sleep 30

# Start again
./docker.h2.sh start
```

### Issue: Out of memory errors

**Error**: `java.lang.OutOfMemoryError`

**Solution**:
```bash
# Increase heap in docker-compose.h2.yml
# Find: java -Xmx2g -jar
# Change to: java -Xmx4g -jar (for 4GB)
# 
# Then rebuild:
./docker.h2.sh clean
./docker.h2.sh start
```

---

## Comparing with Phone Deployment

| Aspect | Local Docker | Phone Deployment |
|--------|-------------|-----------------|
| Backend | Spring Boot in Docker | Spring Boot on phone |
| Database | H2 file: `./backend/data/` | H2 file: `~/ScannerX/backend/data/` |
| Profile | `local` | `local` |
| Queue Mode | `local` | `local` |
| Public URL | http://127.0.0.1:8080 | ngrok HTTPS URL |
| Frontend | Vite dev server | Vercel (connected via env vars) |

**Key difference**: Next step is replacing http://127.0.0.1:8080 with the ngrok HTTPS URL.

---

## Next Steps After Testing

If everything works locally:

### 1. Export Environment

Save your test environment setup:

```bash
cat > backend/.env.phone <<'EOF'
SPRING_PROFILES_ACTIVE=local
APP_QUEUE_MODE=local
APP_JWT_SECRET=$(openssl rand -hex 32)
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://scannerx.vercel.app,https://*.vercel.app
AI_FINDING_ENRICHMENT_ENABLED=false
EOF
```

### 2. Copy to Phone

```bash
# On your phone/NetHunter
git clone https://github.com/yourusername/ScannerX.git
cd ScannerX/backend
cp .env.phone.example .env.phone
# Edit .env.phone with your values
```

### 3. Set up ngrok

See [DEPLOYMENT_PHONE_VERCEL_NGROK.md](DEPLOYMENT_PHONE_VERCEL_NGROK.md) for phone setup.

### 4. Deploy to Vercel

```bash
# From your laptop after confirming ngrok URL
cd frontend
npx vercel@latest env add VITE_API_BASE_URL production
# Enter: https://your-ngrok-domain.ngrok-free.app/api

npx vercel@latest env add VITE_WS_BASE_URL production  
# Enter: https://your-ngrok-domain.ngrok-free.app

npx vercel@latest --prod
```

---

## Cleanup

When done testing:

```bash
# Stop services
./docker.h2.sh stop

# Remove images (optional, saves space)
docker-compose -f docker-compose.h2.yml down --rmi local

# Full cleanup including data
./docker.h2.sh clean
```

---

## Getting Help

If something doesn't work:

1. **Check logs**:
   ```bash
   ./docker.h2.sh logs
   ```

2. **Check API health**:
   ```bash
   ./docker.h2.sh test-api
   ```

3. **Shell into container**:
   ```bash
   ./docker.h2.sh shell-backend
   # Check H2 database:
   ls -la data/
   ```

4. **Review troubleshooting** section above

5. **Rebuild everything**:
   ```bash
   ./docker.h2.sh clean
   ./docker.h2.sh start
   ```

---

## Performance

Typical startup times:
- Backend image build: 2-3 minutes (first time only)
- Backend startup: 10-15 seconds  
- Frontend startup: 5-10 seconds
- Total: 3-5 minutes for full stack (including build)

On subsequent runs (no rebuild): ~20 seconds total.

---

## Environment Variables Reference

Located in `docker-compose.h2.yml`:

| Variable | Value | Purpose |
|----------|-------|---------|
| `SPRING_PROFILES_ACTIVE` | `local` | Uses local H2 profile |
| `APP_QUEUE_MODE` | `local` | No RabbitMQ needed |
| `APP_JWT_SECRET` | Random | JWT signing (change in production) |
| `AI_FINDING_ENRICHMENT_ENABLED` | `false` | Disable AI for testing |
| `DB_USERNAME` | `sa` | H2 default user |
| `DB_PASSWORD` | (empty) | H2 default (no password) |

---

## Files Created

```
webscanner_anti/
├── docker-compose.h2.yml       ← Local testing compose file
├── docker.h2.sh               ← Helper script
├── backend/
│   ├── Dockerfile             ← Backend image definition
│   ├── docker-entrypoint.sh   ← Startup script
│   └── data/                  ← H2 database (created on first run)
├── frontend/
│   └── Dockerfile.dev         ← Frontend dev image
└── [this file]                ← Testing guide
```

---

Good luck with your testing! This is a crucial step before deploying to the phone. 🚀
