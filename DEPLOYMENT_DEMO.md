# ScannerX Deployment Guide

## 1. Push the repo to GitHub

Create a new public repository:
- Owner: `udayXXkumar`
- Name: `ScannerX`
- Do not initialize it with a README, license, or `.gitignore`

Then run:

```bash
cd /home/kali/Project/web_scanner_anti
git init
git branch -M main
git add .
git status --ignored
git commit -m "Prepare ScannerX for Vercel and Render deployment"
git remote add origin https://github.com/udayXXkumar/ScannerX.git
git push -u origin main
```

## 2. Verify locally

Backend:

```bash
cd /home/kali/Project/web_scanner_anti/backend
./mvnw -q test
./mvnw -q -DskipTests compile
```

Frontend:

```bash
cd /home/kali/Project/web_scanner_anti/frontend
npm install
npm run lint
npm run build
```

Local Postgres stack:

```bash
cd /home/kali/Project/web_scanner_anti
docker compose -f docker-compose.dev.yml up -d
./start.sh
```

## 3. Deploy backend on Render

Use `render.yaml` with:
- one Postgres database: `scannerx-postgres`
- one web service: `scannerx-backend`

In Render:
1. `New` -> `Blueprint`
2. Connect `udayXXkumar/ScannerX`
3. Apply the blueprint

The backend container converts Render's `postgresql://...` connection string into the JDBC URL Spring Boot needs, so you do not need to set `SPRING_DATASOURCE_URL` manually.

Verify:

```bash
curl -fsS https://<your-render-service>.onrender.com/actuator/health
```

## 4. Deploy frontend on Vercel

```bash
cd /home/kali/Project/web_scanner_anti/frontend
npm i -g vercel
vercel login
vercel
```

Use:
- Framework preset: `Vite`
- Root directory: `frontend`
- Build command: `npm run build`
- Output directory: `dist`

Set frontend env vars:

```bash
cd /home/kali/Project/web_scanner_anti/frontend
vercel env add VITE_API_BASE_URL production
vercel env add VITE_API_BASE_URL preview
vercel env add VITE_WS_BASE_URL production
vercel env add VITE_WS_BASE_URL preview
```

Suggested values:
- `VITE_API_BASE_URL=https://<your-render-service>.onrender.com/api`
- `VITE_WS_BASE_URL=https://<your-render-service>.onrender.com`

Redeploy:

```bash
vercel --prod
```

## 5. End-to-end verification

After both sides are live:
- landing page loads
- register works
- login works
- dashboard loads
- target creation works
- `Scan Now` starts
- scan progress updates render live
- findings and reports load

For a live presentation:
- warm the Render service before the demo
- use a small target
- prefer `FAST` or `MEDIUM`
