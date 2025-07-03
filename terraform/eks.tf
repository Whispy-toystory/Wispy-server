module "eks" {
  source = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"

  cluster_name    = var.cluster_name
  cluster_version = var.cluster_version

  vpc_id                         = module.vpc.vpc_id
  subnet_ids                     = module.vpc.private_subnets
  cluster_endpoint_public_access = true

  cluster_service_role_arn = aws_iam_role.eks_cluster_role.arn

  eks_managed_node_groups = {
    one = {
      name = "wispy-server-node-group"

      instance_types = ["t3.small"]
      capacity_type  = "ON_DEMAND"

      min_size     = 1
      max_size     = 3
      desired_size = 2

      ami_type = "AL2_x86_64"
      
      disk_size = 20
      
      iam_role_arn = aws_iam_role.eks_node_group_role.arn
      
      vpc_security_group_ids = [aws_security_group.node_group_sg.id]
    }
  }

  enable_irsa = true

  manage_aws_auth_configmap = true

  aws_auth_users = [
    {
      userarn  = data.aws_caller_identity.current.arn
      username = data.aws_caller_identity.current.user_id
      groups   = ["system:masters"]
    },
  ]

  tags = {
    Environment = var.environment
    Project = var.project_name
    Terraform = "true"
  }
}

data "aws_caller_identity" "current" {}

resource "aws_security_group" "node_group_sg" {
  name        = "${var.cluster_name}-node-group-sg"
  description = "Security group for EKS node group"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description = "Allow cluster control plane"
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.cluster_name}-node-group-sg"
    Environment = var.environment
    Project = var.project_name
  }
}