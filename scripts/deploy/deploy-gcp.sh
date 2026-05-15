#!/usr/bin/env bash
# =============================================================================
# deploy-gcp.sh — NSE-MCX-Trends on Google Cloud Platform
# =============================================================================
# Provisions:
#   - GCP project firewall rules for required ports
#   - Compute Engine e2-standard-2 (2 vCPU, 8 GB) with Ubuntu 22.04
#   - Docker + docker compose installed via startup script
#   - Project files deployed via SCP
#
# Prerequisites:
#   - gcloud CLI installed and authenticated: gcloud auth login
#   - A GCP project with billing enabled
#   - .env file present in project root
#
# Usage:
#   chmod +x deploy-gcp.sh
#   ./deploy-gcp.sh [--project my-project] [--zone asia-south1-a] [--machine-type e2-standard-4]
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
GCP_PROJECT="${GCLOUD_PROJECT:-$(gcloud config get-value project 2>/dev/null || true)}"
GCP_ZONE="${GCP_ZONE:-asia-south1-a}"
GCP_REGION="${GCP_ZONE%-*}"
MACHINE_TYPE="${GCP_MACHINE_TYPE:-e2-standard-2}"
INSTANCE_NAME="nse-mcx-trends-vm"
FIREWALL_TAG="nse-mcx-server"
DISK_SIZE_GB=50
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/id_rsa}"

# ─── Parse CLI args ───────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --project)      GCP_PROJECT="$2"; shift 2 ;;
        --zone)         GCP_ZONE="$2"; GCP_REGION="${2%-*}"; shift 2 ;;
        --machine-type) MACHINE_TYPE="$2"; shift 2 ;;
        --ssh-key)      SSH_KEY_PATH="$2"; shift 2 ;;
        --help|-h)
            echo "Usage: $0 [--project <id>] [--zone <zone>] [--machine-type <type>]"
            exit 0 ;;
        *) error "Unknown option: $1" ;;
    esac
done

# ─── Prereq checks ────────────────────────────────────────────────────────────
banner "Checking prerequisites"

for cmd in gcloud ssh scp jq; do
    command -v "$cmd" &>/dev/null && success "$cmd found" || error "$cmd not installed."
done

[[ -n "$GCP_PROJECT" ]] || error "GCP project not set. Pass --project <id> or run: gcloud config set project <id>"
[[ -f "$PROJECT_DIR/.env" ]] || error ".env not found at $PROJECT_DIR/.env"
[[ -f "$SSH_KEY_PATH" ]] || error "SSH key not found: $SSH_KEY_PATH"

gcloud projects describe "$GCP_PROJECT" &>/dev/null || error "Cannot access GCP project: $GCP_PROJECT"
success "Project: $GCP_PROJECT  Zone: $GCP_ZONE  Machine: $MACHINE_TYPE"

# Enable required APIs
banner "Enabling GCP APIs"
for API in compute.googleapis.com; do
    gcloud services enable "$API" --project="$GCP_PROJECT" --quiet
    success "Enabled: $API"
done

# ─── Firewall Rules ───────────────────────────────────────────────────────────
banner "Configuring firewall rules"

FIREWALL_RULE="allow-nse-mcx-ports"
if ! gcloud compute firewall-rules describe "$FIREWALL_RULE" \
    --project="$GCP_PROJECT" &>/dev/null; then
    gcloud compute firewall-rules create "$FIREWALL_RULE" \
        --project="$GCP_PROJECT" \
        --direction=INGRESS \
        --priority=1000 \
        --network=default \
        --action=ALLOW \
        --rules=tcp:22,tcp:80,tcp:443,tcp:3000,tcp:8080,tcp:9092,tcp:5050,tcp:8090,tcp:11434 \
        --source-ranges=0.0.0.0/0 \
        --target-tags="$FIREWALL_TAG" \
        --description="NSE-MCX-Trends application ports"
    success "Firewall rule created: $FIREWALL_RULE"
else
    info "Firewall rule already exists: $FIREWALL_RULE"
fi

# ─── Startup Script ───────────────────────────────────────────────────────────
STARTUP_SCRIPT=$(cat <<'STARTUP'
#!/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

# Install Docker
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable docker
systemctl start docker
usermod -aG docker ubuntu

# Kernel tuning
cat >> /etc/sysctl.conf <<EOF
vm.max_map_count=262144
net.core.somaxconn=65535
EOF
sysctl -p

echo "Startup script complete."
STARTUP
)

# ─── Create or reuse VM ───────────────────────────────────────────────────────
banner "Provisioning Compute Engine VM"

