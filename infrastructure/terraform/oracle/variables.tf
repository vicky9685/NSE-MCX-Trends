# =============================================================================
# Variables — Oracle Cloud Infrastructure Terraform
# =============================================================================

variable "compartment_id" {
  description = "OCID of the OCI compartment to deploy into"
  type        = string
  validation {
    condition     = can(regex("^ocid1\\.compartment\\.", var.compartment_id))
    error_message = "compartment_id must be a valid OCI compartment OCID starting with 'ocid1.compartment.'"
  }
}

variable "region" {
  description = "OCI region identifier"
  type        = string
  default     = "ap-mumbai-1"
  validation {
    condition = contains([
      "ap-mumbai-1", "ap-hyderabad-1", "ap-singapore-1",
      "us-ashburn-1", "us-phoenix-1", "eu-frankfurt-1",
      "uk-london-1", "ap-sydney-1", "ap-tokyo-1"
    ], var.region)
    error_message = "Region must be a valid OCI region identifier."
  }
}

variable "stack_name" {
  description = "Name prefix for all resources"
  type        = string
  default     = "nse-mcx-trends"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,28}[a-z0-9]$", var.stack_name))
    error_message = "stack_name must be 4-30 lowercase alphanumeric characters or hyphens."
  }
}

variable "environment" {
  description = "Deployment environment: production, staging, development"
  type        = string
  default     = "production"
  validation {
    condition     = contains(["production", "staging", "development"], var.environment)
    error_message = "environment must be one of: production, staging, development."
  }
}

variable "ssh_public_key" {
  description = "SSH public key content to authorize on instances (paste the full key string)"
  type        = string
  sensitive   = false
  validation {
    condition     = can(regex("^(ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp256)", var.ssh_public_key))
    error_message = "ssh_public_key must be a valid OpenSSH public key (ssh-rsa, ssh-ed25519, or ecdsa-sha2-nistp256)."
  }
}

variable "vcn_cidr" {
  description = "CIDR block for the Virtual Cloud Network"
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_cidr" {
  description = "CIDR block for the public subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "instance_count" {
  description = "Number of Ampere A1 instances to provision (max 2 for Free Tier with 4 OCPU total)"
  type        = number
  default     = 2
  validation {
    condition     = var.instance_count >= 1 && var.instance_count <= 4
    error_message = "instance_count must be between 1 and 4."
  }
}

variable "instance_ocpus" {
  description = "Number of OCPUs per instance (Free Tier: 4 total across all A1 instances)"
  type        = number
  default     = 2
  validation {
    condition     = var.instance_ocpus >= 1 && var.instance_ocpus <= 4
    error_message = "instance_ocpus must be between 1 and 4."
  }
}

variable "instance_memory_gb" {
  description = "Memory in GB per instance (Free Tier: 24 GB total across all A1 instances)"
  type        = number
  default     = 12
  validation {
    condition     = var.instance_memory_gb >= 1 && var.instance_memory_gb <= 24
    error_message = "instance_memory_gb must be between 1 and 24."
  }
}

variable "boot_volume_size_gb" {
  description = "Boot volume size in GB (Free Tier: up to 200 GB total across all volumes)"
  type        = number
  default     = 50
  validation {
    condition     = var.boot_volume_size_gb >= 50 && var.boot_volume_size_gb <= 200
    error_message = "boot_volume_size_gb must be between 50 and 200."
  }
}

# =============================================================================
# Optional: API Key Auth (for local Terraform execution outside OCI)
# Uncomment and set these if not using instance principal auth.
# =============================================================================

# variable "tenancy_ocid" {
#   description = "OCID of your OCI tenancy"
#   type        = string
#   default     = ""
# }

# variable "user_ocid" {
#   description = "OCID of the OCI user for Terraform API calls"
#   type        = string
#   default     = ""
# }

# variable "fingerprint" {
#   description = "Fingerprint of the API key"
#   type        = string
#   default     = ""
# }

# variable "private_key_path" {
#   description = "Path to the OCI API private key PEM file"
#   type        = string
#   default     = "~/.oci/oci_api_key.pem"
# }
