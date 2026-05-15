# =============================================================================
# Terraform — Oracle Cloud Infrastructure (OCI) Free Tier
# NSE-MCX-Trends: 2× Always Free Ampere A1 instances
# =============================================================================
# Auth: Instance Principal (no user credentials required when running on OCI)
# Alternatively, use API key auth for local development (see providers.tf)
#
# Usage:
#   cd infrastructure/terraform/oracle
#   terraform init
#   terraform plan -var="compartment_id=ocid1.compartment.oc1...."
#   terraform apply -var="compartment_id=ocid1.compartment.oc1...."
# =============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }

  # Uncomment to use remote state (recommended for production)
  # backend "s3" {
  #   bucket   = "your-terraform-state-bucket"
  #   key      = "nse-mcx/oracle/terraform.tfstate"
  #   region   = "us-east-1"
  #   endpoint = "https://<namespace>.compat.objectstorage.ap-mumbai-1.oraclecloud.com"
  #   skip_region_validation      = true
  #   skip_credentials_validation = true
  #   skip_metadata_api_check     = true
  #   force_path_style            = true
  # }
}

# =============================================================================
# Provider Configuration
# Instance principal auth: works when Terraform runs on an OCI instance.
# For local dev: set TF_VAR_use_instance_principal=false and configure
# tenancy_ocid, user_ocid, fingerprint, private_key_path in variables.
# =============================================================================
provider "oci" {
  region = var.region

  # Instance principal auth — no user credentials needed.
  # Comment out auth = "InstancePrincipal" and configure below for local dev:
  # tenancy_ocid     = var.tenancy_ocid
  # user_ocid        = var.user_ocid
  # fingerprint      = var.fingerprint
  # private_key_path = var.private_key_path
  auth = "InstancePrincipal"
}

# =============================================================================
# Data Sources
# =============================================================================

data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_id
}

# Ubuntu 22.04 aarch64 (Ampere A1 compatible) — latest image
data "oci_core_images" "ubuntu_aarch64" {
  compartment_id           = var.compartment_id
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "22.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"

  filter {
    name   = "display_name"
    values = [".*Ubuntu-22\\.04.*aarch64.*"]
    regex  = true
  }
}

locals {
  # Use the most recent Ubuntu 22.04 aarch64 image
  ubuntu_image_id = data.oci_core_images.ubuntu_aarch64.images[0].id

  # Cloud-init script: installs Docker and applies kernel tuning
  cloud_init_userdata = base64encode(<<-USERDATA
    #!/bin/bash
    set -euo pipefail
    export DEBIAN_FRONTEND=noninteractive
    export HOME=/root

    # ── System update ───────────────────────────────────────────────────────
    apt-get update -y
    apt-get upgrade -y

    # ── Docker installation ─────────────────────────────────────────────────
    apt-get install -y ca-certificates curl gnupg lsb-release apt-transport-https

    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list

    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io \
        docker-buildx-plugin docker-compose-plugin

    systemctl enable docker
    systemctl start docker
    usermod -aG docker ubuntu

    # ── Kernel tuning ───────────────────────────────────────────────────────
    cat >> /etc/sysctl.conf <<EOF
    vm.max_map_count=262144
    net.core.somaxconn=65535
    net.ipv4.tcp_max_syn_backlog=65535
    net.ipv4.ip_local_port_range=1024 65535
    EOF
    sysctl -p

    # ── UFW: allow required ports ────────────────────────────────────────────
    ufw --force reset
    ufw default deny incoming
    ufw default allow outgoing
    for PORT in 22 80 443 3000 8080 8090 9092 5050 11434; do
        ufw allow $PORT/tcp
    done
    ufw --force enable

    # ── Create app directory ─────────────────────────────────────────────────
    mkdir -p /home/ubuntu/nse-mcx-trends
    chown ubuntu:ubuntu /home/ubuntu/nse-mcx-trends

    echo "Cloud-init complete: Docker ${docker --version} installed."
    USERDATA
  )

  # Common tags for all resources
  common_tags = {
    project     = "nse-mcx-trends"
    environment = var.environment
    managed_by  = "terraform"
    owner       = "trading-ops"
  }
}

# =============================================================================
# VCN (Virtual Cloud Network)
# =============================================================================

resource "oci_core_vcn" "trading_vcn" {
  compartment_id = var.compartment_id
  display_name   = "${var.stack_name}-vcn"
  cidr_blocks    = [var.vcn_cidr]
  dns_label      = "nsemcx"

  freeform_tags = local.common_tags
}

# =============================================================================
# Internet Gateway
# =============================================================================

resource "oci_core_internet_gateway" "trading_igw" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trading_vcn.id
  display_name   = "${var.stack_name}-igw"
  enabled        = true

  freeform_tags = local.common_tags
}

# =============================================================================
# Route Table
# =============================================================================

resource "oci_core_route_table" "trading_rt" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trading_vcn.id
  display_name   = "${var.stack_name}-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.trading_igw.id
  }

  freeform_tags = local.common_tags
}

# =============================================================================
# Security List (Firewall Rules)
# =============================================================================

