# ScannerX Phone + Vercel + ngrok Deployment Guide

## Architecture

- Frontend: Vercel
- Backend: Spring Boot on NetHunter Rootless
- Database: local H2 file database using the `local` Spring profile
- Public backend URL: ngrok HTTPS tunnel
- Queue mode: `APP_QUEUE_MODE=local`

## 1. Push a clean repo

From your laptop:

```bash
cd /home/kali/Project/web_scanner_anti
git status --ignored
git add -A
git commit -m "Clean repo for NetHunter backend and Vercel frontend deployment"
git push origin main
```

## 2. Install backend dependencies on NetHunter Rootless

Inside your NetHunter Rootless shell:

```bash
sudo apt update
sudo apt install -y git curl wget unzip openjdk-21-jdk maven httpx whatweb nuclei ffuf nikto sqlmap zaproxy golang-go
go install github.com/hahwul/dalfox/v2@latest
echo 'export PATH="$PATH:$(go env GOPATH)/bin"' >> ~/.zshrc
source ~/.zshrc
```

Verify the required binaries:

```bash
which httpx whatweb nuclei ffuf nikto dalfox sqlmap zaproxy
java -version
mvn -version
```

## 3. Clone ScannerX on the phone

```bash
git clone https://github.com/udayXXkumar/ScannerX.git
cd ~/ScannerX/backend
```

## 4. Configure the phone backend

Create the phone env file:

```bash
cat > .env.phone <<'EOF'
SPRING_PROFILES_ACTIVE=local
APP_QUEUE_MODE=local
APP_JWT_SECRET=CHANGE_THIS_TO_A_LONG_RANDOM_SECRET
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://scannerx.vercel.app,https://*.vercel.app
AI_FINDING_ENRICHMENT_ENABLED=false
HF_API_TOKEN=
HF_MODEL_ID=Qwen/Qwen2.5-7B-Instruct
HF_PROVIDER=
EOF
```

Start the backend:

```bash
cd ~/ScannerX/backend
set -a
source .env.phone
set +a
./mvnw spring-boot:run
```

Verify health locally:

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

## 5. Install and run ngrok on the phone

```bash
cd ~
ARCH="$(dpkg --print-architecture)"
case "$ARCH" in
  arm64) NGROK_ZIP="ngrok-v3-stable-linux-arm64.zip" ;;
  armhf|armel) NGROK_ZIP="ngrok-v3-stable-linux-arm.zip" ;;
  amd64) NGROK_ZIP="ngrok-v3-stable-linux-amd64.zip" ;;
  *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac
wget "https://bin.equinox.io/c/bNyj1mQVY4c/${NGROK_ZIP}"
unzip -o "$NGROK_ZIP"
mkdir -p ~/.local/bin
install -m 755 ngrok ~/.local/bin/ngrok
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

Add your ngrok authtoken and expose the backend:

```bash
ngrok config add-authtoken <YOUR_NGROK_AUTHTOKEN>
ngrok http 8080
```

Copy the HTTPS forwarding URL and verify it:

```bash
curl -fsS https://your-ngrok-subdomain.ngrok-free.app/actuator/health
```

## 6. Point Vercel at the ngrok backend

From your laptop:

```bash
cd /home/kali/Project/web_scanner_anti/frontend
npx vercel@latest link
```

Remove old backend env vars:

```bash
cd /home/kali/Project/web_scanner_anti/frontend
npx vercel@latest env rm VITE_API_BASE_URL production
npx vercel@latest env rm VITE_API_BASE_URL preview
npx vercel@latest env rm VITE_WS_BASE_URL production
npx vercel@latest env rm VITE_WS_BASE_URL preview
```

Add the ngrok env vars:

```bash
cd /home/kali/Project/web_scanner_anti/frontend
npx vercel@latest env add VITE_API_BASE_URL production
npx vercel@latest env add VITE_API_BASE_URL preview
npx vercel@latest env add VITE_WS_BASE_URL production
npx vercel@latest env add VITE_WS_BASE_URL preview
```

Use:

```text
VITE_API_BASE_URL=https://your-ngrok-subdomain.ngrok-free.app/api
VITE_WS_BASE_URL=https://your-ngrok-subdomain.ngrok-free.app
```

Deploy the frontend:

```bash
cd /home/kali/Project/web_scanner_anti/frontend
npx vercel@latest --prod
```

## 7. Operational notes

- The backend uses the `local` profile, so data is stored only on the phone in `backend/data/`.
- Every time the free ngrok URL changes, update the Vercel env vars and redeploy.
- WebSocket traffic uses the same ngrok base URL as the API.
- If Hugging Face enrichment is needed later, add `HF_API_TOKEN` to `.env.phone` on the phone only.
