package com.example.stateful_functions.ingress;

import com.example.stateful_functions.protobuf.ExampleProtobuf;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import org.apache.flink.statefun.sdk.kinesis.ingress.IngressRecord;
import org.apache.flink.statefun.sdk.kinesis.ingress.KinesisIngressDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;

public class IngressDeserializer implements KinesisIngressDeserializer<ExampleProtobuf.Envelope> {

    private static Logger LOG = LoggerFactory.getLogger(IngressDeserializer.class);

    public static final EventFormat CLOUD_EVENT_FORMAT = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);

    @Override
    public ExampleProtobuf.Envelope deserialize(IngressRecord ingressRecord) {
        try {
            return ExampleProtobuf.Envelope.newBuilder().setPayload(new String(ingressRecord.getData(), UTF_8)).build();
        }
        catch (Throwable t) {
            LOG.debug("Failed to deserialize event", t);
            return null;
        }
    }
}