INSTANCE_EXISTS=$(gcloud compute instances list \
    --project="$GCP_PROJECT" \
    --zones="$GCP_ZONE" \
    --filter="name=$INSTANCE_NAME AND status=RUNNING" \
    --format="value(name)" 2>/dev/null || true)

if [[ -n "$INSTANCE_EXISTS" ]]; then
    info "VM already running: $INSTANCE_NAME"
else
    info "Creating VM: $INSTANCE_NAME ($MACHINE_TYPE) in $GCP_ZONE ..."
    gcloud compute instances create "$INSTANCE_NAME" \
        --project="$GCP_PROJECT" \
        --zone="$GCP_ZONE" \
        --machine-type="$MACHINE_TYPE" \
        --image-family=ubuntu-2204-lts \
        --image-project=ubuntu-os-cloud \
        --boot-disk-size="${DISK_SIZE_GB}GB" \
        --boot-disk-type=pd-balanced \
        --tags="$FIREWALL_TAG" \
        --metadata="startup-script=$STARTUP_SCRIPT" \
        --scopes=cloud-platform
    success "VM created: $INSTANCE_NAME"
fi

# ─── Get external IP ──────────────────────────────────────────────────────────
EXTERNAL_IP=$(gcloud compute instances describe "$INSTANCE_NAME" \
    --project="$GCP_PROJECT" \
    --zone="$GCP_ZONE" \
    --format="value(networkInterfaces[0].accessConfigs[0].natIP)")

success "External IP: $EXTERNAL_IP"

# ─── Wait for VM and Docker to be ready ───────────────────────────────────────
banner "Waiting for SSH readiness"
sleep 30   # Allow startup script to run

for i in $(seq 1 24); do
    gcloud compute ssh "ubuntu@$INSTANCE_NAME" \
        --project="$GCP_PROJECT" \
        --zone="$GCP_ZONE" \
        --ssh-flag="-o ConnectTimeout=5" \
        --command="docker info &>/dev/null && echo ready" 2>/dev/null && break
    echo -n "."
    sleep 10
done
echo ""
success "VM and Docker are ready"

# ─── Copy files ───────────────────────────────────────────────────────────────
banner "Deploying application files"

REMOTE_DIR="/home/ubuntu/nse-mcx-trends"

gcloud compute ssh "ubuntu@$INSTANCE_NAME" \
    --project="$GCP_PROJECT" \
    --zone="$GCP_ZONE" \
    --command="mkdir -p $REMOTE_DIR/infrastructure/docker"

gcloud compute scp --project="$GCP_PROJECT" --zone="$GCP_ZONE" \
    "$PROJECT_DIR/docker-compose.yml" \
    "$PROJECT_DIR/.env" \
    "ubuntu@$INSTANCE_NAME:$REMOTE_DIR/"

gcloud compute scp --project="$GCP_PROJECT" --zone="$GCP_ZONE" --recurse \
    "$PROJECT_DIR/infrastructure/docker" \
    "ubuntu@$INSTANCE_NAME:$REMOTE_DIR/infrastructure/"

success "Files deployed to $INSTANCE_NAME:$REMOTE_DIR"

# ─── Start services ───────────────────────────────────────────────────────────
banner "Starting services"

gcloud compute ssh "ubuntu@$INSTANCE_NAME" \
    --project="$GCP_PROJECT" \
    --zone="$GCP_ZONE" \
    --command="
        cd $REMOTE_DIR
        docker compose pull --quiet
        docker compose up -d postgres redis zookeeper kafka
        sleep 25
        docker compose up -d
        docker compose ps
    "

success "All services started"

# ─── Summary ──────────────────────────────────────────────────────────────────
banner "Deployment Complete"

echo -e "${GREEN}${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║          NSE-MCX-Trends — GCP Compute Engine             ║"
echo "╠══════════════════════════════════════════════════════════╣"
printf "║  Instance    : %-42s ║\n" "$INSTANCE_NAME"
printf "║  Zone        : %-42s ║\n" "$GCP_ZONE"
printf "║  External IP : %-42s ║\n" "$EXTERNAL_IP"
echo "╠══════════════════════════════════════════════════════════╣"
printf "║  Frontend    : http://%-36s ║\n" "$EXTERNAL_IP:3000"
printf "║  Backend API : http://%-36s ║\n" "$EXTERNAL_IP:8080/api"
printf "║  pgAdmin     : http://%-36s ║\n" "$EXTERNAL_IP:5050"
printf "║  Kafka UI    : http://%-36s ║\n" "$EXTERNAL_IP:8090"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo "SSH: gcloud compute ssh ubuntu@$INSTANCE_NAME --project=$GCP_PROJECT --zone=$GCP_ZONE"
