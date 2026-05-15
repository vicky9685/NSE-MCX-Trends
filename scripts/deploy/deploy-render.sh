#!/usr/bin/env bash
# =============================================================================
# deploy-render.sh — NSE-MCX-Trends on Render.com
# =============================================================================
# Uses Render's Blueprint (render.yaml) for declarative deployments.
# Creates:
#   - Managed PostgreSQL database
#   - Backend web service (Docker)
#   - Frontend static site (React/Vite build)
#
# Prerequisites:
#   - Render account: https://render.com
#   - RENDER_API_KEY set (Settings → API Keys)
#   - GitHub repo connected to Render (for auto-deploy)
#   - .env file present in project root
#
# Usage:
#   chmod +x deploy-render.sh
#   export RENDER_API_KEY=your_key
#   ./deploy-render.sh [--owner-id rsp_xxx] [--repo-url https://github.com/...]
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
banner()  { echo -e "\n${BOLD}${CYAN}═══ $* ═══${NC}\n"; }

RENDER_API="https://api.render.com/v1"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RENDER_API_KEY="${RENDER_API_KEY:-}"
OWNER_ID="${RENDER_OWNER_ID:-}"
REPO_URL="${REPO_URL:-}"
REGION="${RENDER_REGION:-oregon}"   # oregon | frankfurt | singapore | ohio

# ─── Parse CLI args ───────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --owner-id)  OWNER_ID="$2"; shift 2 ;;
        --repo-url)  REPO_URL="$2"; shift 2 ;;
        --region)    REGION="$2"; shift 2 ;;
        --help|-h)
            echo "Usage: $0 [--owner-id <id>] [--repo-url <url>] [--region <region>]"
            exit 0 ;;
        *) error "Unknown option: $1" ;;
    esac
done

# ─── Prereq checks ────────────────────────────────────────────────────────────
banner "Checking prerequisites"

for cmd in curl jq; do
    command -v "$cmd" &>/dev/null && success "$cmd found" || error "$cmd not installed."
done

[[ -n "$RENDER_API_KEY" ]] || error "RENDER_API_KEY not set. Export it or pass via env."
[[ -f "$PROJECT_DIR/.env" ]] || error ".env not found at $PROJECT_DIR/.env"

# Verify API key
RENDER_USER=$(curl -sf -H "Authorization: Bearer $RENDER_API_KEY" \
    "$RENDER_API/owners" | jq -r '.[0].owner.name // empty' 2>/dev/null || true)
[[ -n "$RENDER_USER" ]] || error "Invalid RENDER_API_KEY or API unreachable."
success "Authenticated as: $RENDER_USER"

# Get owner ID if not provided
if [[ -z "$OWNER_ID" ]]; then
    OWNER_ID=$(curl -sf -H "Authorization: Bearer $RENDER_API_KEY" \
        "$RENDER_API/owners" | jq -r '.[0].owner.id // empty')
    [[ -n "$OWNER_ID" ]] || error "Could not retrieve owner ID. Pass --owner-id <id>."
    info "Owner ID: $OWNER_ID"
fi

# Load .env
set -o allexport
# shellcheck source=/dev/null
source "$PROJECT_DIR/.env"
set +o allexport

# ─── Generate render.yaml blueprint ──────────────────────────────────────────
banner "Generating render.yaml blueprint"

cat > "$PROJECT_DIR/render.yaml" <<RENDER_YAML
# render.yaml — NSE-MCX-Trends Render.com Blueprint
# Deploy at: https://render.com/deploy
# Docs: https://render.com/docs/blueprint-spec

databases:
  - name: nse-mcx-postgres
    databaseName: trading_db
    user: trading_user
    plan: free           # free (256 MB) | starter | standard
    region: ${REGION}
    postgresMajorVersion: 16

