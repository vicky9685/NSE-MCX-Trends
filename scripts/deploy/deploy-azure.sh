#!/usr/bin/env bash
# =============================================================================
# deploy-azure.sh — NSE-MCX-Trends on Microsoft Azure
# =============================================================================
# Provisions:
#   - Resource Group
#   - Virtual Network + subnet
#   - Network Security Group with required port rules
#   - Public IP (static)
#   - NIC attached to NSG
#   - Ubuntu 22.04 VM: Standard_B2s (2 vCPU, 4 GB) or Standard_D2s_v3 (2 vCPU, 8 GB)
#   - Docker + docker compose installed via custom-script extension
#
# Prerequisites:
#   - az CLI installed and logged in: az login
#   - .env file present in project root
#
# Usage:
#   chmod +x deploy-azure.sh
#   ./deploy-azure.sh [--resource-group nse-mcx-rg] [--location eastus] [--vm-size Standard_D2s_v3]
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
RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-nse-mcx-trends-rg}"
LOCATION="${AZURE_LOCATION:-centralindia}"
VM_SIZE="${AZURE_VM_SIZE:-Standard_B2s}"
VM_NAME="nse-mcx-vm"
VNET_NAME="nse-mcx-vnet"
SUBNET_NAME="nse-mcx-subnet"
NSG_NAME="nse-mcx-nsg"
PIP_NAME="nse-mcx-pip"
NIC_NAME="nse-mcx-nic"
ADMIN_USER="azureuser"
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/id_rsa}"
SSH_PUB_KEY_PATH="${SSH_KEY_PATH}.pub"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OS_DISK_SIZE_GB=64

# ─── Parse CLI args ───────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --resource-group) RESOURCE_GROUP="$2"; shift 2 ;;
        --location)       LOCATION="$2"; shift 2 ;;
        --vm-size)        VM_SIZE="$2"; shift 2 ;;
        --ssh-key)        SSH_KEY_PATH="$2"; SSH_PUB_KEY_PATH="${2}.pub"; shift 2 ;;
        --help|-h)
            echo "Usage: $0 [--resource-group <name>] [--location <region>] [--vm-size <size>]"
            exit 0 ;;
        *) error "Unknown option: $1" ;;
    esac
done

# ─── Prereq checks ────────────────────────────────────────────────────────────
banner "Checking prerequisites"

for cmd in az ssh scp jq; do
    command -v "$cmd" &>/dev/null && success "$cmd found" || error "$cmd not installed."
done

az account show &>/dev/null || error "Not logged in. Run: az login"
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
success "Azure Subscription: $SUBSCRIPTION_ID  Location: $LOCATION"

[[ -f "$PROJECT_DIR/.env" ]] || error ".env not found at $PROJECT_DIR/.env"
[[ -f "$SSH_PUB_KEY_PATH" ]] || error "SSH public key not found: $SSH_PUB_KEY_PATH"

# ─── Resource Group ───────────────────────────────────────────────────────────
banner "Resource Group"

if az group show --name "$RESOURCE_GROUP" &>/dev/null; then
    info "Resource group exists: $RESOURCE_GROUP"
else
    az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --output none
    success "Resource group created: $RESOURCE_GROUP"
fi

# ─── Virtual Network ──────────────────────────────────────────────────────────
banner "Virtual Network and Subnet"

if az network vnet show --resource-group "$RESOURCE_GROUP" --name "$VNET_NAME" &>/dev/null; then
    info "VNet exists: $VNET_NAME"
else
    az network vnet create \
        --resource-group "$RESOURCE_GROUP" \
        --name "$VNET_NAME" \
        --address-prefix "10.20.0.0/16" \
        --subnet-name "$SUBNET_NAME" \
        --subnet-prefix "10.20.1.0/24" \
        --output none
    success "VNet and subnet created: $VNET_NAME"
fi

# ─── Network Security Group ───────────────────────────────────────────────────
banner "Network Security Group"

