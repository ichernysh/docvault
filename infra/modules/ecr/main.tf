# One private repository per image. for_each over the list: a new image = one more name in the list.
resource "aws_ecr_repository" "this" {
  for_each = toset(var.repositories)

  # The "docvault/" prefix groups our images in the account.
  name = "docvault/${each.key}"

  # A tag can never be overwritten. We tag images with the git SHA, so a tag always means the same image.
  image_tag_mutability = "IMMUTABLE"

  # AWS scans every pushed image for known vulnerabilities.
  image_scanning_configuration {
    scan_on_push = true
  }

  # Refuse to delete a repository that still contains images.
  force_delete = false
}

# Keep only the newest images; storage is billed per GB, old images pile up quickly.
resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last ${var.keep_images} images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = var.keep_images
      }
      action = { type = "expire" }
    }]
  })
}
