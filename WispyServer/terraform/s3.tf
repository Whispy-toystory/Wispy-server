resource "random_string" "bucket_suffix" {
  length  = 8
  special = false
  upper   = false
}

resource "aws_s3_bucket" "wispy_bucket" {
  bucket = "${var.project_name}-${var.environment}-glb-files-${random_string.bucket_suffix.result}"

  tags = merge(var.tags, {
    Name        = "${var.project_name}-${var.environment}-glb-bucket"
    Description = "GLB files storage for Wispy Server"
  })
}

resource "aws_s3_bucket_versioning" "wispy_bucket_versioning" {
  bucket = aws_s3_bucket.wispy_bucket.id
  versioning_configuration {
    status = "Disabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "wispy_bucket_encryption" {
  bucket = aws_s3_bucket.wispy_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "wispy_bucket_pab" {
  bucket = aws_s3_bucket.wispy_bucket.id

  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_cors_configuration" "wispy_bucket_cors" {
  bucket = aws_s3_bucket.wispy_bucket.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "POST", "DELETE", "HEAD"]
    allowed_origins = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "wispy_bucket_lifecycle" {
  bucket = aws_s3_bucket.wispy_bucket.id

  rule {
    id     = "optimize_storage_cost"
    status = "Enabled"

    filter {
      prefix = ""
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

  }
}

resource "aws_iam_role_policy" "eks_s3_policy" {
  name = "${var.project_name}-${var.environment}-eks-s3-policy"
  role = aws_iam_role.eks_node_group.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.wispy_bucket.arn,
          "${aws_s3_bucket.wispy_bucket.arn}/*"
        ]
      }
    ]
  })
}