#! /bin/sh

# Port-forward port 4566 to the LocalStack service running in the Kubernetes cluster
# This script requires the jq command: https://jqlang.github.io/jq/

localstack_pod_name=$(kubectl -n localstack get pods -o json | jq -cr .items[0].metadata.name)
kubectl -n localstack port-forward $localstack_pod_name 4566:4566