resource "oci_core_security_list" "trading_sl" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.trading_vcn.id
  display_name   = "${var.stack_name}-sl"

  # ── Egress: allow all outbound ────────────────────────────────────────────
  egress_security_rules {
    destination      = "0.0.0.0/0"
    protocol         = "all"
    stateless        = false
    description      = "Allow all outbound traffic"
  }

  # ── Ingress: SSH ──────────────────────────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"   # TCP
    source      = "0.0.0.0/0"
    stateless   = false
    description = "SSH access"
    tcp_options {
      min = 22
      max = 22
    }
  }

  # ── Ingress: HTTP ─────────────────────────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
    description = "HTTP"
    tcp_options {
      min = 80
      max = 80
    }
  }

  # ── Ingress: HTTPS ────────────────────────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
    description = "HTTPS"
    tcp_options {
      min = 443
      max = 443
    }
  }

  # ── Ingress: Frontend (port 3000) ─────────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
    description = "React frontend"
    tcp_options {
      min = 3000
      max = 3000
    }
  }

  # ── Ingress: Backend API (port 8080) ──────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
    description = "Spring Boot backend API"
    tcp_options {
      min = 8080
      max = 8080
    }
  }

  # ── Ingress: Kafka (port 9092) ────────────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
    description = "Kafka broker"
    tcp_options {
      min = 9092
      max = 9092
    }
  }

  # ── Ingress: Kafka UI (port 8090) ─────────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
    description = "Kafka UI"
    tcp_options {
      min = 8090
      max = 8090
    }
  }

  # ── Ingress: pgAdmin (port 5050) ──────────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
    description = "pgAdmin"
    tcp_options {
      min = 5050
      max = 5050
    }
  }

  # ── Ingress: Ollama (port 11434) ──────────────────────────────────────────
  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
    description = "Ollama LLM API"
    tcp_options {
      min = 11434
      max = 11434
    }
  }

  # ── Ingress: ICMP path MTU discovery ─────────────────────────────────────
  ingress_security_rules {
    protocol    = "1"   # ICMP
    source      = "0.0.0.0/0"
    stateless   = false
    description = "ICMP path MTU discovery"
    icmp_options {
      type = 3
      code = 4
    }
  }

  # ── Ingress: ICMP from VCN ────────────────────────────────────────────────
  ingress_security_rules {
    protocol    = "1"
    source      = var.vcn_cidr
    stateless   = false
    description = "ICMP from VCN"
    icmp_options {
      type = 3
    }
  }

  freeform_tags = local.common_tags
}

# =============================================================================
# Public Subnet
# =============================================================================

resource "oci_core_subnet" "trading_subnet" {
  compartment_id             = var.compartment_id
  vcn_id                     = oci_core_vcn.trading_vcn.id
  display_name               = "${var.stack_name}-public-subnet"
  cidr_block                 = var.subnet_cidr
  route_table_id             = oci_core_route_table.trading_rt.id
  security_list_ids          = [oci_core_security_list.trading_sl.id]
  dns_label                  = "public"
  prohibit_public_ip_on_vnic = false   # Allow public IPs

  freeform_tags = local.common_tags
}

# =============================================================================
# Always Free Ampere A1 Instances
# Each: 2 OCPU, 12 GB RAM — total: 4 OCPU, 24 GB (OCI Free Tier maximum)
# =============================================================================

resource "oci_core_instance" "trading_nodes" {
  count               = var.instance_count
  compartment_id      = var.compartment_id
  display_name        = "${var.stack_name}-node-${count.index + 1}"
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[count.index % length(data.oci_identity_availability_domains.ads.availability_domains)].name

  shape = "VM.Standard.A1.Flex"

  shape_config {
    ocpus         = var.instance_ocpus
    memory_in_gbs = var.instance_memory_gb
  }

  source_details {
    source_type             = "image"
    source_id               = local.ubuntu_image_id
    boot_volume_size_in_gbs = var.boot_volume_size_gb
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.trading_subnet.id
    display_name     = "${var.stack_name}-vnic-${count.index + 1}"
    assign_public_ip = true
    hostname_label   = "${var.stack_name}-node-${count.index + 1}"
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = local.cloud_init_userdata
  }

  # Instance principal — no user IAM credentials needed
  # The instance itself has an identity that can be granted permissions
  # via dynamic groups and policies on the resource principal.

  freeform_tags = merge(local.common_tags, {
    node_index = tostring(count.index + 1)
    role       = count.index == 0 ? "primary" : "secondary"
  })

  timeouts {
    create = "20m"
    update = "15m"
    delete = "10m"
  }
}

# =============================================================================
# Outputs
# =============================================================================

output "vcn_id" {
  description = "OCID of the VCN"
  value       = oci_core_vcn.trading_vcn.id
}

output "subnet_id" {
  description = "OCID of the public subnet"
  value       = oci_core_subnet.trading_subnet.id
}

output "instance_ocids" {
  description = "OCIDs of all provisioned instances"
  value       = oci_core_instance.trading_nodes[*].id
}

output "instance_public_ips" {
  description = "Public IP addresses of all instances"
  value       = oci_core_instance.trading_nodes[*].public_ip
}

output "instance_private_ips" {
  description = "Private IP addresses of all instances"
  value       = oci_core_instance.trading_nodes[*].private_ip
}

output "primary_public_ip" {
  description = "Public IP of the primary (node-1) instance"
  value       = oci_core_instance.trading_nodes[0].public_ip
}

output "frontend_url" {
  description = "Frontend URL on primary instance"
  value       = "http://${oci_core_instance.trading_nodes[0].public_ip}:3000"
}

output "backend_url" {
  description = "Backend API URL on primary instance"
  value       = "http://${oci_core_instance.trading_nodes[0].public_ip}:8080"
}

output "ssh_commands" {
  description = "SSH commands to connect to each instance"
  value = [
    for i, instance in oci_core_instance.trading_nodes :
    "ssh -i <your-private-key> ubuntu@${instance.public_ip}"
  ]
}
