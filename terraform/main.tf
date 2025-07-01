# terraform/main.tf

resource "aws_s3_bucket" "glb_bucket" {
  bucket = var.bucket_name

  tags = {
    Name        = "glb-file-storage"
    Environment = "Dev"
  }
}

