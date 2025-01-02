#! /bin/bash

set -e

# Get the events sent to the egress stream
stream_name=$(aws kinesis list-streams | jq -crM .StreamNames[] | grep flink-tf-demo-egress)

get_records_response=$(mktemp)

shard_id=$(aws kinesis list-shards --stream-name $stream_name | jq -crM .Shards[0].ShardId)
shard_iterator=$(aws kinesis get-shard-iterator --shard-id $shard_id --shard-iterator-type TRIM_HORIZON --stream-name $stream_name | jq -crM .ShardIterator)
while [ "true" ]; do
    aws kinesis get-records --shard-iterator $shard_iterator >$get_records_response
    shard_iterator=$(cat $get_records_response | jq -crM .NextShardIterator)
    record_count=0
    for encoded_data in $(cat $get_records_response | jq -crM .Records[].Data); do
      record_count=$(expr $record_count + 1)
      echo $encoded_data | base64 -d | jq .
    done
    if [ $record_count -eq 0 ]; then
        sleep 2
    fi
done

