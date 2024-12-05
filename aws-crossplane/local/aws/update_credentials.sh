#! /bin/bash

# Update manifests/credentials.yaml with values from environment variables
cd $(dirname $0)

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
    sed "s!aws_access_key_id=REPLACE!aws_access_key_id=$AWS_ACCESS_KEY_ID!" | \
    sed "s!aws_secret_access_key=REPLACE!aws_secret_access_key=$AWS_SECRET_ACCESS_KEY!" | \
    sed "s!aws_session_token=REPLACE!aws_session_token=$AWS_SESSION_TOKEN!" >manifests/credentials.yaml.tmp
mv manifests/credentials.yaml.tmp manifests/credentials.yaml

echo "Run this command to clear env vars:"
echo "unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN"
