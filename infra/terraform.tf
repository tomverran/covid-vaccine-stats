terraform {
  backend "s3" {
    bucket = "bootstrap-states3bucket-1dkgi4f94tp70"
    key    = "terraform"
    region = "eu-west-1"
    encrypt = true
  }
}

provider "aws" {
  region = "eu-west-1"
}

resource "aws_s3_bucket" "statistics-bucket" {
  bucket_prefix = "vaccine-statistics-"
  acl = "public-read"

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET"]
    allowed_origins = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }

  website {
    index_document = "index.html"
    error_document = "index.html" // no errors here
  }
}


locals {
  frontend_files = {
    "index.html" = "text/html",
    "app.js" = "application/javascript"
  }
}

resource "aws_s3_bucket_object" "frontend" {
  for_each = local.frontend_files
  bucket = aws_s3_bucket.statistics-bucket.id
  etag = filemd5("../ui/${each.key}")
  source =  "../ui/${each.key}"
  content_type = each.value
  acl = "public-read"
  key = each.key
}

data aws_caller_identity "current" {}

data "aws_iam_policy_document" "lambda-assume-role-policy" {
  statement {
    effect = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      identifiers = ["lambda.amazonaws.com"]
      type = "Service"
    }
    principals {
      identifiers = [data.aws_caller_identity.current.arn]
      type = "AWS" // for local testing
    }
  }
}

data "aws_iam_policy_document" "lambda-policy-document" {
  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup"
    ]
    resources = [
      "arn:aws:logs:eu-west-1:${data.aws_caller_identity.current.account_id}:*"
    ]
  }
  statement {
    effect = "Allow"
    actions = [
      "s3:PutObjectAcl",
      "s3:PutObject",
      "s3:GetObject"
    ]
    resources = [
      aws_s3_bucket.statistics-bucket.arn,
      "${aws_s3_bucket.statistics-bucket.arn}/*"
    ]
  }
}

resource "aws_iam_role" "lambda-role" {
  assume_role_policy = data.aws_iam_policy_document.lambda-assume-role-policy.json
  name_prefix = "stats-lambda-"
}

resource "aws_iam_role_policy" "lambda-role-policy" {
  policy = data.aws_iam_policy_document.lambda-policy-document.json
  role = aws_iam_role.lambda-role.id
}

resource "aws_s3_bucket" "lambda-code" {
  bucket_prefix = "lambda-code-"
}

resource "aws_s3_bucket_public_access_block" "lambda-code-block" {
  bucket = aws_s3_bucket.lambda-code.id
  restrict_public_buckets = true
  block_public_acls   = true
  block_public_policy = true
  ignore_public_acls  = true
}

locals {
  code_path = "${path.module}/../target/scala-2.13/vaccine-stats.jar"
  code_md5 = filemd5(local.code_path)
}

resource "aws_s3_bucket_object" "checkout-code" {
  source =  local.code_path
  bucket = aws_s3_bucket.lambda-code.id
  key = "checkout-${local.code_md5}.jar"
  etag = local.code_md5
}

resource "aws_lambda_function" "lambda-function" {
  s3_key = aws_s3_bucket_object.checkout-code.key
  s3_bucket = aws_s3_bucket.lambda-code.bucket
  function_name = "vaccine-statistics"
  handler = "io.tvc.vaccines.Handler"
  role = aws_iam_role.lambda-role.arn
  memory_size = 1024
  runtime = "java11"
  timeout = 120

  environment {
    variables = {
      STATISTICS_BUCKET_NAME = aws_s3_bucket.statistics-bucket.bucket
    }
  }
}

resource "aws_cloudwatch_event_rule" "lambda_triggers" {
  schedule_expression = "rate(30 minutes)"
  name_prefix = "lambda-cron-"
}

resource "aws_cloudwatch_event_target" "lambda-target" {
  rule = aws_cloudwatch_event_rule.lambda_triggers.name
  arn = aws_lambda_function.lambda-function.arn
  target_id = "lambda"
}
