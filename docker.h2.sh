#!/bin/bash

# ScannerX Local H2 Testing - Quick Start Script
# This script sets up and starts the local H2 testing environment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.h2.yml"

# Functions
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Main commands
case "${1:-start}" in
    start)
        print_header "Starting ScannerX with Local H2 Database"
        
        print_info "Building Docker images..."
        docker-compose -f "$COMPOSE_FILE" build
        
        print_success "Images built"
        
        print_info "Starting services..."
        docker-compose -f "$COMPOSE_FILE" up -d
        
        print_info "Waiting for backend to be healthy..."
        sleep 5
        
        if docker-compose -f "$COMPOSE_FILE" exec -T backend curl -fsS http://127.0.0.1:8080/actuator/health > /dev/null; then
            print_success "Backend is healthy"
        else
            print_warning "Backend health check pending, waiting..."
            sleep 5
        fi
        
        print_header "ScannerX is Running!"
        print_info "Backend API: http://127.0.0.1:8080"
        print_info "Frontend: http://127.0.0.1:5173"
        print_info "H2 Console: http://127.0.0.1:8080/h2-console"
        print_info ""
        print_info "Default H2 credentials:"
        print_info "  Driver: org.h2.Driver"
        print_info "  URL: jdbc:h2:file:./data/scannerx"
        print_info "  Username: sa"
        print_info "  Password: (empty)"
        print_info ""
        print_info "Database file: ./backend/data/"
        print_info "Logs directory: ./logs/backend/"
        ;;
        
    stop)
        print_header "Stopping ScannerX"
        docker-compose -f "$COMPOSE_FILE" down
        print_success "Services stopped"
        ;;
        
    restart)
        print_header "Restarting ScannerX"
        docker-compose -f "$COMPOSE_FILE" down
        docker-compose -f "$COMPOSE_FILE" up -d
        print_success "Services restarted"
        sleep 3
        print_info "Services are starting up..."
        sleep 7
        print_success "Ready to use"
        ;;
        
    logs)
        print_header "Backend Logs"
        docker-compose -f "$COMPOSE_FILE" logs -f backend
        ;;
        
    logs-frontend)
        print_header "Frontend Logs"
        docker-compose -f "$COMPOSE_FILE" logs -f frontend
        ;;
        
    clean)
        print_header "Cleaning Up ScannerX"
        docker-compose -f "$COMPOSE_FILE" down
        print_info "Removing database..."
        rm -rf "$PROJECT_ROOT/backend/data" || true
        print_info "Removing logs..."
        rm -rf "$PROJECT_ROOT/logs" || true
        print_success "Cleanup complete"
        ;;
        
    status)
        print_header "ScannerX Status"
        docker-compose -f "$COMPOSE_FILE" ps
        ;;
        
    test-api)
        print_header "Testing Backend API"
        print_info "Testing health endpoint..."
        if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
            print_success "Backend health check passed"
        else
            print_error "Backend is not responding"
            exit 1
        fi
        
        print_info "Testing API health..."
        if curl -fsS http://127.0.0.1:8080/api/auth/me 2>&1 | grep -q "401\|Unauthorized"; then
            print_success "API is responding (401 expected without auth)"
        else
            print_error "API is not responding correctly"
            exit 1
        fi
        
        print_success "All API tests passed"
        ;;
        
    shell-backend)
        print_header "Opening Backend Container Shell"
        docker-compose -f "$COMPOSE_FILE" exec backend /bin/bash
        ;;
        
    shell-frontend)
        print_header "Opening Frontend Container Shell"
        docker-compose -f "$COMPOSE_FILE" exec frontend /bin/sh
        ;;
        
    *)
        cat << EOF

${BLUE}ScannerX Local H2 Testing - Helper Script${NC}

${YELLOW}Usage:${NC}
  ./docker.h2.sh [command]

${YELLOW}Commands:${NC}
  start          - Build and start all services
  stop           - Stop all services
  restart        - Restart all services  
  logs           - View backend logs (follow)
  logs-frontend  - View frontend logs (follow)
  clean          - Stop services and clean up data
  status         - Show service status
  test-api       - Run API health checks
  shell-backend  - Open interactive shell in backend container
  shell-frontend - Open interactive shell in frontend container

${YELLOW}Examples:${NC}
  ./docker.h2.sh start
  ./docker.h2.sh logs
  ./docker.h2.sh clean

${BLUE}Endpoints:${NC}
  Backend API:  http://127.0.0.1:8080
  Frontend:     http://127.0.0.1:5173
  H2 Console:   http://127.0.0.1:8080/h2-console

${BLUE}Database Info:${NC}
  File: ./backend/data/
  H2 URL: jdbc:h2:file:./data/scannerx
  Username: sa
  Password: (empty)

EOF
        exit 0
        ;;
esac
