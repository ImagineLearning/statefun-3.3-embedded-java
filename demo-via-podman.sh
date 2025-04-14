#! /bin/bash

set -e

cd $(dirname $0)

export NETWORK_NAME=demo

function main() {



  case $1 in
	start)
	    start
	    ;;
  send-events)
      send_events
      ;;
  get-events)
      get_events
      ;;
	stop)
	    stop
	    ;;
	*)
	    echo "Usage: $0 {start|send-events|get-events|down}"
      exit 1
      ;;
  esac
}

function run_cmd() {
    cmd="$@"
    echo $cmd
    eval $cmd
}

function start() {

    if ! podman network ls --format json | jq .[].name | grep $NETWORK_NAME >/dev/null; then
	run_cmd "podman network create $NETWORK_NAME"
    fi
    
    # ./mvnw package
    
    # podman build -t $(basename $PWD) .

    if podman images | grep aws-cli-with-jq ; then
      echo "aws-cli-with-jq image already exists"
    else
      cat >Dockerfile.aws-cli-with-jq~ <<EOF
FROM amazon/aws-cli

RUN yum install -y jq
EOF
      podman build -t aws-cli-with-jq -f Dockerfile.aws-cli-with-jq~ .
    fi

    # run_container localstack
    run_cmd podman run -d --network demo --name demo.localhost.localstack.cloud -p 4566:4566 -e SERVICES=kinesis \
    -e HOSTNAME=demo.localhost.localstack.cloud -e AWS_REGION=us-east-1 -e AWS_ACCESS_KEY_ID=example-access-key-id \
    -e AWS_SECRET_ACCESS_KEY=example-secret-access-key -e AWS_ENDPOINT=https://demo.localhost.localstack.cloud:4566 \
    -e AWS_ENDPOINT_URL=https://demo.localhost.localstack.cloud:4566 -e DEBUG=0 -e KINESIS_ERROR_PROBABILITY=0.0 \
    -e DOCKER_HOST=unix:///var/run/docker.sock localstack/localstack:3.1.0 $CMD_BEGIN

    # run_container jobmanager $(basename $PWD)
    run_cmd podman run -d --network demo --name demo.flink-jobmanager --entrypoint /entrypoint.sh -p 8081:8081 \
    -v ./docker-mounts/checkpoints:/checkpoints -v ./docker-mounts/savepoints:/savepoints -e IS_LOCAL_DEV=true \
    -e AWS_REGION=us-east-1 -e AWS_ACCESS_KEY_ID=example-access-key-id -e AWS_SECRET_ACCESS_KEY=example-secret-access-key \
    -e AWS_ENDPOINT=https://demo.localhost.localstack.cloud:4566 -e AWS_CBOR_DISABLE=true -e USE_ENHANCED_FANOUT=false \
    statefun-3.3-embedded-java $CMD_BEGIN standalone-job -D "rest.address=demo.flink-jobmanager" \
    -D "jobmanager.rpc.address=demo.flink-jobmanager" -D "state.savepoints.dir=file:///savepoints" \
    -D "state.checkpoints.dir=file:///checkpoints" --job-classname org.apache.flink.statefun.flink.core.StatefulFunctionsJob

    # run_container taskmanager $(basename $PWD)
    run_cmd podman run -d --network demo --name demo.flink-taskmanager --entrypoint /entrypoint.sh -p 5066:5066 \
    -v ./docker-mounts/checkpoints:/checkpoints -v ./docker-mounts/savepoints:/savepoints -e IS_LOCAL_DEV=true \
    -e AWS_REGION=us-east-1 -e AWS_ACCESS_KEY_ID=example-access-key-id -e AWS_SECRET_ACCESS_KEY=example-secret-access-key \
    -e AWS_ENDPOINT=https://demo.localhost.localstack.cloud:4566 -e AWS_CBOR_DISABLE=true -e USE_ENHANCED_FANOUT=false \
    -e FLINK_ENV_JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5066 statefun-3.3-embedded-java \
    $CMD_BEGIN taskmanager -D "jobmanager.rpc.address=demo.flink-jobmanager" -D "state.savepoints.dir=file:///savepoints" \
    -D "state.checkpoints.dir=file:///checkpoints"

    # run_container create-streams
    cat >command.sh~ <<EOF
set -x
echo Creating ingress stream
until aws --endpoint-url=http://demo.localhost.localstack.cloud:4566 kinesis create-stream --stream-name example-ingress-stream --shard-count 1; do sleep 1; done
echo Creating egress stream
aws --endpoint-url=http://demo.localhost.localstack.cloud:4566 kinesis create-stream --stream-name example-egress-stream --shard-count 1
echo Listing streams
aws --endpoint-url=http://demo.localhost.localstack.cloud:4566 kinesis list-streams
EOF
    chmod +x command.sh~
    run_cmd podman run -d --network demo --name demo.create-kinesis-streams --entrypoint /bin/bash \
    -v /var/run/docker.sock:/var/run/docker.sock -v ./src/test/resources:/test-resources -e AWS_ACCESS_KEY_ID=example-access-key-id \
    -e AWS_SECRET_ACCESS_KEY=example-access-key-id -e AWS_REGION=us-east-1 \
    -v ./command.sh~:/command.sh \
    amazon/aws-cli \
    -c /command.sh

}

