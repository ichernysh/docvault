variable "repositories" {
  description = "Names of the image repositories to create (one per Docker image)."
  type        = list(string)
}

variable "keep_images" {
  description = "How many newest images to keep in each repository; older ones are deleted automatically."
  type        = number
  default     = 10
}
