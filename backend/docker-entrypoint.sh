#!/bin/bash

# ScannerX Docker Entrypoint Script
# Sets up environment and starts the application

set -e

# Validate required environment variables
if [ -z "$APP_JWT_SECRET" ] || [ "$APP_JWT_SECRET" = "CHANGE_THIS_TO_A_LONG_RANDOM_SECRET" ]; then
    echo "WARNING: APP_JWT_SECRET is not set or using default value!"
    echo "Please set APP_JWT_SECRET to a secure random string"
fi

# Log startup information
echo "======================================="
echo "ScannerX Starting"
echo "======================================="
echo "Profile: $SPRING_PROFILES_ACTIVE"
echo "Queue Mode: $APP_QUEUE_MODE"
echo "Port: $PORT"
echo "JWT Secret: ${APP_JWT_SECRET:0:10}..." 
echo "======================================="

# Execute the command passed as arguments
exec "$@"
