package com.example.stateful_functions.util;

import com.example.stateful_functions.protobuf.ExampleProtobuf;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Component
public class TestMessageSource implements SourceFunction<ExampleProtobuf.Envelope>, Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(TestMessageSource.class);

    @Autowired TestMessageLoader testMessageLoader;

    final List<ExampleProtobuf.Envelope> envelopes = new ArrayList<>();

    public void setEventsResourcePath(String eventsResourcePath) {
        List<ExampleProtobuf.Envelope> testMessages = inputFixture(eventsResourcePath);
        testMessages.forEach(this::add);
    }

    public void add(List<ExampleProtobuf.Envelope> items) {
        this.envelopes.addAll(items);
    }

    public void add(ExampleProtobuf.Envelope item) {
        this.envelopes.add(item);
    }

    class TestActionDetails implements Serializable {
        String action;
        long durationMillis;
        String comment;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public long getDurationMillis() {
            return durationMillis;
        }

        public void setDurationMillis(long durationMillis) {
            this.durationMillis = durationMillis;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }

    @Override
    public void run(SourceContext<ExampleProtobuf.Envelope> sourceContext) {
        long delayBetweenEvents = 0;

        try {
            for (ExampleProtobuf.Envelope envelope : envelopes) {
                String event = envelope.getPayload();
                if (event.startsWith("test.action:")) {
                    LOG.info("{}", event);

                    String[] actionFields =  event.split(":");
                    if (actionFields.length != 3) {
                        LOG.warn("Expected three fields delimited by colon in {}", event);
                        continue;
                    }
                    String action = actionFields[1];

                    switch (action) {
                        case "delay":
                            Thread.sleep(Long.parseLong(actionFields[2]));
                            break;
                        case "delay-between-events":
                            delayBetweenEvents = Long.parseLong(actionFields[2]);
                            break;
                        case "comment":
                            // Ignore
                            break;
                        default:
                            LOG.warn("Unrecognized test action: {}", action);
                    }
                }
                else {
                    sourceContext.collect(envelope);
                    Thread.sleep(delayBetweenEvents);
                }
            }

            // Allow some time for messages to propagate, because w/o doing this the Harness shuts pre-maturely and bad things happen.
            // E.g. this: java.lang.IllegalStateException: Mailbox is in state CLOSED, but is required to be in state OPEN for put operations

            String sleepSeconds = System.getProperty("message.source.sleep.seconds", "1");
            LOG.info("Will sleep for {} seconds to allow messages to propagate.  Change this delay with -Dmessage.source.sleep.seconds=<numSeconds>", sleepSeconds);
            final long sleepUntilMillis = System.currentTimeMillis() + Long.valueOf(sleepSeconds)*1000L;
            long sleepForMillis = sleepUntilMillis - System.currentTimeMillis();
            boolean manualLoopExit = false;
            while (!manualLoopExit && sleepForMillis > 0) {
                try {
                    // Sleep for max of 1 second at a time to allow manualLoopExit to
                    Thread.sleep(Math.min(1000L, sleepForMillis));
                } catch (InterruptedException e) {
                    // Ignore
                }
                sleepForMillis = sleepUntilMillis - System.currentTimeMillis();
            }
        }
        catch (Exception x) {
            LOG.error(x.getMessage(), x);
        }
    }

    @Override
    public void cancel() {}

    List<ExampleProtobuf.Envelope> inputFixture(String path) {
        return testMessageLoader.loadMessages(path);
    }
}
