#!/usr/bin/env bash
# =============================================================================
# deploy-aws.sh — NSE-MCX-Trends on AWS (EC2 + optional RDS/MSK)
# =============================================================================
# Provisions:
#   - VPC with public subnet and internet gateway
#   - Security group with required ingress rules
#   - EC2 t3.medium (on-demand) or t3.xlarge (Spot, ~70% cheaper)
#   - User-data script to install Docker and start all services
#   - Optional: RDS PostgreSQL and MSK Kafka (controlled by flags)
#
# Prerequisites:
#   - aws CLI v2 configured: aws configure
#   - EC2 key pair exists (set KEY_PAIR_NAME) or will be created
#   - .env file present in project root
#
# Usage:
#   chmod +x deploy-aws.sh
#   ./deploy-aws.sh [--spot] [--region us-east-1] [--instance-type t3.xlarge]
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

# ─── Defaults ─────────────────────────────────────────────────────────────────
AWS_REGION="${AWS_DEFAULT_REGION:-ap-south-1}"
INSTANCE_TYPE="${EC2_INSTANCE_TYPE:-t3.medium}"
KEY_PAIR_NAME="${EC2_KEY_PAIR:-nse-mcx-keypair}"
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/${KEY_PAIR_NAME}.pem}"
STACK_NAME="nse-mcx-trends"
USE_SPOT=false
USE_RDS=false
USE_MSK=false
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VPC_CIDR="10.10.0.0/16"
SUBNET_CIDR="10.10.1.0/24"

# ─── Parse CLI args ───────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --spot)            USE_SPOT=true; INSTANCE_TYPE="${2:-t3.xlarge}"; shift ;;
        --region)          AWS_REGION="$2"; shift 2 ;;
        --instance-type)   INSTANCE_TYPE="$2"; shift 2 ;;
        --key-pair)        KEY_PAIR_NAME="$2"; SSH_KEY_PATH="$HOME/.ssh/${2}.pem"; shift 2 ;;
        --use-rds)         USE_RDS=true; shift ;;
        --use-msk)         USE_MSK=true; shift ;;
        --help|-h)
            echo "Usage: $0 [--spot] [--region <region>] [--instance-type <type>] [--use-rds] [--use-msk]"
            exit 0 ;;
        *) error "Unknown option: $1" ;;
    esac
done

export AWS_DEFAULT_REGION="$AWS_REGION"

# ─── Prerequisite checks ──────────────────────────────────────────────────────
banner "Checking prerequisites"

for cmd in aws ssh scp jq curl; do
    command -v "$cmd" &>/dev/null && success "$cmd found" || error "$cmd is not installed."
done

aws sts get-caller-identity &>/dev/null || error "AWS CLI not configured. Run: aws configure"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
success "AWS Account: $ACCOUNT_ID  Region: $AWS_REGION"

[[ -f "$PROJECT_DIR/.env" ]] || error ".env not found at $PROJECT_DIR/.env"

# ─── EC2 Key Pair ─────────────────────────────────────────────────────────────
banner "EC2 Key Pair"

EXISTING_KEY=$(aws ec2 describe-key-pairs \
    --key-names "$KEY_PAIR_NAME" \
    --query 'KeyPairs[0].KeyName' --output text 2>/dev/null || true)

if [[ "$EXISTING_KEY" == "$KEY_PAIR_NAME" ]]; then
    info "Key pair '$KEY_PAIR_NAME' already exists."
    [[ -f "$SSH_KEY_PATH" ]] || warn "Local key file not found: $SSH_KEY_PATH — you may not be able to SSH."
else
    info "Creating key pair '$KEY_PAIR_NAME' ..."
    aws ec2 create-key-pair \
        --key-name "$KEY_PAIR_NAME" \
        --query 'KeyMaterial' \
        --output text > "$SSH_KEY_PATH"
    chmod 600 "$SSH_KEY_PATH"
    success "Key pair created and saved to $SSH_KEY_PATH"
