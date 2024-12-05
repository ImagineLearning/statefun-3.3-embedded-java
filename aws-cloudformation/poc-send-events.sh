#! /bin/bash

stream_name=$(aws kinesis list-streams | jq -crM .StreamNames[] | grep ManagedFlinkIngressStream)

grep -v test.action ../src/test/resources/product-cart-integration-test-events.jsonl | while read line; do
  partkey=$(echo $line | md5sum | awk '{print $1}') 
  data=$(echo $line | base64)
  cmd="aws kinesis put-record --stream-name $stream_name --partition-key $partkey --data $data"
  echo $cmd
  eval $cmd
done
