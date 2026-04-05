# ScannerX H2 Local Testing - Quick Reference

## TL;DR - Get started in 3 commands

```bash
cd /home/kali/Project/web_scanner_anti

# 1. Start everything
./docker.h2.sh start

# 2. Test the API (optional)
./docker.h2.sh test-api

# 3. Open in browser
# Frontend: http://127.0.0.1:5173
# Backend: http://127.0.0.1:8080
# H2 Console: http://127.0.0.1:8080/h2-console
```

---

## Essential Commands

| Command | What it does |
|---------|-------------|
| `./docker.h2.sh start` | Build & start everything |
| `./docker.h2.sh stop` | Stop services gracefully |
| `./docker.h2.sh restart` | Quick restart (no rebuild) |
| `./docker.h2.sh logs` | View backend logs (stream) |
| `./docker.h2.sh clean` | ⚠️ Stop & delete database |
| `./docker.h2.sh test-api` | Health check |

---

## Key URLs

| Service | URL |
|---------|-----|
| Frontend | http://127.0.0.1:5173 |
| Backend API | http://127.0.0.1:8080 |
| Backend Health | http://127.0.0.1:8080/actuator/health |
| H2 Console | http://127.0.0.1:8080/h2-console |

---

## First Time Setup

1. **Start services**:
   ```bash
   ./docker.h2.sh start
   ```
   Wait 10-15 seconds for everything to boot.

2. **Register user**:
   - Go to http://127.0.0.1:5173
   - Click "Register"
   - Create test user

3. **Create target**:
   - Click "Add Target"
   - Enter target URL (e.g., https://httpbin.org)

4. **Launch scan**:
   - Click "Scan Now"
   - ✓ Should work without "Authentication required" error
   - ✓ Should redirect to scan detail
   - ✓ Should NOT auto-logout

---

## Testing the Auth Fix

The key thing to test before deploying:

1. **Scan Launch**:
   ```
   Open DevTools (F12)
   Click "Scan Now"
   Check Network tab:
     ✓ POST /api/scans should return 200
     ✓ No false 401 errors
   ```

2. **Session Persistence**:
   ```
   Login → Create Target → Launch Scan → Navigate around
   ✓ Should stay logged in throughout
   ✓ No random logouts
   ```

3. **Logout**:
   ```
   Click profile → Logout
   ✓ Should redirect to login
   ✓ Cannot access protected pages
   ```

---

## Database

**Location**: `./backend/data/`

**Files**:
- `scannerx.mv.db` - Main data file
- `scannerx.trace.db` - Error logs

**Reset**:
```bash
./docker.h2.sh clean
# OR
rm -rf ./backend/data/
```

**Access H2 Console**:
- URL: http://127.0.0.1:8080/h2-console
- Driver: `org.h2.Driver`
- URL: `jdbc:h2:file:./data/scannerx`
- User: `sa`
- Password: (empty)

---

## Troubleshooting

**Backend won't start**:
```bash
# Check what's using port 8080
sudo lsof -i :8080
# Stop it or restart docker
```

**Can't connect to API from frontend**:
```bash
# Verify backend is running
docker ps | grep backend

# Check logs
./docker.h2.sh logs

# Restart if needed
./docker.h2.sh restart
```

**Database locked**:
```bash
./docker.h2.sh stop
sleep 30
./docker.h2.sh start
```

**Full reset**:
```bash
./docker.h2.sh clean
./docker.h2.sh start
```

---

## What's Different from Phone Deployment

| Currently (Local) | Later (Phone) |
|------------------|--------------|
| Backend: Docker container | Backend: Java process on phone |
| Database: `./backend/data/` | Database: `~/ScannerX/backend/data/` |
| URL: http://127.0.0.1:8080 | URL: ngrok HTTPS tunnel |
| Frontend: Localhost | Frontend: Vercel |

**Profile is the same**: `local` with H2 file database

---

## Next Steps

1. ✅ Test everything locally with Docker
2. ✅ Verify auth/logout fixes work
3. → Clone to phone & set up Java/Maven
4. → Run backend directly on phone
5. → Set up ngrok tunnel
6. → Deploy frontend to Vercel

---

## Files Reference

| File | Purpose |
|------|---------|
| `docker-compose.h2.yml` | Local testing setup |
| `docker.h2.sh` | Helper script (execute this!) |
| `backend/Dockerfile` | Backend image |
| `backend/docker-entrypoint.sh` | Backend startup |
| `frontend/Dockerfile.dev` | Frontend dev image |
| `DOCKER_H2_TESTING_GUIDE.md` | Full guide (this file!) |

---

## Support

If stuck:
1. Read `DOCKER_H2_TESTING_GUIDE.md` troubleshooting section
2. Check logs: `./docker.h2.sh logs`
3. Test API: `./docker.h2.sh test-api`
4. Shell in: `./docker.h2.sh shell-backend`

---

**Ready? Start with**: `./docker.h2.sh start` 🚀
