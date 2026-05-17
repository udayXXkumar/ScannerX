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


### Stop the local stack

```bash
./stop.sh
```


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


## Responsible Use

ScannerX is a security tool. Only scan assets that you own or are explicitly authorized to assess. Respect scope, rate limits, maintenance windows, and applicable laws in every jurisdiction connected to your activity.


## Author

Built and maintained by [@udayXXkumar](https://github.com/udayXXkumar).
