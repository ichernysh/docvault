provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]

  default_tags {
    tags = {
      project = "docvault"
    }
  }
}

module "network" {
  source = "../../modules/network"

  azs = ["${var.region}a", "${var.region}b"]
}

module "ecr" {
  source = "../../modules/ecr"

  repositories = ["docs-api", "keycloak"]
}
