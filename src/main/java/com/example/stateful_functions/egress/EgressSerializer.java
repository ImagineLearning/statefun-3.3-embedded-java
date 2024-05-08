package com.example.stateful_functions.egress;

import com.example.stateful_functions.Configuration;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import org.apache.flink.kinesis.shaded.com.amazonaws.util.Md5Utils;
import org.apache.flink.statefun.sdk.kinesis.egress.EgressRecord;
import org.apache.flink.statefun.sdk.kinesis.egress.KinesisEgressSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;


public class EgressSerializer implements KinesisEgressSerializer<ExampleProtobuf.Envelope> {

    private static final Logger LOG = LoggerFactory.getLogger(EgressSerializer.class);

    @Override
    public EgressRecord serialize(ExampleProtobuf.Envelope envelope) {
        try {
            String payload = envelope.getPayload();
            String partitionKey = Optional.ofNullable(envelope.getPartitionKey())
                    .orElse(new String(Md5Utils.computeMD5Hash(payload.getBytes(UTF_8))));
            return EgressRecord.newBuilder()
                    .withPartitionKey(partitionKey)
                    .withData(payload.getBytes(UTF_8))
                    .withStream(Configuration.EGRESS_KINESIS_STREAM_NAME)
                    .build();
        } catch (Exception e) {
            LOG.info("Failed to serialize event", e);
            return null;
        }
    }
}

