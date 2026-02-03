#!/bin/bash

#===============================================================================
# FHIR Server - Build Script
# Usage: ./scripts/build.sh [OPTIONS]
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
SKIP_TESTS=false
DOCKER_BUILD=true
CLEAN=false

#-------------------------------------------------------------------------------
# Functions
#-------------------------------------------------------------------------------

print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -s, --skip-tests    Skip running tests"
    echo "  -c, --clean         Clean build (mvn clean)"
    echo "  -m, --maven-only    Only build Maven, skip Docker"
    echo "  -d, --docker-only   Only build Docker image (assumes JAR exists)"
    echo "  -h, --help          Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                  # Full build (Maven + Docker)"
    echo "  $0 --skip-tests     # Build without tests"
    echo "  $0 --clean          # Clean and build"
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

check_java() {
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed. Please install Java 17+."
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        log_error "Java 17+ is required. Current version: $JAVA_VERSION"
        exit 1
    fi
}

check_maven() {
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed. Please install Maven 3.8+."
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

#-------------------------------------------------------------------------------
# Parse Arguments
#-------------------------------------------------------------------------------

MAVEN_BUILD=true

while [[ $# -gt 0 ]]; do
    case $1 in
        -s|--skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -c|--clean)
            CLEAN=true
            shift
            ;;
        -m|--maven-only)
            DOCKER_BUILD=false
            shift
            ;;
        -d|--docker-only)
            MAVEN_BUILD=false
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

echo -e "${BLUE}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║                 Building FHIR Server                          ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Maven Build
if [ "$MAVEN_BUILD" = true ]; then
    check_java
    check_maven

    MVN_CMD="mvn"

    if [ "$CLEAN" = true ]; then
        MVN_CMD="$MVN_CMD clean"
    fi

    MVN_CMD="$MVN_CMD package"

    if [ "$SKIP_TESTS" = true ]; then
        MVN_CMD="$MVN_CMD -DskipTests"
        log_warn "Skipping tests"
    fi

    log_info "Building Maven project..."
    echo ""

    eval $MVN_CMD

    echo ""
    log_info "Maven build complete!"
    echo ""
fi

# Docker Build
if [ "$DOCKER_BUILD" = true ]; then
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed. Please install Docker."
        exit 1
    fi

    log_info "Building Docker image..."
    echo ""

    COMPOSE_CMD=$(get_compose_cmd)
    $COMPOSE_CMD build fhir-server

    echo ""
    log_info "Docker build complete!"
    echo ""

    # Show image info
    log_info "Docker image details:"
    docker images | grep -E "fhir|REPOSITORY" | head -5
fi

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}                    Build Successful!                          ${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "Next steps:"
echo "  • Start services:  ./scripts/start.sh"
echo "  • Run locally:     mvn spring-boot:run"
echo ""
