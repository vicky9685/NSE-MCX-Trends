#!/usr/bin/env bash
# =============================================================================
# deploy-oracle-cloud.sh — NSE-MCX-Trends on Oracle Cloud Free Tier
# =============================================================================
# Provisions 2× Always Free Ampere A1 instances (4 OCPU, 24 GB RAM total)
# in Oracle Cloud using instance principal auth (no IAM user credentials needed).
#
# Prerequisites:
#   - oci CLI configured: oci setup config
#   - Docker + docker compose installed locally
#   - SSH key pair at ~/.ssh/id_rsa (or set SSH_KEY_PATH)
#   - .env file present in the project root
#
# Usage:
#   chmod +x deploy-oracle-cloud.sh
#   ./deploy-oracle-cloud.sh [--compartment-id <ocid>] [--region <region>]
# =============================================================================

set -euo pipefail

# ─── Colour helpers ───────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
banner()  { echo -e "\n${BOLD}${CYAN}═══ $* ═══${NC}\n"; }

# ─── Default configuration ────────────────────────────────────────────────────
REGION="${OCI_REGION:-ap-mumbai-1}"
COMPARTMENT_ID="${OCI_COMPARTMENT_ID:-}"
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/id_rsa}"
SSH_PUB_KEY_PATH="${SSH_KEY_PATH}.pub"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_NAME="nse-mcx-trends"
INSTANCE_SHAPE="VM.Standard.A1.Flex"
INSTANCE_OCPUS=2
INSTANCE_MEMORY_GB=12
INSTANCE_COUNT=2
OS_IMAGE_OS="Canonical Ubuntu"
OS_IMAGE_VERSION="22.04"
OLLAMA_MODEL="llama3.2"

# ─── Parse CLI args ───────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --compartment-id) COMPARTMENT_ID="$2"; shift 2 ;;
        --region)         REGION="$2";         shift 2 ;;
        --ssh-key)        SSH_KEY_PATH="$2"; SSH_PUB_KEY_PATH="${2}.pub"; shift 2 ;;
        --help|-h)
            echo "Usage: $0 [--compartment-id <ocid>] [--region <region>] [--ssh-key <path>]"
            exit 0
            ;;
        *) error "Unknown option: $1" ;;
    esac
done

# ─── Prerequisite checks ──────────────────────────────────────────────────────
banner "Checking prerequisites"

for cmd in oci docker ssh scp curl jq; do
    if command -v "$cmd" &>/dev/null; then
        success "$cmd found: $(command -v "$cmd")"
    else
        error "$cmd is not installed. Please install it and re-run."
    fi
done

docker compose version &>/dev/null || error "docker compose plugin not found. Install Docker Compose v2."

[[ -f "$SSH_KEY_PATH" ]]     || error "SSH private key not found: $SSH_KEY_PATH"
[[ -f "$SSH_PUB_KEY_PATH" ]] || error "SSH public key not found: $SSH_PUB_KEY_PATH"
[[ -f "$PROJECT_DIR/.env" ]] || error ".env file not found at $PROJECT_DIR/.env — copy .env.example and fill in values."

# Resolve compartment ID
if [[ -z "$COMPARTMENT_ID" ]]; then
    COMPARTMENT_ID=$(oci iam compartment list --all --query 'data[0].id' --raw-output 2>/dev/null || true)
    [[ -n "$COMPARTMENT_ID" ]] || error "Could not determine compartment ID. Pass --compartment-id <ocid>."
    info "Using compartment: $COMPARTMENT_ID"
fi

SSH_PUB_KEY_CONTENT=$(cat "$SSH_PUB_KEY_PATH")

# ─── VCN and Networking ───────────────────────────────────────────────────────
banner "Creating Virtual Cloud Network"

VCN_ID=$(oci network vcn list \
    --compartment-id "$COMPARTMENT_ID" \
    --display-name "${STACK_NAME}-vcn" \
    --query 'data[0].id' --raw-output 2>/dev/null || true)

if [[ -z "$VCN_ID" || "$VCN_ID" == "null" ]]; then
    VCN_ID=$(oci network vcn create \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "${STACK_NAME}-vcn" \
        --cidr-block "10.0.0.0/16" \
        --dns-label "nsemcx" \
        --wait-for-state AVAILABLE \
        --query 'data.id' --raw-output)
    success "VCN created: $VCN_ID"
else
    info "Reusing existing VCN: $VCN_ID"
fi

