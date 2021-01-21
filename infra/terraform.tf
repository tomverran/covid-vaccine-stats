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
    "style.css" = "text/css"
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
      "ssm:GetParameter"
    ]
    resources = [
      "arn:aws:ssm:eu-west-1:${data.aws_caller_identity.current.account_id}:parameter/vaccines/*"
    ]
  }
  statement {
    effect = "Allow"
    actions = [
      "events:PutRule"
    ]
    resources = [
      aws_cloudwatch_event_rule.lambda_triggers.arn
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
  max_session_duration = 28800
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
      SCHEDULER_RULE_NAME = aws_cloudwatch_event_rule.lambda_triggers.name
    }
  }
}

resource "aws_lambda_permission" "allow-cloudwatch" {
  function_name = aws_lambda_function.lambda-function.function_name
  principal = "events.amazonaws.com"
  action = "lambda:InvokeFunction"
}

resource "aws_cloudwatch_event_rule" "lambda_triggers" {
  schedule_expression = "cron(0/5 16-23 * * ? *)"
  name_prefix = "lambda-cron-"

  lifecycle {
    ignore_changes = [schedule_expression]
  }
}

resource "aws_cloudwatch_event_target" "lambda-target" {
  rule = aws_cloudwatch_event_rule.lambda_triggers.id
  arn = aws_lambda_function.lambda-function.arn
  target_id = "lambda"
}

resource "aws_route53_zone" "app-zone" {
  name = "covid-vaccine-stats.uk"
}

provider "aws" {
  alias = "us-east-1"
  region = "us-east-1"
}

resource "aws_acm_certificate" "cert" {
  domain_name = aws_route53_zone.app-zone.name
  validation_method = "DNS"
  provider = aws.us-east-1

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "cert-validation" {
  for_each = {
    for dvo in aws_acm_certificate.cert.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }
  name    = each.value.name
  type    = each.value.type
  records = [each.value.record]
  zone_id = aws_route53_zone.app-zone.zone_id
  ttl     = 60
}

resource "aws_route53_record" "google-domain-validation" {
  records = ["google-site-verification=pnP-srQ7EWoJtccK6nohOmTUrgQyojXbG2G9q-mkzsM"]
  zone_id = aws_route53_zone.app-zone.zone_id
  name = aws_route53_zone.app-zone.name
  type = "TXT"
  ttl  = 60
}

resource "aws_acm_certificate_validation" "cert-valid" {
  certificate_arn = aws_acm_certificate.cert.arn
  provider = aws.us-east-1
}

resource "aws_cloudfront_distribution" "frontend-cloudfront" {
  aliases = [trimsuffix(aws_route53_zone.app-zone.name, ".")]
  default_root_object = "index.html"
  enabled = true

  default_cache_behavior {
    cached_methods = ["HEAD", "GET"]
    allowed_methods = ["HEAD", "GET"]
    viewer_protocol_policy = "redirect-to-https"
    target_origin_id = aws_s3_bucket.statistics-bucket.bucket

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }
  }

  origin {
    domain_name = aws_s3_bucket.statistics-bucket.bucket_regional_domain_name
    origin_id = aws_s3_bucket.statistics-bucket.bucket
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn = aws_acm_certificate_validation.cert-valid.certificate_arn
    minimum_protocol_version = "TLSv1.2_2018"
    ssl_support_method = "sni-only"
  }
}

resource "aws_route53_record" "cloudfront-record" {
  zone_id = aws_route53_zone.app-zone.zone_id
  name = aws_route53_zone.app-zone.name
  type = "A"

  alias {
    evaluate_target_health = true
    name = aws_cloudfront_distribution.frontend-cloudfront.domain_name
    zone_id = aws_cloudfront_distribution.frontend-cloudfront.hosted_zone_id
  }
}

