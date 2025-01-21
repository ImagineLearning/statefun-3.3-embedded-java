#! /bin/bash

# A helper script to launch and configure a local IDP
cd $(dirname $0)

function main() {

  # Default to AWS, but allow localstack as well
  cloud=${1:-aws}
  if [ $cloud = "aws" ]; then
    # Verify that the credentials are set and not expired
    if grep =REPLACE local/aws/manifests/credentials.yaml >/dev/null || \
       grep 000000000000 local/aws/manifests/credentials.yaml >/dev/null || \
       [[ $(echo $(date +%s) - $(stat -f%m local/aws/manifests/credentials.yaml) | bc) -gt 43200 ]]; then
         echo "The credentials in ./local/aws/manifests/credentials.yaml appear to have expired.  Consider running ./local/aws/update_credentials.sh"

         # I haven't figured out how to refresh the credentials w/o restarting the cluster, so delete the cluster
         if [ "$(idpbuilder get clusters)" ]; then
           idpbuilder delete
         fi
         exit 1
    fi
  fi

  if [ -z $(idpbuilder get clusters) ]; then
    echo "Running: idpbuilder create -p local/$cloud"
    idpbuilder create -p local/$cloud
    echo
  fi

  echo "Waiting for getea to be ready..."
  wait_for_pods gitea my-gitea
  echo

  echo "Building and loading the docker image for the Managed Flink composition function"
  ./xfn/configure-xfn.sh
  echo

  echo "Waiting for the Crossplane AWS providers to be ready..."
  wait_for_pods crossplane-system provider-aws
  echo


  echo "Waiting for the Crossplane AWS provider configs to be ready..."
  until [[ $(kubectl get providerconfigs 2>&1 | grep aws | wc -l) -eq 2 ]]; do
    sleep 2
  done
  echo

  echo "Loading the Crossplane Composite Resource Definitions and Compositions"
  for i in $(find resources -name \*xrd.yaml -o -name \*comp.yaml); do
    kubectl apply -f $i
  done
  echo

  echo "The system is ready for claims to be applied"
}

# Wait for pods in the given namespace to be running
function wait_for_pods() {
    namespace=$1
    pod_name_prefix=$2

    running=0
    total=0

    until [[ $total != 0 && $total == $running ]]; do

        sleep 2
        running=0
        total=0

        for i in $(kubectl -n ${namespace} get pods | grep $pod_name_prefix | grep -v Completed | awk '{print $3}'); do
            if [ $i == "Running" ]; then
                running=$(echo $running + 1 | bc)
            fi
            total=$(echo $total + 1 | bc)
        done
    done
}

main "$@"
