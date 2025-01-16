#! /bin/bash

# Transfer account number and key/secret/session tokens to the local Crossplane AWS provider
cd $(dirname $0)

if [ -z "$AWS_ACCOUNT" ]; then
    # Attempt to resolve the account number via get-caller-identity
    AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
    if [ -z "$AWS_ACCOUNT" ]; then
      echo "AWS_ACCOUNT is not set, and finding it via \`aws sts get-caller-identity\` failed."
      exit 1
    fi
fi

required_vars="AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN"
for var in ${required_vars}; do
    val=$(eval "echo \$$var")
    if [ -z "$val" ]; then
      echo "$var is not set"
      exit 1
    fi
done

git restore manifests/credentials.yaml

cat manifests/credentials.yaml | \
    sed "s!000000000000!$AWS_ACCOUNT!" | \
    sed "s!aws_access_key_id=REPLACE!aws_access_key_id=$AWS_ACCESS_KEY_ID!" | \
    sed "s!aws_secret_access_key=REPLACE!aws_secret_access_key=$AWS_SECRET_ACCESS_KEY!" | \
    sed "s!aws_session_token=REPLACE!aws_session_token=$AWS_SESSION_TOKEN!" >manifests/credentials.yaml.tmp
mv manifests/credentials.yaml.tmp manifests/credentials.yaml

echo "Run this command to clear env vars:"
echo "unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN"
