#! /bin/bash

cd $(dirname $0)

# Fix terminal on exit b/c 'docker compose' leaves tty termiinal echo off sometimes
trap "stty sane" EXIT

docker compose --profile send-events up
docker compose --profile get-egress-events up
