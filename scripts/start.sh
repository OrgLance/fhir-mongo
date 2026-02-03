#!/bin/bash

#===============================================================================
# FHIR Server - Start Script
# Usage: ./scripts/start.sh [OPTIONS]
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
PROFILE=""
DETACHED=true
BUILD=false
WAIT=true

#-------------------------------------------------------------------------------
# Functions
#-------------------------------------------------------------------------------

print_banner() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║                    FHIR R4 Server                             ║"
    echo "║              High-Performance Healthcare API                  ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -a, --all         Start all services (admin + monitoring)"
    echo "  -m, --admin       Start with admin UIs (MongoDB Express, Redis Commander)"
    echo "  -o, --monitoring  Start with monitoring (Prometheus, Grafana)"
    echo "  -b, --build       Build images before starting"
    echo "  -f, --foreground  Run in foreground (show logs)"
    echo "  -n, --no-wait     Don't wait for services to be healthy"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                # Start basic services (MongoDB, Redis, FHIR Server)"
    echo "  $0 --admin        # Start with admin UIs"
    echo "  $0 --monitoring   # Start with Prometheus & Grafana"
    echo "  $0 --all          # Start all services"
    echo "  $0 --all --build  # Rebuild and start all services"
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

check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed. Please install Docker first."
        exit 1
    fi

    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running. Please start Docker first."
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
}

get_compose_cmd() {
    if docker compose version &> /dev/null 2>&1; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

wait_for_service() {
    local service=$1
    local url=$2
    local max_attempts=60
    local attempt=1

    echo -n "  Waiting for $service"
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
        ((attempt++))
    done
    echo -e " ${RED}✗${NC}"
    return 1
}

print_services() {
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}                    Services Started                           ${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  ${BLUE}FHIR Server${NC}"
    echo "    • API:          http://localhost:8080/fhir"
    echo "    • Swagger UI:   http://localhost:8080/swagger-ui.html"
    echo "    • Health:       http://localhost:8080/actuator/health"
    echo "    • Metrics:      http://localhost:8080/actuator/prometheus"
    echo ""
    echo -e "  ${BLUE}Databases${NC}"
    echo "    • MongoDB:      localhost:27017  (fhiruser/fhirpass)"
    echo "    • Redis:        localhost:6379   (password: fhirRedis@2024)"

    if [[ "$PROFILE" == *"admin"* ]] || [[ "$PROFILE" == *"all"* ]]; then
        echo ""
        echo -e "  ${BLUE}Admin UIs${NC}"
        echo "    • MongoDB Express:   http://localhost:8081  (admin/admin123)"
        echo "    • Redis Commander:   http://localhost:8082  (admin/admin123)"
    fi

    if [[ "$PROFILE" == *"monitoring"* ]] || [[ "$PROFILE" == *"all"* ]]; then
        echo ""
        echo -e "  ${BLUE}Monitoring${NC}"
        echo "    • Prometheus:   http://localhost:9090"
        echo "    • Grafana:      http://localhost:3000  (admin/admin123)"
    fi

    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

#-------------------------------------------------------------------------------
# Parse Arguments
#-------------------------------------------------------------------------------

while [[ $# -gt 0 ]]; do
    case $1 in
        -a|--all)
            PROFILE="admin,monitoring"
            shift
            ;;
        -m|--admin)
            if [ -z "$PROFILE" ]; then
                PROFILE="admin"
            else
                PROFILE="$PROFILE,admin"
            fi
            shift
            ;;
        -o|--monitoring)
            if [ -z "$PROFILE" ]; then
                PROFILE="monitoring"
            else
                PROFILE="$PROFILE,monitoring"
            fi
            shift
            ;;
        -b|--build)
            BUILD=true
            shift
            ;;
        -f|--foreground)
            DETACHED=false
            shift
            ;;
        -n|--no-wait)
            WAIT=false
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

print_banner
check_docker

cd "$PROJECT_DIR"

COMPOSE_CMD=$(get_compose_cmd)

# Build compose command
CMD="$COMPOSE_CMD"

if [ -n "$PROFILE" ]; then
    # Handle multiple profiles
    IFS=',' read -ra PROFILES <<< "$PROFILE"
    for p in "${PROFILES[@]}"; do
        CMD="$CMD --profile $p"
    done
fi

CMD="$CMD up"

if [ "$BUILD" = true ]; then
    CMD="$CMD --build"
fi

if [ "$DETACHED" = true ]; then
    CMD="$CMD -d"
fi

# Start services
log_info "Starting FHIR Server services..."
if [ -n "$PROFILE" ]; then
    log_info "Profiles: $PROFILE"
fi

echo ""
eval $CMD

if [ "$DETACHED" = true ] && [ "$WAIT" = true ]; then
    echo ""
    log_info "Waiting for services to be healthy..."
    echo ""

    wait_for_service "MongoDB" "http://localhost:27017" || true
    wait_for_service "Redis" "http://localhost:6379" || true

    # Wait longer for FHIR server
    sleep 5
    wait_for_service "FHIR Server" "http://localhost:8080/actuator/health" || log_warn "FHIR Server may still be starting..."

    if [[ "$PROFILE" == *"admin"* ]] || [[ "$PROFILE" == *"all"* ]]; then
        wait_for_service "MongoDB Express" "http://localhost:8081" || true
        wait_for_service "Redis Commander" "http://localhost:8082" || true
    fi

    if [[ "$PROFILE" == *"monitoring"* ]] || [[ "$PROFILE" == *"all"* ]]; then
        wait_for_service "Prometheus" "http://localhost:9090" || true
        wait_for_service "Grafana" "http://localhost:3000" || true
    fi

    print_services
fi

if [ "$DETACHED" = false ]; then
    log_info "Running in foreground. Press Ctrl+C to stop."
fi
