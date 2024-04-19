package com.example.stateful_functions.ingress;


import com.example.stateful_functions.Configuration;

import com.example.stateful_functions.protobuf.ExampleProtobuf;
import org.apache.flink.statefun.sdk.io.IngressIdentifier;
import org.apache.flink.statefun.sdk.io.IngressSpec;
import org.apache.flink.statefun.sdk.kinesis.auth.AwsCredentials;
import org.apache.flink.statefun.sdk.kinesis.ingress.KinesisIngressStartupPosition;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;


public class IngressSpecs {

    public static final IngressIdentifier<ExampleProtobuf.Envelope> INGRESS_ID =
            new IngressIdentifier<>(ExampleProtobuf.Envelope.class, "example", "input-ingress");

    private static final IngressSpec<ExampleProtobuf.Envelope> createKinesisIngress(IngressIdentifier<ExampleProtobuf.Envelope> id, String streamName) {
        return KinesisSourceIngressBuilder.forIdentifier(id)
                .withAwsRegion(Configuration.getAwsRegion())
                .withAwsCredentials(AwsCredentials.fromDefaultProviderChain())
                .withDeserializer(IngressDeserializer.class)
                .withStream(streamName)
                .withStartupPosition(KinesisIngressStartupPosition.fromEarliest())
                .useEnhancedFanout(Configuration.USE_ENHANCED_FANOUT)
                .withConsumerProperty(ConsumerConfigConstants.SHARD_GETRECORDS_MAX, "5000")
                .withConsumerProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS, "5000")
                .withConsumerProperty(ConsumerConfigConstants.SHARD_GETRECORDS_BACKOFF_BASE, "2000")
                .withConsumerProperty(ConsumerConfigConstants.SHARD_GETRECORDS_BACKOFF_MAX, "10000")
                .withConsumerProperty(ConsumerConfigConstants.SHARD_GETRECORDS_BACKOFF_EXPONENTIAL_CONSTANT, "1.5")
                .withConsumerProperty(ConsumerConfigConstants.SHARD_GETRECORDS_RETRIES, "100")
                .withConsumerProperty(ConsumerConfigConstants.SHARD_DISCOVERY_INTERVAL_MILLIS, "120000")
                .withConsumerProperty(ConsumerConfigConstants.SHARD_USE_ADAPTIVE_READS, "true")
                .build();

    }

    public static final IngressSpec<ExampleProtobuf.Envelope> DEFAULT_KINESIS_INGRESS =
            createKinesisIngress(INGRESS_ID, Configuration.INGRESS_KINESIS_STREAM_NAME);
    
}