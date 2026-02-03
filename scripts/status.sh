#!/bin/bash

#===============================================================================
# FHIR Server - Status Script
# Usage: ./scripts/status.sh
#===============================================================================

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

#-------------------------------------------------------------------------------
# Functions
#-------------------------------------------------------------------------------

get_compose_cmd() {
    if docker compose version &> /dev/null 2>&1; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

check_service() {
    local name=$1
    local url=$2
    local timeout=${3:-2}

    if curl -s --max-time $timeout "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}●${NC} UP"
    else
        echo -e "${RED}●${NC} DOWN"
    fi
}

check_port() {
    local port=$1
    if nc -z localhost $port 2>/dev/null; then
        echo -e "${GREEN}●${NC} OPEN"
    else
        echo -e "${RED}●${NC} CLOSED"
    fi
}

format_bytes() {
    local bytes=$1
    if [ $bytes -ge 1073741824 ]; then
        echo "$(echo "scale=2; $bytes/1073741824" | bc) GB"
    elif [ $bytes -ge 1048576 ]; then
        echo "$(echo "scale=2; $bytes/1048576" | bc) MB"
    elif [ $bytes -ge 1024 ]; then
        echo "$(echo "scale=2; $bytes/1024" | bc) KB"
    else
        echo "$bytes B"
    fi
}

#-------------------------------------------------------------------------------
# Main
#-------------------------------------------------------------------------------

cd "$PROJECT_DIR"

COMPOSE_CMD=$(get_compose_cmd)

echo -e "${BLUE}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║                 FHIR Server Status                            ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Container Status
echo -e "${CYAN}Container Status:${NC}"
echo "─────────────────────────────────────────────────────────────────"
$COMPOSE_CMD --profile admin --profile monitoring ps 2>/dev/null || echo "No containers running"
echo ""

# Service Health
echo -e "${CYAN}Service Health:${NC}"
echo "─────────────────────────────────────────────────────────────────"
printf "  %-20s %-10s %s\n" "Service" "Port" "Status"
echo "  ─────────────────────────────────────────────────────────────"
printf "  %-20s %-10s %s\n" "FHIR Server" "8080" "$(check_service 'fhir' 'http://localhost:8080/actuator/health')"
printf "  %-20s %-10s %s\n" "MongoDB" "27017" "$(check_port 27017)"
printf "  %-20s %-10s %s\n" "Redis" "6379" "$(check_port 6379)"
printf "  %-20s %-10s %s\n" "MongoDB Express" "8081" "$(check_service 'mongo-express' 'http://localhost:8081')"
printf "  %-20s %-10s %s\n" "Redis Commander" "8082" "$(check_service 'redis-commander' 'http://localhost:8082')"
printf "  %-20s %-10s %s\n" "Prometheus" "9090" "$(check_service 'prometheus' 'http://localhost:9090')"
printf "  %-20s %-10s %s\n" "Grafana" "3000" "$(check_service 'grafana' 'http://localhost:3000')"
echo ""

# FHIR Server Details
if curl -s --max-time 2 "http://localhost:8080/actuator/health" > /dev/null 2>&1; then
    echo -e "${CYAN}FHIR Server Details:${NC}"
    echo "─────────────────────────────────────────────────────────────────"

    # Get health details
    HEALTH=$(curl -s "http://localhost:8080/actuator/health" 2>/dev/null)
    STATUS=$(echo $HEALTH | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)

    echo "  Status: ${GREEN}$STATUS${NC}"

    # Get metrics if available
    if curl -s --max-time 2 "http://localhost:8080/actuator/metrics/jvm.memory.used" > /dev/null 2>&1; then
        MEM_USED=$(curl -s "http://localhost:8080/actuator/metrics/jvm.memory.used" 2>/dev/null | grep -o '"value":[0-9.]*' | cut -d':' -f2 | head -1)
        if [ -n "$MEM_USED" ]; then
            echo "  Memory Used: $(format_bytes ${MEM_USED%.*})"
        fi
    fi

    # Get info
    INFO=$(curl -s "http://localhost:8080/actuator/info" 2>/dev/null)
    if [ -n "$INFO" ] && [ "$INFO" != "{}" ]; then
        echo "  Info: $INFO"
    fi
    echo ""
fi

# Volume Status
echo -e "${CYAN}Volume Status:${NC}"
echo "─────────────────────────────────────────────────────────────────"
docker volume ls --filter "name=fhirspringboot" --format "table {{.Name}}\t{{.Driver}}" 2>/dev/null || echo "No volumes found"
echo ""

# Resource Usage
echo -e "${CYAN}Resource Usage:${NC}"
echo "─────────────────────────────────────────────────────────────────"
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" $(docker ps -q --filter "name=fhir") 2>/dev/null || echo "No running containers"
echo ""

# Quick Links
echo -e "${CYAN}Quick Links:${NC}"
echo "─────────────────────────────────────────────────────────────────"
echo "  • Swagger UI:      http://localhost:8080/swagger-ui.html"
echo "  • FHIR Metadata:   http://localhost:8080/fhir/metadata"
echo "  • Health Check:    http://localhost:8080/actuator/health"
echo "  • Prometheus:      http://localhost:8080/actuator/prometheus"
echo ""
