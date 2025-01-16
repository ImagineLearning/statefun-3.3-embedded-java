#!/bin/bash

# Run this script to deploy the function to the local running IDP
cd $(dirname $0)

go generate ./...

# Build the Docker image
docker build -t function-managed-flink .

# Tag the image with the Gitea server URL
docker tag function-managed-flink gitea.cnoe.localtest.me:8443/giteaadmin/function-managed-flink:1

# Log in to the Gitea server
idpbuilder get secrets -p gitea -o json | jq -r '.[0].data.password' | docker login -u giteaAdmin --password-stdin gitea.cnoe.localtest.me:8443

# Push the image to the Gitea server
docker push gitea.cnoe.localtest.me:8443/giteaadmin/function-managed-flink:1
