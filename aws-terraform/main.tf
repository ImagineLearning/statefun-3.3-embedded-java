provider "aws" {
  region = "us-east-2"
}

# Caller identity allows referencing the account ID w/o having to hard-code it in the bucket name
data "aws_caller_identity" "current" {}
# Same for aws_region so we don't have to hard-code the Flink environment variables.
data "aws_region" "current" {}

resource "aws_s3_bucket" "flink_demo_bucket" {
  # Bucket names must be globally unique, so I'm appending the account ID to workaround BucketAlreadyExists
  bucket = "flink-tf-demo-bucket-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_ownership_controls" "flink_demo_bucket_ownership_controls" {
  bucket = aws_s3_bucket.flink_demo_bucket.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "flink_demo_bucket_acl" {
  depends_on = [aws_s3_bucket_ownership_controls.flink_demo_bucket_ownership_controls]

  bucket = aws_s3_bucket.flink_demo_bucket.id
  acl    = "private"
}

resource "aws_kinesis_stream" "flink_demo_ingress" {
  name             = "flink-tf-demo-ingress"
  shard_count      = 1
  retention_period = 24  # Retention period in hours

  shard_level_metrics = [
    "IncomingBytes",
    "OutgoingBytes",
  ]

  stream_mode_details {
    stream_mode = "PROVISIONED"
  }
}

resource "aws_kinesis_stream" "flink_demo_egress" {
  name             = "flink-tf-demo-egress"
  shard_count      = 1
  retention_period = 24  # Retention period in hours

  shard_level_metrics = [
    "IncomingBytes",
    "OutgoingBytes",
  ]

  stream_mode_details {
    stream_mode = "PROVISIONED"
  }
}


resource "aws_iam_role" "flink_application_role" {
  name = "flink-application-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = {
        Service = "kinesisanalytics.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "kinisis_full_access" {
  role       = aws_iam_role.flink_application_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonKinesisFullAccess"
}

resource "aws_iam_role_policy_attachment" "s3_full_access" {
  role       = aws_iam_role.flink_application_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

resource "aws_iam_role_policy_attachment" "cloudwatch_full_access" {
  role       = aws_iam_role.flink_application_role.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchFullAccess"
}

resource "aws_iam_role_policy" "flink_app_s3_policy" {
  name = "flink-app-s3-policy"
  role = aws_iam_role.flink_application_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action   = [
        "s3:GetObject",
        "s3:ListBucket"
      ]
      Effect   = "Allow"
      Resource = [
        aws_s3_bucket.flink_demo_bucket.arn,
        "${aws_s3_bucket.flink_demo_bucket.arn}/*"
      ]
    }]
  })
}

resource "aws_iam_role_policy" "flink_app_kinesis_policy" {
  name = "flink-app-kinesis-policy"
  role = aws_iam_role.flink_application_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Resource = [
        "${aws_kinesis_stream.flink_demo_ingress.arn}",
        "${aws_kinesis_stream.flink_demo_egress.arn}"
      ]
      Action = [
        "kinesis:DescribeStream",
        "kinesis:GetRecords",
        "kinesis:GetShardIterator",
        "kinesis:ListShards"
      ]
    }]
  })
}

resource "aws_iam_role_policy" "flink_app_logs_policy" {
  name = "flink-app-logs-policy"
  role = aws_iam_role.flink_application_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Resource = [
        "${aws_cloudwatch_log_group.flink_demo_log_group.arn}"
      ]
      Action = [
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams",
        "logs:PutLogEvents"
      ]
    }]
  })
}

resource "aws_cloudwatch_log_group" "flink_demo_log_group" {
  name              = "flink-tf-demo-log-group"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_stream" "flink_demo_log_stream" {
  name           = "flink-tf-demo-log-stream"
  log_group_name = aws_cloudwatch_log_group.flink_demo_log_group.name
}

resource "aws_iam_role_policy" "flink_app_metrics_policy" {
  name = "flink-app-metrics-policy"
  role = aws_iam_role.flink_application_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Resource = "*"
      Action = [
        "cloudwatch:PutMetricData"
      ]
    }]
  })
}

resource "aws_iam_role_policy" "flink_app_secrets_manager_policy" {
  name = "SecretsManagerAccessPolicy"
  role = aws_iam_role.flink_application_role.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ],
        Effect   = "Allow",
        Resource = "*"
      }
    ]
  })
}


# Reference: https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/kinesisanalyticsv2_application
resource "aws_kinesisanalyticsv2_application" "flink_demo_tf" {
  name                   = "flink-tf-demo-application"
  runtime_environment    = "FLINK-1_18"
  service_execution_role = aws_iam_role.flink_application_role.arn
  application_mode = "STREAMING"
  start_application = true

  application_configuration {
    application_code_configuration {
      code_content {
        s3_content_location {
          bucket_arn = aws_s3_bucket.flink_demo_bucket.arn
          file_key   = "my-stateful-functions-embedded-java-3.3.0.jar"
        }
      }
      code_content_type = "ZIPFILE"
    }

    application_snapshot_configuration {
      snapshots_enabled = true
    }

    environment_properties {
      property_group {
        property_group_id = "StatefunApplicationProperties"

        property_map = {
              EVENTS_INGRESS_STREAM_DEFAULT = "${aws_kinesis_stream.flink_demo_ingress.name}"
              EVENTS_EGRESS_STREAM_DEFAULT  = "${aws_kinesis_stream.flink_demo_egress.name}"
              AWS_REGION =  data.aws_region.current.name
              ENVIRONMENT = "demo"
              NAMESPACE = "sandbox-demo"
              OTEL_SDK_DISABLED: "false"
              OTEL_SERVICE_NAME: "sandbox-statefun"
              OTEL_EXPORTER_OTLP_ENDPOINT = "https://otlp.nr-data.net:443"
              OTEL_EXPORTER_OTLP_PROTOCOL: "http/protobuf"
              OTEL_EXPORTER_OTLP_HEADERS = "!secret:OTEL_EXPORTER_OTLP_HEADERS"
        }
      }
    }

    flink_application_configuration {
      checkpoint_configuration {
        configuration_type = "CUSTOM"
        checkpoint_interval = 60000 # Every minute # Increase this to 300000 in production (every 5 minutes)
        checkpointing_enabled = true
      }
      monitoring_configuration {
        configuration_type = "CUSTOM"
        log_level          = "INFO"
        metrics_level      = "APPLICATION"
      }
      parallelism_configuration {
        auto_scaling_enabled = false
        configuration_type   = "CUSTOM"
        parallelism          = 1
        parallelism_per_kpu  = 1
      }
    }

    run_configuration {
      application_restore_configuration {
        application_restore_type = "RESTORE_FROM_LATEST_SNAPSHOT"
        # snapshot_name = "xyz"
      }
      flink_run_configuration {
        allow_non_restored_state = false
      }
    }
  }
  cloudwatch_logging_options {
    log_stream_arn = aws_cloudwatch_log_stream.flink_demo_log_stream.arn
  }

  tags = {
    ProvisionedBy = "Terraform"
  }
}