# Internet Gateway
IGW_ID=$(oci network internet-gateway list \
    --compartment-id "$COMPARTMENT_ID" \
    --vcn-id "$VCN_ID" \
    --query 'data[0].id' --raw-output 2>/dev/null || true)

if [[ -z "$IGW_ID" || "$IGW_ID" == "null" ]]; then
    IGW_ID=$(oci network internet-gateway create \
        --compartment-id "$COMPARTMENT_ID" \
        --vcn-id "$VCN_ID" \
        --display-name "${STACK_NAME}-igw" \
        --is-enabled true \
        --wait-for-state AVAILABLE \
        --query 'data.id' --raw-output)
    success "Internet Gateway created: $IGW_ID"
fi

# Route Table
RT_ID=$(oci network route-table list \
    --compartment-id "$COMPARTMENT_ID" \
    --vcn-id "$VCN_ID" \
    --display-name "${STACK_NAME}-rt" \
    --query 'data[0].id' --raw-output 2>/dev/null || true)

if [[ -z "$RT_ID" || "$RT_ID" == "null" ]]; then
    RT_ID=$(oci network route-table create \
        --compartment-id "$COMPARTMENT_ID" \
        --vcn-id "$VCN_ID" \
        --display-name "${STACK_NAME}-rt" \
        --route-rules "[{\"destination\": \"0.0.0.0/0\", \"networkEntityId\": \"${IGW_ID}\", \"destinationType\": \"CIDR_BLOCK\"}]" \
        --wait-for-state AVAILABLE \
        --query 'data.id' --raw-output)
    success "Route Table created: $RT_ID"
fi

# Security List — open required ports
banner "Configuring Security List (firewall rules)"

SL_ID=$(oci network security-list list \
    --compartment-id "$COMPARTMENT_ID" \
    --vcn-id "$VCN_ID" \
    --display-name "${STACK_NAME}-sl" \
    --query 'data[0].id' --raw-output 2>/dev/null || true)

