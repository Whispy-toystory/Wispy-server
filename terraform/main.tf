data "aws_availability_zones" "available" {
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

resource "aws_s3_bucket" "glb_bucket" {
  bucket = var.bucket_name

  tags = {
    Name        = "glb-file-storage"
    Environment = var.environment
    Project     = var.project_name
  }
}