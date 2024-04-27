#! /bin/bash

set -e

# Fix terminal on exit b/c 'docker compose' leaves tty termiinal echo off sometimes
trap "stty sane" EXIT

cd $(dirname $0)

docker compose --profile all down
./mvnw package
docker compose build jobmanager
docker compose build taskmanager
docker compose --profile statefun up -d

# Wait for the container that creates the Kinesis streams to exit
sleep 5
until [ $(docker inspect -f {{.State.Status}} statefun-33-embedded-java-create-streams-1) = 'exited' ]; do sleep 1; done

docker compose --profile send-events up
docker compose --profile get-egress-events up
