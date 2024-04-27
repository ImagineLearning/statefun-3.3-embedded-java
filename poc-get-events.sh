#! /bin/bash

# Get the events sent to the egress stream
stream_name=$(aws kinesis list-streams | jq -crM .StreamNames[] | grep ManagedFlinkEgressStream)

shard_id=$(aws kinesis list-shards --stream-name $stream_name | jq -crM .Shards[0].ShardId)
shard_iterator=$(aws kinesis get-shard-iterator --shard-id $shard_id --shard-iterator-type TRIM_HORIZON --stream-name $stream_name | jq -crM .ShardIterator)
for encoded_data in $(aws kinesis get-records --shard-iterator $shard_iterator | jq -crM .Records[].Data); do
  echo $encoded_data | base64 -d | jq .
done