INGRESS_RULES='[
  {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":22,"max":22}}},
  {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":80,"max":80}}},
  {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":443,"max":443}}},
  {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":3000,"max":3000}}},
  {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":8080,"max":8080}}},
  {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":9092,"max":9092}}},
  {"source":"0.0.0.0/0","protocol":"1","isStateless":false,"icmpOptions":{"type":3,"code":4}},
  {"source":"0.0.0.0/0","protocol":"1","isStateless":false,"icmpOptions":{"type":3}}
]'

EGRESS_RULES='[{"destination":"0.0.0.0/0","protocol":"all","isStateless":false}]'

if [[ -z "$SL_ID" || "$SL_ID" == "null" ]]; then
    SL_ID=$(oci network security-list create \
        --compartment-id "$COMPARTMENT_ID" \
        --vcn-id "$VCN_ID" \
        --display-name "${STACK_NAME}-sl" \
        --ingress-security-rules "$INGRESS_RULES" \
        --egress-security-rules "$EGRESS_RULES" \
        --wait-for-state AVAILABLE \
        --query 'data.id' --raw-output)
    success "Security List created: $SL_ID"
else
    oci network security-list update \
        --security-list-id "$SL_ID" \
        --ingress-security-rules "$INGRESS_RULES" \
        --egress-security-rules "$EGRESS_RULES" \
        --force &>/dev/null
    info "Security List updated: $SL_ID"
fi

# Public Subnet
SUBNET_ID=$(oci network subnet list \
    --compartment-id "$COMPARTMENT_ID" \
    --vcn-id "$VCN_ID" \
    --display-name "${STACK_NAME}-subnet" \
    --query 'data[0].id' --raw-output 2>/dev/null || true)

if [[ -z "$SUBNET_ID" || "$SUBNET_ID" == "null" ]]; then
    SUBNET_ID=$(oci network subnet create \
        --compartment-id "$COMPARTMENT_ID" \
        --vcn-id "$VCN_ID" \
        --display-name "${STACK_NAME}-subnet" \
        --cidr-block "10.0.1.0/24" \
        --route-table-id "$RT_ID" \
        --security-list-ids "[\"$SL_ID\"]" \
        --dns-label "public" \
        --prohibit-public-ip-on-vnic false \
        --wait-for-state AVAILABLE \
        --query 'data.id' --raw-output)
    success "Public subnet created: $SUBNET_ID"
else
    info "Reusing existing subnet: $SUBNET_ID"
fi

# ─── Resolve OS image ─────────────────────────────────────────────────────────
banner "Resolving Ubuntu 22.04 aarch64 image"

IMAGE_ID=$(oci compute image list \
    --compartment-id "$COMPARTMENT_ID" \
    --operating-system "$OS_IMAGE_OS" \
    --operating-system-version "$OS_IMAGE_VERSION" \
    --shape "$INSTANCE_SHAPE" \
    --sort-by TIMECREATED \
    --sort-order DESC \
    --query 'data[0].id' --raw-output)

[[ -n "$IMAGE_ID" && "$IMAGE_ID" != "null" ]] || error "No Ubuntu 22.04 image found for shape $INSTANCE_SHAPE in $REGION."
success "Image resolved: $IMAGE_ID"

# ─── Provision instances ──────────────────────────────────────────────────────
banner "Provisioning $INSTANCE_COUNT Ampere A1 instances"

INSTANCE_IDS=()
PUBLIC_IPS=()

for i in $(seq 1 $INSTANCE_COUNT); do
    INSTANCE_NAME="${STACK_NAME}-node-${i}"
    info "Checking for existing instance: $INSTANCE_NAME"

    EXISTING_ID=$(oci compute instance list \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "$INSTANCE_NAME" \
        --lifecycle-state RUNNING \
        --query 'data[0].id' --raw-output 2>/dev/null || true)

    if [[ -n "$EXISTING_ID" && "$EXISTING_ID" != "null" ]]; then
        info "Instance already exists: $EXISTING_ID"
        INSTANCE_ID="$EXISTING_ID"
    else
        info "Creating instance $INSTANCE_NAME (${INSTANCE_OCPUS} OCPU, ${INSTANCE_MEMORY_GB} GB RAM) ..."

        CLOUD_INIT_B64=$(base64 -w0 <<'CLOUD_INIT'
#!/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

# Update and install Docker
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release apt-transport-https

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable docker
systemctl start docker
usermod -aG docker ubuntu

# Disable UFW to avoid conflicts with Docker iptables rules
ufw disable || true

# Increase system limits for Kafka and PostgreSQL
cat >> /etc/sysctl.conf <<EOF
vm.max_map_count=262144
net.core.somaxconn=65535
net.ipv4.tcp_max_syn_backlog=65535
EOF
sysctl -p

echo "Cloud-init complete: Docker installed and configured"
CLOUD_INIT
)

        INSTANCE_ID=$(oci compute instance launch \
            --compartment-id "$COMPARTMENT_ID" \
            --display-name "$INSTANCE_NAME" \
            --availability-domain "$(oci iam availability-domain list \
                --compartment-id "$COMPARTMENT_ID" \
                --query "data[$((i-1))].name" --raw-output 2>/dev/null || \
                oci iam availability-domain list \
                --compartment-id "$COMPARTMENT_ID" \
                --query 'data[0].name' --raw-output)" \
            --shape "$INSTANCE_SHAPE" \
            --shape-config "{\"ocpus\": ${INSTANCE_OCPUS}, \"memoryInGBs\": ${INSTANCE_MEMORY_GB}}" \
            --subnet-id "$SUBNET_ID" \
            --image-id "$IMAGE_ID" \
            --assign-public-ip true \
            --ssh-authorized-keys-file "$SSH_PUB_KEY_PATH" \
            --user-data "$CLOUD_INIT_B64" \
            --wait-for-state RUNNING \
            --query 'data.id' --raw-output)

        success "Instance $i created: $INSTANCE_ID"
    fi

    INSTANCE_IDS+=("$INSTANCE_ID")
done

# ─── Collect public IPs ───────────────────────────────────────────────────────
banner "Collecting public IP addresses"

for INSTANCE_ID in "${INSTANCE_IDS[@]}"; do
    PUBLIC_IP=$(oci compute instance list-vnics \
        --instance-id "$INSTANCE_ID" \
        --query 'data[0]."public-ip"' --raw-output)
    PUBLIC_IPS+=("$PUBLIC_IP")
    success "Instance $INSTANCE_ID → $PUBLIC_IP"
done

PRIMARY_IP="${PUBLIC_IPS[0]}"

# ─── Wait for SSH availability ────────────────────────────────────────────────
banner "Waiting for SSH on primary instance ($PRIMARY_IP)"

MAX_WAIT=300
ELAPSED=0
until ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 \
    -i "$SSH_KEY_PATH" "ubuntu@$PRIMARY_IP" "echo ready" 2>/dev/null; do
    echo -n "."
    sleep 10
    ELAPSED=$((ELAPSED + 10))
    [[ $ELAPSED -lt $MAX_WAIT ]] || error "SSH not available after ${MAX_WAIT}s on $PRIMARY_IP"
done
echo ""
success "SSH is available on $PRIMARY_IP"

# ─── Copy project files ───────────────────────────────────────────────────────
banner "Copying project files to primary instance"

REMOTE_DIR="/home/ubuntu/nse-mcx-trends"

ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "ubuntu@$PRIMARY_IP" \
    "mkdir -p $REMOTE_DIR/infrastructure/docker $REMOTE_DIR/infrastructure/airflow"

scp -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" \
    "$PROJECT_DIR/docker-compose.yml" \
    "$PROJECT_DIR/.env" \
    "ubuntu@$PRIMARY_IP:$REMOTE_DIR/"

scp -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" -r \
    "$PROJECT_DIR/infrastructure/docker" \
    "ubuntu@$PRIMARY_IP:$REMOTE_DIR/infrastructure/"

success "Files copied to $PRIMARY_IP:$REMOTE_DIR"

# ─── Start services ───────────────────────────────────────────────────────────
banner "Starting Docker services on primary instance"

ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "ubuntu@$PRIMARY_IP" bash -s <<REMOTE_SCRIPT
set -euo pipefail
cd $REMOTE_DIR

echo "Pulling Docker images ..."
docker compose pull --quiet

echo "Starting infrastructure services ..."
docker compose up -d postgres redis zookeeper kafka

echo "Waiting 30s for Kafka to be healthy ..."
sleep 30

echo "Starting remaining services ..."
docker compose up -d

echo "All services started."
docker compose ps
REMOTE_SCRIPT

success "Docker services started on $PRIMARY_IP"

# ─── Pull Ollama model ────────────────────────────────────────────────────────
banner "Pulling Ollama model: $OLLAMA_MODEL"

ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "ubuntu@$PRIMARY_IP" bash -s <<REMOTE_SCRIPT
set -euo pipefail
echo "Waiting for Ollama to be ready ..."
for i in \$(seq 1 30); do
    if curl -sf http://localhost:11434/api/tags &>/dev/null; then
        echo "Ollama is ready."
        break
    fi
    sleep 5
done
echo "Pulling model: $OLLAMA_MODEL ..."
docker exec nse-ollama ollama pull $OLLAMA_MODEL
echo "Model pull complete."
docker exec nse-ollama ollama list
REMOTE_SCRIPT

success "Ollama model $OLLAMA_MODEL is ready"

# ─── Install nginx reverse proxy ──────────────────────────────────────────────
banner "Configuring nginx reverse proxy"

ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "ubuntu@$PRIMARY_IP" bash -s <<REMOTE_SCRIPT
set -euo pipefail
apt-get install -y nginx

cat > /etc/nginx/sites-available/nse-mcx <<'NGINX'
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    }

    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_read_timeout 3600s;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/nse-mcx /etc/nginx/sites-enabled/nse-mcx
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
echo "Nginx configured and reloaded."
REMOTE_SCRIPT

success "nginx reverse proxy configured"

# ─── Summary ──────────────────────────────────────────────────────────────────
banner "Deployment Complete"

echo -e "${GREEN}${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║          NSE-MCX-Trends — Oracle Cloud Deployment        ║"
echo "╠══════════════════════════════════════════════════════════╣"
for i in "${!PUBLIC_IPS[@]}"; do
    printf "║  Node %-2d: %-46s ║\n" "$((i+1))" "${PUBLIC_IPS[$i]}"
done
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Service URLs (via nginx on primary node):               ║"
printf "║    Frontend   : http://%-34s ║\n" "$PRIMARY_IP"
printf "║    Backend API: http://%-34s ║\n" "$PRIMARY_IP/api"
printf "║    pgAdmin    : http://%-34s ║\n" "$PRIMARY_IP:5050"
printf "║    Kafka UI   : http://%-34s ║\n" "$PRIMARY_IP:8090"
printf "║    Ollama     : http://%-34s ║\n" "$PRIMARY_IP:11434"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo "SSH access: ssh -i $SSH_KEY_PATH ubuntu@$PRIMARY_IP"
echo "Logs:       ssh -i $SSH_KEY_PATH ubuntu@$PRIMARY_IP 'cd nse-mcx-trends && docker compose logs -f'"
