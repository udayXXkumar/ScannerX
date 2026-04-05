# ScannerX AWS EC2 Deployment Guide

## Architecture

- Frontend: Vercel
- Backend: Dockerized Spring Boot app on one EC2 instance
- Scanner engine: same EC2 instance, inside the backend container
- Database: Postgres on the same EC2 instance
- HTTPS: Caddy using `<EC2_PUBLIC_IP>.sslip.io`
- Queue mode: `APP_QUEUE_MODE=local`

## 1. Launch the EC2 instance

In AWS Console:

1. Open EC2 in `ap-south-1`.
2. Click `Launch instance`.
3. Name: `scannerx-backend`.
4. AMI: `Ubuntu Server 24.04 LTS`.
5. Instance type: `t3.small`.
6. Create or select a key pair.
7. Network:
   - auto-assign public IP: `Enable`
   - security group:
     - `22/tcp` from `My IP`
     - `80/tcp` from `0.0.0.0/0` and `::/0`
     - `443/tcp` from `0.0.0.0/0` and `::/0`
8. Storage: `30 GB gp3`.
9. Launch.

Important:
- Do not open `5432`
- Do not open `8080`
- Do not open RabbitMQ ports

## 2. Allocate and attach an Elastic IP

In AWS Console:

1. EC2 -> `Elastic IPs`
2. `Allocate Elastic IP`
3. `Associate Elastic IP`
4. Attach it to `scannerx-backend`

Then define:

```bash
EC2_IP=<your_elastic_ip>
SCANNERX_HOST=<your_elastic_ip>.sslip.io
```

Example:

```bash
EC2_IP=13.234.56.78
SCANNERX_HOST=13.234.56.78.sslip.io
```

## 3. SSH into the instance

On your local machine:

```bash
chmod 400 ~/Downloads/<your-key>.pem
ssh -i ~/Downloads/<your-key>.pem ubuntu@<EC2_IP>
```

## 4. Install Docker and Git on EC2

Run on EC2:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg git

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo \"$VERSION_CODENAME\") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker $USER
newgrp docker

docker --version
docker compose version
git --version
```

## 5. Pull ScannerX

Run on EC2:

```bash
git clone https://github.com/udayXXkumar/ScannerX.git
cd ScannerX
```

## 6. Create the EC2 env file

Run on EC2:

```bash
cat > .env.ec2 <<'EOF'
SCANNERX_HOST=<EC2_IP>.sslip.io

POSTGRES_DB=scannerx
POSTGRES_USER=scannerx
POSTGRES_PASSWORD=CHANGE_THIS_TO_A_STRONG_PASSWORD

APP_JWT_SECRET=CHANGE_THIS_TO_A_LONG_RANDOM_SECRET_AT_LEAST_32_CHARS
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://*.vercel.app
EOF
```

If you already know your Vercel production domain, use:

```bash
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://<your-vercel-domain>,https://*.vercel.app
```

## 7. Start ScannerX on EC2

Run on EC2:

```bash
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 up -d --build
```

## 8. Verify backend health

Run on EC2:

```bash
curl -fsS https://<EC2_IP>.sslip.io/actuator/health
```

Expected output includes:

```json
{"status":"UP"}
```

## 9. Point Vercel frontend to the AWS backend

Run on your local machine:

```bash
cd /home/kali/Project/web_scanner_anti/frontend
npx vercel@latest env add VITE_API_BASE_URL production
npx vercel@latest env add VITE_API_BASE_URL preview
npx vercel@latest env add VITE_WS_BASE_URL production
npx vercel@latest env add VITE_WS_BASE_URL preview
```

Use:

```bash
VITE_API_BASE_URL=https://<EC2_IP>.sslip.io/api
VITE_WS_BASE_URL=https://<EC2_IP>.sslip.io
```

Then redeploy:

```bash
npx vercel@latest --prod
```

## 10. Useful EC2 operations

Run on EC2:

```bash
cd ~/ScannerX
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 ps
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 logs -f backend
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 logs -f caddy
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 logs -f db
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 restart backend
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 pull
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 up -d --build
```

## 11. End-to-end verification

Check:

- `https://<EC2_IP>.sslip.io/actuator/health` returns `UP`
- Vercel frontend loads
- register/login works
- no mixed-content or websocket-origin errors
- target creation works
- `Scan Now` starts
- progress updates appear
- findings and reports load

## 12. Reboot resilience

On EC2:

```bash
sudo reboot
```

After reconnecting:

```bash
cd ~/ScannerX
docker compose -f docker-compose.ec2.yml --env-file .env.ec2 ps
curl -fsS https://<EC2_IP>.sslip.io/actuator/health
```
