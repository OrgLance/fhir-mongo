#!/bin/bash

#===============================================================================
# FHIR Server - Restart Script
# Usage: ./scripts/restart.sh [SERVICE] [OPTIONS]
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
BUILD=false

#-------------------------------------------------------------------------------
# Functions
#-------------------------------------------------------------------------------

print_usage() {
    echo "Usage: $0 [SERVICE] [OPTIONS]"
    echo ""
    echo "Services:"
    echo "  fhir, server      Restart FHIR Server"
    echo "  mongo, mongodb    Restart MongoDB"
    echo "  redis             Restart Redis"
    echo "  all               Restart all services (default)"
    echo ""
    echo "Options:"
    echo "  -b, --build       Rebuild before restarting"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                # Restart all services"
    echo "  $0 fhir           # Restart only FHIR server"
    echo "  $0 fhir --build   # Rebuild and restart FHIR server"
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
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
        -b|--build)
            BUILD=true
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

echo -e "${BLUE}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║                Restarting FHIR Server                         ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

if [ -n "$SERVICE" ]; then
    log_info "Restarting service: $SERVICE"

    if [ "$BUILD" = true ] && [ "$SERVICE" = "fhir-server" ]; then
        log_info "Rebuilding $SERVICE..."
        $COMPOSE_CMD build $SERVICE
    fi

    $COMPOSE_CMD --profile admin --profile monitoring --profile redis restart $SERVICE
else
    log_info "Restarting all services..."

    if [ "$BUILD" = true ]; then
        log_info "Rebuilding services..."
        $COMPOSE_CMD build
    fi

    $COMPOSE_CMD --profile admin --profile monitoring --profile redis restart
fi

echo ""
log_info "Restart complete!"
echo ""

# Show status
"$SCRIPT_DIR/status.sh"
