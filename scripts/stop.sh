#!/bin/bash

#===============================================================================
# FHIR Server - Stop Script
# Usage: ./scripts/stop.sh [OPTIONS]
#===============================================================================

set -e

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
REMOVE_VOLUMES=false
REMOVE_IMAGES=false
REMOVE_ALL=false

#-------------------------------------------------------------------------------
# Functions
#-------------------------------------------------------------------------------

print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -v, --volumes     Remove volumes (deletes all data)"
    echo "  -i, --images      Remove images"
    echo "  -a, --all         Remove everything (volumes + images + orphans)"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                # Stop services, keep data"
    echo "  $0 --volumes      # Stop and remove all data"
    echo "  $0 --all          # Complete cleanup"
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

get_compose_cmd() {
    if docker compose version &> /dev/null 2>&1; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

#-------------------------------------------------------------------------------
# Parse Arguments
#-------------------------------------------------------------------------------

while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--volumes)
            REMOVE_VOLUMES=true
            shift
            ;;
        -i|--images)
            REMOVE_IMAGES=true
            shift
            ;;
        -a|--all)
            REMOVE_ALL=true
            REMOVE_VOLUMES=true
            REMOVE_IMAGES=true
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            print_usage
            exit 1
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
echo "║                 Stopping FHIR Server                          ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Build command
CMD="$COMPOSE_CMD --profile admin --profile monitoring down"

if [ "$REMOVE_VOLUMES" = true ]; then
    CMD="$CMD -v"
    log_warn "Volumes will be removed (all data will be deleted)"
fi

if [ "$REMOVE_IMAGES" = true ]; then
    CMD="$CMD --rmi local"
    log_warn "Local images will be removed"
fi

if [ "$REMOVE_ALL" = true ]; then
    CMD="$CMD --remove-orphans"
    log_warn "Orphan containers will be removed"
fi

echo ""
log_info "Stopping services..."
echo ""

eval $CMD

echo ""
log_info "All services stopped successfully."

if [ "$REMOVE_VOLUMES" = true ]; then
    echo ""
    log_info "Data volumes have been removed."
    log_info "Run './scripts/start.sh' to start fresh."
else
    echo ""
    log_info "Data volumes preserved."
    log_info "Run './scripts/start.sh' to restart services."
fi

echo ""