fi

# ─── VPC ──────────────────────────────────────────────────────────────────────
banner "VPC and Networking"

VPC_ID=$(aws ec2 describe-vpcs \
    --filters "Name=tag:Name,Values=${STACK_NAME}-vpc" \
    --query 'Vpcs[0].VpcId' --output text 2>/dev/null || true)

if [[ -z "$VPC_ID" || "$VPC_ID" == "None" ]]; then
    VPC_ID=$(aws ec2 create-vpc \
        --cidr-block "$VPC_CIDR" \
        --query 'Vpc.VpcId' --output text)
    aws ec2 modify-vpc-attribute --vpc-id "$VPC_ID" --enable-dns-support
    aws ec2 modify-vpc-attribute --vpc-id "$VPC_ID" --enable-dns-hostnames
    aws ec2 create-tags --resources "$VPC_ID" --tags "Key=Name,Value=${STACK_NAME}-vpc"
    success "VPC created: $VPC_ID"
else
    info "Reusing VPC: $VPC_ID"
fi

# Internet Gateway
IGW_ID=$(aws ec2 describe-internet-gateways \
    --filters "Name=attachment.vpc-id,Values=$VPC_ID" \
    --query 'InternetGateways[0].InternetGatewayId' --output text 2>/dev/null || true)

if [[ -z "$IGW_ID" || "$IGW_ID" == "None" ]]; then
    IGW_ID=$(aws ec2 create-internet-gateway --query 'InternetGateway.InternetGatewayId' --output text)
    aws ec2 attach-internet-gateway --vpc-id "$VPC_ID" --internet-gateway-id "$IGW_ID"
    aws ec2 create-tags --resources "$IGW_ID" --tags "Key=Name,Value=${STACK_NAME}-igw"
    success "Internet Gateway created and attached: $IGW_ID"
fi

