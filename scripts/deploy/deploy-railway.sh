#!/usr/bin/env bash
# =============================================================================
# deploy-railway.sh — NSE-MCX-Trends on Railway.app
# =============================================================================
# Railway auto-provisions PostgreSQL and Redis add-ons.
# Deploys backend (Spring Boot Docker) and frontend (static/Docker) services.
#
# Prerequisites:
#   - railway CLI installed: npm install -g @railway/cli
#   - railway login (authenticated)
#   - .env file present in project root
#
# Usage:
#   chmod +x deploy-railway.sh
#   ./deploy-railway.sh [--project-name nse-mcx-trends] [--environment production]
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
banner()  { echo -e "\n${BOLD}${CYAN}═══ $* ═══${NC}\n"; }

# ─── Defaults ─────────────────────────────────────────────────────────────────
PROJECT_NAME="${RAILWAY_PROJECT:-nse-mcx-trends}"
ENVIRONMENT="${RAILWAY_ENV:-production}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# ─── Parse CLI args ───────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --project-name) PROJECT_NAME="$2"; shift 2 ;;
        --environment)  ENVIRONMENT="$2"; shift 2 ;;
        --help|-h)
            echo "Usage: $0 [--project-name <name>] [--environment <env>]"
            exit 0 ;;
        *) error "Unknown option: $1" ;;
    esac
done

# ─── Prereq checks ────────────────────────────────────────────────────────────
banner "Checking prerequisites"

command -v railway &>/dev/null || error "railway CLI not installed. Run: npm install -g @railway/cli"
command -v jq &>/dev/null || error "jq not installed."
[[ -f "$PROJECT_DIR/.env" ]] || error ".env not found at $PROJECT_DIR/.env"

railway whoami &>/dev/null || error "Not authenticated. Run: railway login"
RAILWAY_USER=$(railway whoami 2>/dev/null | head -1)
success "Logged in as: $RAILWAY_USER"

# ─── Project setup ────────────────────────────────────────────────────────────
banner "Railway Project Setup"

cd "$PROJECT_DIR"

# Check if project is already linked
if railway status &>/dev/null; then
    info "Project already linked."
else
    info "Creating/linking Railway project: $PROJECT_NAME ..."
    # Try to link existing project first, else create new
    railway link --project "$PROJECT_NAME" --environment "$ENVIRONMENT" 2>/dev/null || \
    railway init --name "$PROJECT_NAME" --no-interactive 2>/dev/null || \
    { info "Project may already exist, proceeding ..."; }
fi

success "Railway project: $PROJECT_NAME  Environment: $ENVIRONMENT"

# ─── Provision PostgreSQL ──────────────────────────────────────────────────────
banner "PostgreSQL Add-on"

POSTGRES_EXISTS=$(railway service list 2>/dev/null | grep -i postgres || true)
if [[ -z "$POSTGRES_EXISTS" ]]; then
    info "Adding PostgreSQL plugin ..."
    railway add --plugin postgresql --name "postgres" 2>/dev/null || \
        warn "PostgreSQL may already exist or is being provisioned."
    success "PostgreSQL add-on requested"
else
    info "PostgreSQL already provisioned: $POSTGRES_EXISTS"
fi

# ─── Provision Redis ──────────────────────────────────────────────────────────
banner "Redis Add-on"

REDIS_EXISTS=$(railway service list 2>/dev/null | grep -i redis || true)
if [[ -z "$REDIS_EXISTS" ]]; then
    info "Adding Redis plugin ..."
    railway add --plugin redis --name "redis" 2>/dev/null || \
        warn "Redis may already exist or is being provisioned."
    success "Redis add-on requested"
else
    info "Redis already provisioned: $REDIS_EXISTS"
fi

# ─── Read env vars from .env file ─────────────────────────────────────────────
banner "Setting Environment Variables"

# Railway injects DATABASE_URL and REDIS_URL automatically from plugins.
# We set only the app-specific vars from our .env file.
BACKEND_ENV_VARS=(
    "JWT_SECRET"
    "JWT_EXPIRATION_MS"
    "CORS_ORIGINS"
    "OLLAMA_MODEL"
    "BROKER_ZERODHA_API_KEY"
    "BROKER_ZERODHA_API_SECRET"
    "BROKER_ZERODHA_ACCESS_TOKEN"
    "BROKER_ALICEBLUE_API_KEY"
    "BROKER_ALICEBLUE_USER_ID"
    "BROKER_ALICEBLUE_API_SECRET"
    "BROKER_BONANZA_API_KEY"
    "BROKER_BONANZA_API_SECRET"
    "YAHOO_FINANCE_API_KEY"
    "NSE_DATA_API_URL"
    "SPRING_PROFILES_ACTIVE"
    "JAVA_OPTS"
)

