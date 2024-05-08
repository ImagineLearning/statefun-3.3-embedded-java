#! /bin/bash

set -e

cd $(dirname $0)

AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID:-516535517513}
NEXT_TOKEN_ARG=

CWLOGS_DIR=.cwlogs
mkdir -p $CWLOGS_DIR

ITERATION=1

if [ -f $CWLOGS_DIR/next.token ]; then
    NEXT_TOKEN_ARG="--next-token $(cat $CWLOGS_DIR/next.token)"
fi

while true; do
    CWLOG_FILE=$CWLOGS_DIR/$(printf "%010d" $ITERATION).json
    aws logs get-log-events \
	--start-from-head \
	$NEXT_TOKEN_ARG \
	--log-group-name managed-flink-poc-log-group-${AWS_ACCOUNT_ID} \
	--log-stream-name managed-flink-poc-log-stream-${AWS_ACCOUNT_ID} \
	>$CWLOG_FILE
    
    NEXT_TOKEN=$(cat $CWLOG_FILE | jq -crM .nextForwardToken)
    echo $NEXT_TOKEN >$CWLOGS_DIR/next.token
    NEXT_TOKEN_ARG="--next-token $NEXT_TOKEN"
    EVENT_COUNT=$(cat $CWLOG_FILE | jq -crM '.events | length')

    if [[ $EVENT_COUNT == 0 ]]; then
	   sleep 2
	   rm $CWLOG_FILE
    else
	cat $CWLOG_FILE | jq -crM '.events[] | [.timestamp,(.message | fromjson | [.messageType,.logger,.message] | join(" "))] | join(" ")' | tee -a $CWLOGS_DIR/formatted.log
    fi

    ITERATION=$(echo "1 + $ITERATION" | bc)
done