if az network nsg show --resource-group "$RESOURCE_GROUP" --name "$NSG_NAME" &>/dev/null; then
    info "NSG exists: $NSG_NAME"
else
    az network nsg create \
        --resource-group "$RESOURCE_GROUP" \
        --name "$NSG_NAME" \
        --output none
    success "NSG created: $NSG_NAME"
fi

# Add security rules
declare -A RULES=(
    ["AllowSSH"]="22"
    ["AllowHTTP"]="80"
    ["AllowHTTPS"]="443"
    ["AllowFrontend"]="3000"
    ["AllowBackend"]="8080"
    ["AllowKafka"]="9092"
    ["AllowPgAdmin"]="5050"
    ["AllowKafkaUI"]="8090"
)

PRIORITY=100
for RULE_NAME in "${!RULES[@]}"; do
    PORT="${RULES[$RULE_NAME]}"
    az network nsg rule show \
        --resource-group "$RESOURCE_GROUP" \
        --nsg-name "$NSG_NAME" \
        --name "$RULE_NAME" &>/dev/null || \
    az network nsg rule create \
        --resource-group "$RESOURCE_GROUP" \
        --nsg-name "$NSG_NAME" \
        --name "$RULE_NAME" \
        --priority "$PRIORITY" \
        --protocol Tcp \
        --source-address-prefixes "*" \
        --source-port-ranges "*" \
        --destination-address-prefixes "*" \
        --destination-port-ranges "$PORT" \
        --access Allow \
        --direction Inbound \
        --output none
    info "NSG rule: $RULE_NAME → port $PORT"
    PRIORITY=$((PRIORITY + 10))
done
success "NSG rules configured"

# ─── Public IP ────────────────────────────────────────────────────────────────
banner "Public IP"

if az network public-ip show --resource-group "$RESOURCE_GROUP" --name "$PIP_NAME" &>/dev/null; then
    info "Public IP exists: $PIP_NAME"
else
    az network public-ip create \
        --resource-group "$RESOURCE_GROUP" \
        --name "$PIP_NAME" \
        --allocation-method Static \
        --sku Standard \
        --output none
    success "Static public IP created: $PIP_NAME"
fi

# ─── NIC ──────────────────────────────────────────────────────────────────────
banner "Network Interface"

if az network nic show --resource-group "$RESOURCE_GROUP" --name "$NIC_NAME" &>/dev/null; then
    info "NIC exists: $NIC_NAME"
else
    az network nic create \
        --resource-group "$RESOURCE_GROUP" \
        --name "$NIC_NAME" \
        --vnet-name "$VNET_NAME" \
        --subnet "$SUBNET_NAME" \
        --public-ip-address "$PIP_NAME" \
        --network-security-group "$NSG_NAME" \
        --output none
    success "NIC created: $NIC_NAME"
fi

# ─── VM ───────────────────────────────────────────────────────────────────────
banner "Virtual Machine ($VM_SIZE)"

if az vm show --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" &>/dev/null; then
    info "VM already exists: $VM_NAME — ensuring it's running ..."
    az vm start --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --output none
else
    SSH_PUB_KEY_CONTENT=$(cat "$SSH_PUB_KEY_PATH")
    az vm create \
        --resource-group "$RESOURCE_GROUP" \
        --name "$VM_NAME" \
        --nics "$NIC_NAME" \
        --image Ubuntu2204 \
        --size "$VM_SIZE" \
        --os-disk-size-gb "$OS_DISK_SIZE_GB" \
        --admin-username "$ADMIN_USER" \
        --ssh-key-values "$SSH_PUB_KEY_CONTENT" \
        --no-wait \
        --output none
    info "VM creation started, waiting for running state ..."
    az vm wait --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --created --output none
    success "VM created: $VM_NAME"
fi