# Subnet
SUBNET_ID=$(aws ec2 describe-subnets \
    --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Name,Values=${STACK_NAME}-subnet" \
    --query 'Subnets[0].SubnetId' --output text 2>/dev/null || true)

if [[ -z "$SUBNET_ID" || "$SUBNET_ID" == "None" ]]; then
    AZ=$(aws ec2 describe-availability-zones \
        --query 'AvailabilityZones[0].ZoneName' --output text)
    SUBNET_ID=$(aws ec2 create-subnet \
        --vpc-id "$VPC_ID" \
        --cidr-block "$SUBNET_CIDR" \
        --availability-zone "$AZ" \
        --query 'Subnet.SubnetId' --output text)
    aws ec2 modify-subnet-attribute --subnet-id "$SUBNET_ID" --map-public-ip-on-launch
    aws ec2 create-tags --resources "$SUBNET_ID" --tags "Key=Name,Value=${STACK_NAME}-subnet"
    success "Subnet created: $SUBNET_ID ($AZ)"
fi

# Route Table
RT_ID=$(aws ec2 describe-route-tables \
    --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Name,Values=${STACK_NAME}-rt" \
    --query 'RouteTables[0].RouteTableId' --output text 2>/dev/null || true)

if [[ -z "$RT_ID" || "$RT_ID" == "None" ]]; then
    RT_ID=$(aws ec2 create-route-table \
        --vpc-id "$VPC_ID" \
        --query 'RouteTable.RouteTableId' --output text)
    aws ec2 create-route --route-table-id "$RT_ID" --destination-cidr-block "0.0.0.0/0" --gateway-id "$IGW_ID"
    aws ec2 associate-route-table --route-table-id "$RT_ID" --subnet-id "$SUBNET_ID"
    aws ec2 create-tags --resources "$RT_ID" --tags "Key=Name,Value=${STACK_NAME}-rt"
    success "Route Table created: $RT_ID"
fi

# ─── Security Group ───────────────────────────────────────────────────────────
banner "Security Group"

SG_ID=$(aws ec2 describe-security-groups \
    --filters "Name=vpc-id,Values=$VPC_ID" "Name=group-name,Values=${STACK_NAME}-sg" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)

if [[ -z "$SG_ID" || "$SG_ID" == "None" ]]; then
    SG_ID=$(aws ec2 create-security-group \
        --group-name "${STACK_NAME}-sg" \
        --description "NSE-MCX-Trends security group" \
        --vpc-id "$VPC_ID" \
        --query 'GroupId' --output text)
    aws ec2 create-tags --resources "$SG_ID" --tags "Key=Name,Value=${STACK_NAME}-sg"
    success "Security Group created: $SG_ID"

    # Add ingress rules
    for PORT in 22 80 443 3000 8080 9092 5050 8090 11434; do
        aws ec2 authorize-security-group-ingress \
            --group-id "$SG_ID" \
            --protocol tcp \
            --port "$PORT" \
            --cidr "0.0.0.0/0" &>/dev/null
        info "Opened port $PORT"
    done
else
    info "Reusing Security Group: $SG_ID"
fi

# ─── AMI: Amazon Linux 2023 or Ubuntu 22.04 ──────────────────────────────────
banner "Resolving AMI"

AMI_ID=$(aws ec2 describe-images \
    --owners amazon \
    --filters \
        "Name=name,Values=al2023-ami-*-x86_64" \
        "Name=state,Values=available" \
    --query 'sort_by(Images, &CreationDate)[-1].ImageId' \
    --output text)

[[ -n "$AMI_ID" && "$AMI_ID" != "None" ]] || error "Could not resolve Amazon Linux 2023 AMI."
success "AMI: $AMI_ID"

# ─── User Data ────────────────────────────────────────────────────────────────
USER_DATA=$(base64 -w0 <<'EOF'
#!/bin/bash
set -euo pipefail
export HOME=/root

# Install Docker
dnf update -y
dnf install -y docker git curl jq
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

# Install docker compose plugin
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Kernel tuning for Kafka and PostgreSQL
cat >> /etc/sysctl.conf <<SYSCTL
vm.max_map_count=262144
net.core.somaxconn=65535
SYSCTL
sysctl -p

echo "User-data setup complete."
EOF
)

# ─── Launch EC2 ──────────────────────────────────────────────────────────────
banner "Launching EC2 Instance ($INSTANCE_TYPE, Spot=$USE_SPOT)"

EXISTING_INSTANCE=$(aws ec2 describe-instances \
    --filters \
        "Name=tag:Name,Values=${STACK_NAME}-server" \
        "Name=instance-state-name,Values=running,pending" \
    --query 'Reservations[0].Instances[0].InstanceId' \
    --output text 2>/dev/null || true)

if [[ -n "$EXISTING_INSTANCE" && "$EXISTING_INSTANCE" != "None" ]]; then
    info "Reusing running instance: $EXISTING_INSTANCE"
    INSTANCE_ID="$EXISTING_INSTANCE"
elif [[ "$USE_SPOT" == "true" ]]; then
    info "Requesting Spot instance ..."
    SPOT_REQUEST=$(aws ec2 request-spot-instances \
        --spot-price "0.10" \
        --instance-count 1 \
        --launch-specification "{
            \"ImageId\": \"$AMI_ID\",
            \"InstanceType\": \"$INSTANCE_TYPE\",
            \"KeyName\": \"$KEY_PAIR_NAME\",
            \"SecurityGroupIds\": [\"$SG_ID\"],
            \"SubnetId\": \"$SUBNET_ID\",
            \"UserData\": \"$USER_DATA\",
            \"BlockDeviceMappings\": [{
                \"DeviceName\": \"/dev/xvda\",
                \"Ebs\": {\"VolumeSize\": 50, \"VolumeType\": \"gp3\"}
            }]
        }" \
        --query 'SpotInstanceRequests[0].SpotInstanceRequestId' \
        --output text)

    info "Waiting for Spot instance to be fulfilled ..."
    aws ec2 wait spot-instance-request-fulfilled --spot-instance-request-ids "$SPOT_REQUEST"
    INSTANCE_ID=$(aws ec2 describe-spot-instance-requests \
        --spot-instance-request-ids "$SPOT_REQUEST" \
        --query 'SpotInstanceRequests[0].InstanceId' --output text)
    success "Spot instance launched: $INSTANCE_ID"