# Load .env file
set -o allexport
# shellcheck source=/dev/null
source "$PROJECT_DIR/.env"
set +o allexport

# Set Railway service to backend
if railway service list 2>/dev/null | grep -q "backend"; then
    railway service set backend 2>/dev/null || true
fi

for VAR in "${BACKEND_ENV_VARS[@]}"; do
    VALUE="${!VAR:-}"
    if [[ -n "$VALUE" ]]; then
        railway variables set "${VAR}=${VALUE}" 2>/dev/null || true
        info "Set: $VAR"
    else
        warn "Skipping empty variable: $VAR"
    fi
done

# Railway-specific Spring Boot configuration overrides
railway variables set "SPRING_PROFILES_ACTIVE=railway" 2>/dev/null || true
railway variables set "SERVER_PORT=8080" 2>/dev/null || true

success "Environment variables configured"

# ─── Generate railway.json ────────────────────────────────────────────────────
banner "Generating railway.json"

cat > "$PROJECT_DIR/railway.json" <<'RAILWAY_JSON'
{
  "$schema": "https://railway.app/railway.schema.json",
  "build": {
    "builder": "DOCKERFILE",
    "dockerfilePath": "infrastructure/docker/Dockerfile.backend",
    "buildCommand": null
  },
  "deploy": {
    "startCommand": null,
    "healthcheckPath": "/actuator/health",
    "healthcheckTimeout": 120,
    "restartPolicyType": "ON_FAILURE",
    "restartPolicyMaxRetries": 3
  }
}
RAILWAY_JSON

success "railway.json created"

# ─── Deploy backend ───────────────────────────────────────────────────────────
banner "Deploying Backend Service"

cd "$PROJECT_DIR/backend"

info "Deploying backend (Spring Boot Docker build) ..."
railway up \
    --service "backend" \
    --detach \
    --dockerfile "../infrastructure/docker/Dockerfile.backend" \
    --no-build 2>/dev/null || \
railway up \
    --detach 2>/dev/null || \
    warn "Deploy initiated — check Railway dashboard for status."

success "Backend deploy triggered"

# ─── Deploy frontend ──────────────────────────────────────────────────────────
banner "Deploying Frontend Service"

cd "$PROJECT_DIR/frontend"

# Set frontend env vars
BACKEND_URL=$(railway variables get RAILWAY_PUBLIC_DOMAIN 2>/dev/null || echo "")
if [[ -n "$BACKEND_URL" ]]; then
    railway variables set "VITE_API_BASE_URL=https://${BACKEND_URL}" \
        --service frontend 2>/dev/null || true
fi

info "Deploying frontend (React/Vite Docker build) ..."
railway up \
    --service "frontend" \
    --detach \
    --dockerfile "../infrastructure/docker/Dockerfile.frontend" \
    --no-build 2>/dev/null || \
railway up \
    --detach 2>/dev/null || \
    warn "Frontend deploy initiated — check Railway dashboard."

success "Frontend deploy triggered"

# ─── Deployment status ────────────────────────────────────────────────────────
banner "Deployment Status"

cd "$PROJECT_DIR"
railway status 2>/dev/null || true

DASHBOARD_URL="https://railway.app/project/$(railway project list 2>/dev/null | grep "$PROJECT_NAME" | awk '{print $1}' || echo "")"

echo -e "${GREEN}${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║         NSE-MCX-Trends — Railway Deployment              ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Services:                                               ║"
echo "║    - backend  (Spring Boot, port 8080)                   ║"
echo "║    - frontend (React/Nginx, port 80)                     ║"
echo "║    - postgres (Railway managed PostgreSQL)               ║"
echo "║    - redis    (Railway managed Redis)                    ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Note: Railway auto-provisions DATABASE_URL and          ║"
echo "║  REDIS_URL — no manual DB config needed.                 ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Dashboard: https://railway.app/dashboard                ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo "View logs:   railway logs --service backend"
echo "Open:        railway open"
echo "Variables:   railway variables"
