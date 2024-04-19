package com.example.stateful_functions.egress;

import com.example.stateful_functions.Configuration;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import org.apache.flink.statefun.sdk.io.EgressIdentifier;
import org.apache.flink.statefun.sdk.io.EgressSpec;
import org.apache.flink.statefun.sdk.kinesis.auth.AwsCredentials;
import org.apache.flink.statefun.sdk.kinesis.egress.KinesisEgressBuilder;

import java.util.Properties;

public class EgressSpecs {

    private static final class PropertiesBuilder {
        final Properties props = new Properties();

        public PropertiesBuilder property(String name, String value) {
            props.setProperty(name,value);
            return this;
        }

        public Properties build() {
            return props;
        }
    }

    public static final EgressIdentifier<ExampleProtobuf.Envelope> ID =
            new EgressIdentifier<>("default", "output-egress", ExampleProtobuf.Envelope.class);

    public static final EgressSpec<ExampleProtobuf.Envelope> kinesisEgress =
            KinesisEgressBuilder.forIdentifier(ID)
                    .withAwsRegion(Configuration.getAwsRegion())
                    .withAwsCredentials(AwsCredentials.fromDefaultProviderChain())
                    .withMaxOutstandingRecords(100)
                    .withSerializer(EgressSerializer.class)
                    .withProperties(
                            new PropertiesBuilder().property("VerifyCertificate", Configuration.IS_LOCAL_DEV ? "false" : "true").build())
                    .build();

}