function send_events() {
    cat >command.sh~ <<EOF
grep -v test.action /test-resources/product-cart-integration-test-events.jsonl | while read line; do
  partkey=\$(echo \$line | md5sum | awk '{print \$1}')
  data=\$(echo \$line | base64 -w 0)
  cmd="aws --endpoint-url=http://demo.localhost.localstack.cloud:4566 kinesis put-record --stream-name example-ingress-stream --partition-key \$partkey --data \$data"
  echo \$cmd
  eval \$cmd
done
EOF
    chmod +x command.sh~
    run_cmd podman run -it --rm --network demo --name demo.send-events-to-ingress --entrypoint /bin/bash \
    -v /var/run/docker.sock:/var/run/docker.sock -v ./src/test/resources:/test-resources -e AWS_ACCESS_KEY_ID=example-access-key-id \
    -e AWS_SECRET_ACCESS_KEY=example-access-key-id -e AWS_REGION=us-east-1 \
    -v ./command.sh~:/command.sh \
    amazon/aws-cli \
    -c /command.sh
}

function get_events() {
    cat >command.sh~ <<EOF
shard_id=\$(aws --endpoint-url=http://demo.localhost.localstack.cloud:4566 kinesis list-shards --stream-name example-egress-stream | jq -crM .Shards[0].ShardId)
shard_iterator=\$(aws --endpoint-url=http://demo.localhost.localstack.cloud:4566 kinesis get-shard-iterator --shard-id \$shard_id --shard-iterator-type TRIM_HORIZON --stream-name example-egress-stream | jq -crM .ShardIterator)
for encoded_data in \$(aws --endpoint-url=http://demo.localhost.localstack.cloud:4566 kinesis get-records --shard-iterator \$shard_iterator | jq -crM .Records[].Data); do
  echo \$encoded_data | base64 -d | jq .
done
EOF
    chmod +x command.sh~
    run_cmd podman run -it --rm --network demo --name demo.get-events-from-egress --entrypoint /bin/bash \
    -v /var/run/docker.sock:/var/run/docker.sock -v ./src/test/resources:/test-resources -e AWS_ACCESS_KEY_ID=example-access-key-id \
    -e AWS_SECRET_ACCESS_KEY=example-access-key-id -e AWS_REGION=us-east-1 \
    -v ./command.sh~:/command.sh \
    aws-cli-with-jq \
    -c /command.sh
}

function stop() {
    set +e

    stop_container get-egress-events
    stop_container send-events
    stop_container create-streams
    stop_container jobmanager $(basename $PWD)
    stop_container taskmanager $(basename $PWD)
    stop_container localstack
    run_cmd "podman network rm $NETWORK_NAME"
}

function stop_container() {
    local service_name=$1

    cat >yq.expression~ <<EOF
.services.$service_name |
"podman stop " + (.container_name) + "; " +
"podman rm " + (.container_name)
EOF
    run_cmd $(yq --from-file yq.expression~ docker-compose.yml)
    rm yq.expression~
    
}

function run_container() {
    local service_name=$1
    export IMAGE_NAME=$2
    cat >yq.expression~ <<EOF
.services."$service_name" |
"podman run -d --network $NETWORK_NAME "
+ " --name " + (.container_name) + " "
+ (if .entrypoint then ("--entrypoint " + .entrypoint + " ") else "" end)
+ ((.ports // []) | map("-p " + .) | join(" ")) + " "
+ ((.volumes // []) | map("-v " + .) | join(" ")) + " "
+ ((.environment // []) | map("-e " + .) | join(" ")) + " "
+ (.image // "$IMAGE_NAME") + " "
+ "\$CMD_BEGIN "
+ (.command // "")
EOF
    cmd=$(cat docker-compose.yml | yq -o json . | jq -cr --from-file yq.expression~)
    if echo $cmd | grep '/bin/bash' >/dev/null; then
        cmd=$(echo $cmd | sed 's!/bin\/bash -c!/bin/bash!' | sed 's!\$CMD_BEGIN!-c!')
    fi
    echo $cmd

    rm yq.expression~
}


main "$@"