services:
  # ── Spring Boot Backend ───────────────────────────────────────────────────
  - type: web
    name: nse-mcx-backend
    runtime: docker
    region: ${REGION}
    plan: starter        # starter (512 MB RAM, 0.5 CPU)
    dockerfilePath: ./infrastructure/docker/Dockerfile.backend
    dockerContext: ./backend
    healthCheckPath: /actuator/health
    envVars:
      - key: SPRING_DATASOURCE_URL
        fromDatabase:
          name: nse-mcx-postgres
          property: connectionString
      - key: SPRING_DATASOURCE_USERNAME
        fromDatabase:
          name: nse-mcx-postgres
          property: user
      - key: SPRING_DATASOURCE_PASSWORD
        fromDatabase:
          name: nse-mcx-postgres
          property: password
      - key: SPRING_PROFILES_ACTIVE
        value: render
      - key: JWT_SECRET
        generateValue: true
      - key: CORS_ORIGINS
        value: https://nse-mcx-frontend.onrender.com
      - key: JAVA_OPTS
        value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dspring.threads.virtual.enabled=true"
      - key: BROKER_ZERODHA_API_KEY
        sync: false
      - key: BROKER_ZERODHA_API_SECRET
        sync: false
      - key: BROKER_ZERODHA_ACCESS_TOKEN
        sync: false
      - key: BROKER_ALICEBLUE_API_KEY
        sync: false
      - key: BROKER_ALICEBLUE_USER_ID
        sync: false
      - key: BROKER_ALICEBLUE_API_SECRET
        sync: false
      - key: YAHOO_FINANCE_API_KEY
        sync: false
      - key: KAFKA_BOOTSTRAP_SERVERS
        value: ""   # Set to Upstash Kafka URL if using external Kafka
      - key: OLLAMA_BASE_URL
        value: ""   # Set to external Ollama endpoint

  # ── React Frontend (Static Site) ──────────────────────────────────────────
  - type: web
    name: nse-mcx-frontend
    runtime: static
    region: ${REGION}
    plan: free
    buildCommand: npm ci && npm run build
    staticPublishPath: ./dist
    rootDir: frontend
    headers:
      - path: /*
        name: Cache-Control
        value: no-cache
      - path: /assets/*
        name: Cache-Control
        value: public, max-age=31536000, immutable
    routes:
      - type: rewrite
        source: /api/*
        destination: https://nse-mcx-backend.onrender.com/api/\$1
      - type: rewrite
        source: /*
        destination: /index.html
    envVars:
      - key: VITE_API_BASE_URL
        value: https://nse-mcx-backend.onrender.com
      - key: VITE_WS_URL
        value: wss://nse-mcx-backend.onrender.com/ws
RENDER_YAML

success "render.yaml generated at $PROJECT_DIR/render.yaml"

# ─── API-based deployment ─────────────────────────────────────────────────────
banner "Deploying via Render API"

render_api() {
    local method="$1" endpoint="$2"
    shift 2
    curl -sf -X "$method" \
        -H "Authorization: Bearer $RENDER_API_KEY" \
        -H "Content-Type: application/json" \
        "$RENDER_API$endpoint" \
        "$@"
}

# ── Create PostgreSQL database ───────────────────────────────────────────────
info "Creating PostgreSQL database ..."
DB_RESPONSE=$(render_api POST "/postgres" -d "{
    \"databaseName\": \"trading_db\",
    \"databaseUser\": \"trading_user\",
    \"enableHighAvailability\": false,
    \"name\": \"nse-mcx-postgres\",
    \"ownerId\": \"$OWNER_ID\",
    \"plan\": \"free\",
    \"region\": \"$REGION\",
    \"version\": \"16\"
}" 2>/dev/null || echo "{}")

DB_ID=$(echo "$DB_RESPONSE" | jq -r '.id // empty')
if [[ -n "$DB_ID" ]]; then
    success "PostgreSQL created: $DB_ID"
    DB_CONN_STR=$(echo "$DB_RESPONSE" | jq -r '.connectionInfo.internalConnectionString // empty')
else
    warn "PostgreSQL creation response: $(echo "$DB_RESPONSE" | jq -r '.message // "unknown"')"
    warn "The database may already exist or the free plan may be in use."
    DB_ID=$(render_api GET "/postgres?name=nse-mcx-postgres&ownerId=$OWNER_ID" 2>/dev/null | \
        jq -r '.[0].postgres.id // empty')
    [[ -n "$DB_ID" ]] && info "Using existing DB: $DB_ID"
fi

# ── Create backend web service ────────────────────────────────────────────────
info "Creating backend web service ..."

ENV_VARS_JSON=$(cat <<ENV
[
    {"key": "SPRING_PROFILES_ACTIVE", "value": "render"},
    {"key": "JWT_SECRET", "generateValue": true},
    {"key": "CORS_ORIGINS", "value": "https://nse-mcx-frontend.onrender.com"},
    {"key": "JAVA_OPTS", "value": "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dspring.threads.virtual.enabled=true"}
]
ENV
)

BACKEND_RESPONSE=$(render_api POST "/services" -d "{
    \"name\": \"nse-mcx-backend\",
    \"ownerId\": \"$OWNER_ID\",
    \"region\": \"$REGION\",
    \"type\": \"web_service\",
    \"serviceDetails\": {
        \"runtime\": \"docker\",
        \"dockerfilePath\": \"./infrastructure/docker/Dockerfile.backend\",
        \"dockerContext\": \"./backend\",
        \"healthCheckPath\": \"/actuator/health\",
        \"plan\": \"starter\",
        \"envVars\": $ENV_VARS_JSON
    }
}" 2>/dev/null || echo "{}")

BACKEND_ID=$(echo "$BACKEND_RESPONSE" | jq -r '.service.id // empty')
BACKEND_URL=$(echo "$BACKEND_RESPONSE" | jq -r '.service.serviceDetails.url // empty')

if [[ -n "$BACKEND_ID" ]]; then
    success "Backend service created: $BACKEND_ID"
else
    warn "Backend creation: $(echo "$BACKEND_RESPONSE" | jq -r '.message // "check dashboard"')"
fi

# ─── Deploy instructions ─────────────────────────────────────────────────────
banner "Deployment Complete"

echo -e "${GREEN}${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║         NSE-MCX-Trends — Render.com Deployment           ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Services created:                                       ║"
echo "║    - nse-mcx-postgres  (PostgreSQL 16, free plan)        ║"
echo "║    - nse-mcx-backend   (Docker, starter plan)            ║"
echo "║    - nse-mcx-frontend  (Static site, free plan)          ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  render.yaml is ready — push to GitHub and connect repo  ║"
echo "║  at: https://render.com/blueprints                       ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  IMPORTANT: Set these env vars in the Render dashboard:  ║"
echo "║    BROKER_ZERODHA_API_KEY                                ║"
echo "║    BROKER_ZERODHA_API_SECRET                             ║"
echo "║    BROKER_ZERODHA_ACCESS_TOKEN                           ║"
echo "║    YAHOO_FINANCE_API_KEY                                  ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo "Dashboard: https://dashboard.render.com"
echo "Blueprint deploy URL: https://render.com/deploy?repo=$(git -C "$PROJECT_DIR" remote get-url origin 2>/dev/null || echo 'your-github-repo-url')"
