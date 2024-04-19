package com.example.stateful_functions.util;

import com.example.stateful_functions.protobuf.ExampleProtobuf;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class TestMessageLoader implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(TestMessageLoader.class);

    public final List<ExampleProtobuf.Envelope> loadMessages(String path) {
        try {
            final String resourceContents = Resources.toString(Resources.getResource(path), StandardCharsets.UTF_8);

            List<ExampleProtobuf.Envelope> events = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new StringReader(resourceContents));
            String line = reader.readLine();
            while (line != null) {
                events.add(ExampleProtobuf.Envelope.newBuilder().setPayload(line).build());
                line = reader.readLine();
            }

            return events;

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

}