# ─── Get public IP ────────────────────────────────────────────────────────────
PUBLIC_IP=$(az network public-ip show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$PIP_NAME" \
    --query ipAddress -o tsv)

success "Public IP: $PUBLIC_IP"

# ─── Install Docker via Custom Script Extension ───────────────────────────────
banner "Installing Docker via VM Extension"

DOCKER_INSTALL_SCRIPT='#!/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable docker && systemctl start docker
usermod -aG docker azureuser
cat >> /etc/sysctl.conf <<EOF
vm.max_map_count=262144
net.core.somaxconn=65535
EOF
sysctl -p
echo "Docker installation complete."'

# Write to temp file and upload
TMPFILE=$(mktemp /tmp/docker-install.XXXXXX.sh)
echo "$DOCKER_INSTALL_SCRIPT" > "$TMPFILE"

az vm extension set \
    --resource-group "$RESOURCE_GROUP" \
    --vm-name "$VM_NAME" \
    --name customScript \
    --publisher Microsoft.Azure.Extensions \
    --settings "{\"commandToExecute\": \"bash -c '$(base64 -w0 "$TMPFILE" | base64 -d | bash)'\"}" \
    --output none 2>/dev/null || {
    # Fallback: SSH directly
    warn "Extension failed, installing Docker via SSH..."
    sleep 30
    ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "$ADMIN_USER@$PUBLIC_IP" bash -s < "$TMPFILE"
}
rm -f "$TMPFILE"

success "Docker installed"

# ─── Wait for SSH and Docker ──────────────────────────────────────────────────
banner "Waiting for SSH"

for i in $(seq 1 24); do
    ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 \
        -i "$SSH_KEY_PATH" "$ADMIN_USER@$PUBLIC_IP" "docker info &>/dev/null && echo ok" 2>/dev/null && break
    echo -n "."
    sleep 10
done
echo ""
success "SSH and Docker ready"

# ─── Deploy files ─────────────────────────────────────────────────────────────
banner "Deploying application files"

REMOTE_DIR="/home/$ADMIN_USER/nse-mcx-trends"
ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "$ADMIN_USER@$PUBLIC_IP" \
    "mkdir -p $REMOTE_DIR/infrastructure/docker"

scp -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" \
    "$PROJECT_DIR/docker-compose.yml" "$PROJECT_DIR/.env" \
    "$ADMIN_USER@$PUBLIC_IP:$REMOTE_DIR/"

scp -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" -r \
    "$PROJECT_DIR/infrastructure/docker" \
    "$ADMIN_USER@$PUBLIC_IP:$REMOTE_DIR/infrastructure/"

ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "$ADMIN_USER@$PUBLIC_IP" bash -s <<REMOTE
set -euo pipefail
cd $REMOTE_DIR
docker compose pull --quiet
docker compose up -d postgres redis zookeeper kafka
sleep 25
docker compose up -d
docker compose ps
REMOTE

# ─── Summary ──────────────────────────────────────────────────────────────────
banner "Deployment Complete"

echo -e "${GREEN}${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║          NSE-MCX-Trends — Azure VM Deployment            ║"
echo "╠══════════════════════════════════════════════════════════╣"
printf "║  Resource Group : %-40s ║\n" "$RESOURCE_GROUP"
printf "║  VM Name        : %-40s ║\n" "$VM_NAME"
printf "║  VM Size        : %-40s ║\n" "$VM_SIZE"
printf "║  Location       : %-40s ║\n" "$LOCATION"
printf "║  Public IP      : %-40s ║\n" "$PUBLIC_IP"
echo "╠══════════════════════════════════════════════════════════╣"
printf "║  Frontend       : http://%-34s ║\n" "$PUBLIC_IP:3000"
printf "║  Backend API    : http://%-34s ║\n" "$PUBLIC_IP:8080/api"
printf "║  pgAdmin        : http://%-34s ║\n" "$PUBLIC_IP:5050"
printf "║  Kafka UI       : http://%-34s ║\n" "$PUBLIC_IP:8090"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo "SSH: ssh -i $SSH_KEY_PATH $ADMIN_USER@$PUBLIC_IP"
echo ""
echo "To delete everything: az group delete --name $RESOURCE_GROUP --yes --no-wait"