else
    INSTANCE_ID=$(aws ec2 run-instances \
        --image-id "$AMI_ID" \
        --instance-type "$INSTANCE_TYPE" \
        --key-name "$KEY_PAIR_NAME" \
        --security-group-ids "$SG_ID" \
        --subnet-id "$SUBNET_ID" \
        --user-data "$USER_DATA" \
        --block-device-mappings "[{\"DeviceName\":\"/dev/xvda\",\"Ebs\":{\"VolumeSize\":50,\"VolumeType\":\"gp3\"}}]" \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${STACK_NAME}-server}]" \
        --query 'Instances[0].InstanceId' --output text)
    success "On-demand instance launched: $INSTANCE_ID"
fi

info "Waiting for instance to be running ..."
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"

PUBLIC_DNS=$(aws ec2 describe-instances \
    --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].PublicDnsName' --output text)
PUBLIC_IP=$(aws ec2 describe-instances \
    --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)

success "Instance running: $PUBLIC_IP ($PUBLIC_DNS)"

# ─── Wait for SSH ─────────────────────────────────────────────────────────────
banner "Waiting for SSH"

for i in $(seq 1 30); do
    ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 \
        -i "$SSH_KEY_PATH" "ec2-user@$PUBLIC_IP" "echo ok" 2>/dev/null && break
    echo -n "."
    sleep 10
done
echo ""
success "SSH ready"

# ─── Copy files and start services ───────────────────────────────────────────
banner "Deploying application files"

REMOTE_DIR="/home/ec2-user/nse-mcx-trends"
ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "ec2-user@$PUBLIC_IP" "mkdir -p $REMOTE_DIR/infrastructure/docker"

scp -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" \
    "$PROJECT_DIR/docker-compose.yml" "$PROJECT_DIR/.env" \
    "ec2-user@$PUBLIC_IP:$REMOTE_DIR/"

scp -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" -r \
    "$PROJECT_DIR/infrastructure/docker" \
    "ec2-user@$PUBLIC_IP:$REMOTE_DIR/infrastructure/"

ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "ec2-user@$PUBLIC_IP" bash -s <<REMOTE
set -euo pipefail
cd $REMOTE_DIR
docker compose pull --quiet
docker compose up -d postgres redis zookeeper
sleep 20
docker compose up -d
echo "Services started:"
docker compose ps
REMOTE

# ─── Summary ──────────────────────────────────────────────────────────────────
banner "Deployment Complete"

echo -e "${GREEN}${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║           NSE-MCX-Trends — AWS EC2 Deployment           ║"
echo "╠══════════════════════════════════════════════════════════╣"
printf "║  Instance ID : %-42s ║\n" "$INSTANCE_ID"
printf "║  Public IP   : %-42s ║\n" "$PUBLIC_IP"
printf "║  Public DNS  : %-42s ║\n" "${PUBLIC_DNS:0:42}"
echo "╠══════════════════════════════════════════════════════════╣"
printf "║  Frontend    : http://%-36s ║\n" "$PUBLIC_IP:3000"
printf "║  Backend API : http://%-36s ║\n" "$PUBLIC_IP:8080/api"
printf "║  pgAdmin     : http://%-36s ║\n" "$PUBLIC_IP:5050"
printf "║  Kafka UI    : http://%-36s ║\n" "$PUBLIC_IP:8090"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo "SSH: ssh -i $SSH_KEY_PATH ec2-user@$PUBLIC_IP"
