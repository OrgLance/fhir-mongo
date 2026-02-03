#!/bin/bash

#===============================================================================
# FHIR Server - Logs Script
# Usage: ./scripts/logs.sh [SERVICE] [OPTIONS]
#===============================================================================

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Default values
SERVICE=""
FOLLOW=true
LINES=100

#-------------------------------------------------------------------------------
# Functions
#-------------------------------------------------------------------------------

print_usage() {
    echo "Usage: $0 [SERVICE] [OPTIONS]"
    echo ""
    echo "Services:"
    echo "  fhir, server      FHIR Server logs"
    echo "  mongo, mongodb    MongoDB logs"
    echo "  redis             Redis logs"
    echo "  prometheus        Prometheus logs"
    echo "  grafana           Grafana logs"
    echo "  all               All services (default)"
    echo ""
    echo "Options:"
    echo "  -n, --lines NUM   Number of lines to show (default: 100)"
    echo "  -f, --follow      Follow log output (default: true)"
    echo "  --no-follow       Don't follow log output"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                # Follow all logs"
    echo "  $0 fhir           # Follow FHIR server logs"
    echo "  $0 mongo -n 50    # Show last 50 MongoDB log lines"
    echo "  $0 all --no-follow -n 200  # Show last 200 lines of all services"
}

get_compose_cmd() {
    if docker compose version &> /dev/null 2>&1; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

get_service_name() {
    case $1 in
        fhir|server|fhir-server)
            echo "fhir-server"
            ;;
        mongo|mongodb)
            echo "mongodb"
            ;;
        redis)
            echo "redis"
            ;;
        mongo-express|mongoexpress)
            echo "mongo-express"
            ;;
        redis-commander|rediscommander)
            echo "redis-commander"
            ;;
        prometheus)
            echo "prometheus"
            ;;
        grafana)
            echo "grafana"
            ;;
        all|"")
            echo ""
            ;;
        *)
            echo "$1"
            ;;
    esac
}

#-------------------------------------------------------------------------------
# Parse Arguments
#-------------------------------------------------------------------------------

while [[ $# -gt 0 ]]; do
    case $1 in
        -n|--lines)
            LINES="$2"
            shift 2
            ;;
        -f|--follow)
            FOLLOW=true
            shift
            ;;
        --no-follow)
            FOLLOW=false
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        -*)
            echo -e "${RED}[ERROR]${NC} Unknown option: $1"
            print_usage
            exit 1
            ;;
        *)
            SERVICE=$(get_service_name "$1")
            shift
            ;;
    esac
done

#-------------------------------------------------------------------------------
# Main
#-------------------------------------------------------------------------------

cd "$PROJECT_DIR"

COMPOSE_CMD=$(get_compose_cmd)

# Build command
CMD="$COMPOSE_CMD --profile admin --profile monitoring logs"

if [ "$FOLLOW" = true ]; then
    CMD="$CMD -f"
fi

CMD="$CMD --tail=$LINES"

if [ -n "$SERVICE" ]; then
    CMD="$CMD $SERVICE"
fi

echo -e "${BLUE}[INFO]${NC} Showing logs for: ${SERVICE:-all services}"
echo -e "${BLUE}[INFO]${NC} Press Ctrl+C to exit"
echo ""

eval $CMD
