# ScannerX

[![Live Frontend](https://img.shields.io/badge/Live%20Frontend-scannerx.vercel.app-111827?logo=vercel)](https://scannerx.vercel.app)
![Frontend](https://img.shields.io/badge/Frontend-React%2019%20%2B%20Vite-61DAFB?logo=react&logoColor=white)
![Backend](https://img.shields.io/badge/Backend-Spring%20Boot%204-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-E76F00?logo=openjdk&logoColor=white)
![Database](https://img.shields.io/badge/Runtime-H2%20%7C%20Postgres%20%7C%20MySQL-334155)
![Realtime](https://img.shields.io/badge/Realtime-WebSocket%20%2B%20STOMP-0F172A)

ScannerX is a modern web security scanning workspace for managing targets, launching tiered scans, monitoring live activity, triaging findings, comparing runs, and exporting reports from a single interface. The project combines a React/Vite frontend with a Spring Boot backend, live WebSocket updates, a host-based scanner toolchain, and optional AI-assisted finding enrichment.

**Visit :** [https://scannerx.vercel.app](https://scannerx.vercel.app)

> The public frontend is stable. Authenticated features and scan execution depend on backend availability, which may vary because the backend can be hosted separately from the Vercel deployment.

## Why ScannerX

- Centralizes web security operations into one workspace: targets, scans, findings, reports, schedules, notifications, and profile management.
- Supports tier-based scan depth with **Fast**, **Medium**, and **Deep** scan paths.
- Streams real-time scan progress and activity into the UI through WebSockets.
- Preserves findings, scan history, and exports instead of treating scans as throwaway terminal runs.
- Adds optional AI-assisted finding descriptions and exploit context without overwriting the raw scanner output.

## Core Capabilities

| Area | What ScannerX provides |
| --- | --- |
| Target management | Create, edit, organize, and launch scans directly against tracked targets. |
| Scan operations | Queue, monitor, pause, resume, cancel, compare, and review scan jobs with live updates. |
| Findings workflow | Filter and triage findings by severity, status, and target with persistent detail views. |
| Reporting | Generate normalized reports and view completed-run summaries per target. |
| Scheduling | Configure recurring scans tied to targets. |
| Notifications | Track scan events and workflow state changes from the app shell. |
| AI assist | Enrich persisted findings with clearer descriptions and defender-safe exploit context when Hugging Face is configured. |

## Workspace Modules

| Module | Purpose |
| --- | --- |
| Dashboard | Threat overview, live summary cards, and recent findings context. |
| Targets | Launch scans, manage scan depth defaults, and track per-target status. |
| Scan Jobs | Review queued, running, completed, failed, and historical runs. |
| Scan Detail | Watch stage activity, progress, and report data for a specific scan. |
| Findings | Explore historical and live findings across targets. |
| Reports | View completed-run summaries and export-oriented reporting data. |
| Schedules | Manage recurring scan jobs. |
| Notifications | Review unread and historical operational events. |
| Profile | Update account information and session-level user settings. |
| Admin | Role-gated administration surface for privileged users. |

## Architecture Overview

### Application layout

- **Frontend:** React 19, Vite, React Router, TanStack Query, SockJS/STOMP, Tailwind-based UI
- **Backend:** Spring Boot 4, Java 21, Spring Security, JWT auth, Spring WebSocket, JPA, Flyway
- **Local runtime:** H2 file database by default through the `local` Spring profile
- **Optional profiles:** Postgres and MySQL profile support for non-local deployments

### Runtime flow

1. Users authenticate through the Spring Boot API using JWT-backed sessions.
2. Targets and scan jobs are managed through REST endpoints.
3. Scan stages execute through host-available tools and publish activity events.
4. The frontend consumes REST data with React Query and live updates over WebSockets.
5. Reports and findings remain available after scan completion for comparison and export.

### Scanner/toolchain surface

ScannerX is designed to work with a host-installed toolchain. Full scan depth depends on tool availability on the runtime host.

- `httpx` / `httpx-toolkit`
- `WhatWeb`
- `Nuclei`
- `FFUF`
- `Nikto`
- `SQLMap`
- `OWASP ZAP`
- `Dalfox`

The app can still boot without every scanner installed, but deeper scan tiers require the relevant host tools to be available.

## Tech Stack

| Layer | Technologies |
| --- | --- |
| Frontend | React 19, Vite, Axios, TanStack Query, React Router, SockJS, STOMP, Recharts |
| Backend | Spring Boot 4, Spring Security, Spring WebSocket, Spring Data JPA, Flyway, Actuator |
| Auth | JWT |
| Data | H2 (default local), PostgreSQL, MySQL |
| Realtime | WebSocket + STOMP topics |
| AI | Hugging Face hosted inference for finding enrichment |
| Tooling | Maven Wrapper, npm, ESLint |

## Local Quickstart

### Prerequisites

- Java 21
- Node.js 20+ and npm
- Bash
- Optional but recommended for real scan depth: `httpx`/`httpx-toolkit`, WhatWeb, Nuclei, FFUF, Nikto, SQLMap, ZAP, Dalfox

### First run

```bash
git clone https://github.com/udayXXkumar/ScannerX.git
cd ScannerX

cd frontend
npm install
cd ..

./start.sh
```

### What the default local workflow does

- Starts the backend with `SPRING_PROFILES_ACTIVE=local`
- Uses an H2 file database at `backend/data/scannerx.mv.db`
- Starts the frontend on Vite
- Wires the frontend to the backend automatically through local environment variables

### Default local URLs

- Frontend: `http://localhost:5173`
- Backend: `http://127.0.0.1:8080`
- H2 Console: `http://127.0.0.1:8080/h2-console`

### Stop the local stack

```bash
./stop.sh
```

### Logs

```bash
tail -f .run/backend.log
tail -f .run/frontend.log
```

## Manual Development Setup

Use this path if you want to run the frontend and backend separately instead of using `./start.sh`.

### Backend

```bash
cd backend
SPRING_PROFILES_ACTIVE=local APP_QUEUE_MODE=local ./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
VITE_API_BASE_URL=http://localhost:8080/api \
VITE_WS_BASE_URL=http://localhost:8080 \
npm run dev
```

## Environment Configuration

### Frontend

| Variable | Required | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Yes for non-local deploys | Base URL for REST API requests |
| `VITE_WS_BASE_URL` | Yes for non-local deploys | Base URL used to build the SockJS/STOMP endpoint |

Example:

```bash
VITE_API_BASE_URL=https://your-backend.example.com/api
VITE_WS_BASE_URL=https://your-backend.example.com
```

### Backend

| Variable | Required | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes for explicit runtime selection | Chooses `local`, `postgres`, or `mysql` |
| `APP_QUEUE_MODE` | Recommended | Uses `local` queue mode for local and phone-hosted setups |
| `APP_JWT_SECRET` | Yes outside local testing | JWT signing secret |
| `APP_CORS_ALLOWED_ORIGINS` | Optional | Explicit comma-separated CORS origin allowlist |
| `APP_CORS_ALLOWED_ORIGIN_PATTERNS` | Recommended for hosted frontends | Pattern-based CORS allowlist for Vercel or custom hosted UIs |
| `AI_FINDING_ENRICHMENT_ENABLED` | Optional | Enables Hugging Face-based finding enrichment |
| `HF_API_TOKEN` | Required when AI enrichment is enabled | Hugging Face token used by the backend |
| `HF_MODEL_ID` | Optional | Hugging Face model ID, default `Qwen/Qwen2.5-7B-Instruct` |
| `HF_PROVIDER` | Optional | Hosted inference provider override when needed |
| `AI_ENRICHMENT_TIMEOUT_MS` | Optional | Timeout for AI enrichment requests |
| `AI_ENRICHMENT_MAX_RETRIES` | Optional | Retry count for enrichment attempts |
| `AI_ENRICHMENT_MAX_INPUT_CHARS` | Optional | Input size guard before prompt generation |
| `AI_ENRICHMENT_CONCURRENCY` | Optional | Parallel enrichment worker count |

For phone-hosted or external frontend deployments, use [backend/.env.phone.example](backend/.env.phone.example) and [frontend/.env.example](frontend/.env.example) as the source of truth.

Profile-specific database settings for Postgres/MySQL are defined in:

- [application-postgres.properties](backend/src/main/resources/application-postgres.properties)
- [application-mysql.properties](backend/src/main/resources/application-mysql.properties)

## Deployment

### Supported production shape

ScannerX currently supports a clean split deployment model:

- **Frontend:** Vercel
- **Backend:** NetHunter Rootless or another host capable of running Java + the scanner toolchain
- **Public backend exposure:** ngrok

### Deployment notes

- The frontend is a Vite SPA and uses [frontend/vercel.json](frontend/vercel.json) to rewrite all routes to `index.html`.
- The backend reads hosted phone/runtime configuration from environment variables, not from committed secrets.
- Free ngrok URLs are temporary. If the tunnel URL changes, update:
  - `VITE_API_BASE_URL`
  - `VITE_WS_BASE_URL`
  - then redeploy the frontend

### Local-first recommendation

For development and verification, prefer the local H2 workflow first. Treat phone + ngrok deployment as the hosted runtime path, not the default developer onboarding flow.

## AI Finding Enrichment

ScannerX includes optional, backend-side AI enrichment for persisted findings.

When enabled:

- raw scanner output is preserved
- findings can receive an `aiDescription`
- findings can receive an `exploitNarrative`
- the frontend prefers AI-enriched text when available
- reports can surface the enriched finding language as part of the reporting flow

The enrichment flow is server-side and additive. It is designed to improve clarity for operators and reviewers, not replace the original finding payload.

## Quality Checks

### Frontend

```bash
cd frontend
npm run lint
npm run build
```

### Backend

```bash
cd backend
./mvnw test
```

## Project Structure

```text
ScannerX/
├── backend/    # Spring Boot API, auth, scanning orchestration, data, AI enrichment
├── frontend/   # React/Vite workspace UI and Vercel deployment target
├── start.sh    # Local H2-first startup for backend + frontend
├── stop.sh     # Stops locally started ScannerX processes
└── README.md
```

## Responsible Use

ScannerX is a security tool. Only scan assets that you own or are explicitly authorized to assess. Respect scope, rate limits, maintenance windows, and applicable laws in every jurisdiction connected to your activity.

## Roadmap

- richer remediation guidance for persisted findings
- executive-facing report summaries
- duplicate finding clustering across runs
- confidence or false-positive assistance
- deeper comparative analytics across scan history

## Author

Built and maintained by [@udayXXkumar](https://github.com/udayXXkumar).

If you use ScannerX as a portfolio project, include screenshots and the live frontend link so reviewers can immediately understand the product surface and the quality of the operational workflow.
