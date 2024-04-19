package com.example.stateful_functions.cloudevents;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class ExampleCloudEventJsonFormat {

    private static Logger LOG = LoggerFactory.getLogger(ExampleCloudEventJsonFormat.class);

    public static final EventFormat CLOUD_EVENT_FORMAT = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);

    public CloudEvent deserialize(String serializedEvent) {
        try {
            return CLOUD_EVENT_FORMAT.deserialize(serializedEvent.getBytes(StandardCharsets.UTF_8));
        }
        catch (Throwable t) {
            LOG.debug("Failed to deserialize event", t);
            return null;
        }
    }


    public String serialize(CloudEvent cloudEvent) {
        return new String(CLOUD_EVENT_FORMAT.serialize(cloudEvent), StandardCharsets.UTF_8);
    }
}

