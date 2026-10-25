variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "frontend_bucket_name" {
  type = string
}

variable "domain" {
  type = string
}

variable "backend_image" {
  type = string
}

variable "admin_ip_ranges" {
  type = list(string)

  validation {
    condition     = length(var.admin_ip_ranges) > 0
    error_message = "At least one trusted admin IP range is required."
  }
}

variable "db_tier" {
  type    = string
  default = "db-custom-1-3840"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "jwt_secret_name" {
  type = string
}

variable "db_password_secret_name" {
  type = string
}

variable "mail_host" {
  type = string
}

variable "mail_username" {
  type = string
}

variable "mail_password_secret_name" {
  type = string
}

variable "mail_from" {
  type = string
}

variable "protect_database" {
  type    = bool
  default = true
}
