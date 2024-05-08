package com.example.stateful_functions;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.statefun.sdk.kinesis.auth.AwsRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public class Configuration {

    private static Logger LOG = LoggerFactory.getLogger(Configuration.class);

    public static Properties properties = getProperties();

    public static String INGRESS_KINESIS_STREAM_NAME = properties.getOrDefault("EVENTS_INGRESS_STREAM_DEFAULT", "example-ingress-stream").toString();
    public static String EGRESS_KINESIS_STREAM_NAME = properties.getOrDefault("EVENTS_EGRESS_STREAM_DEFAULT", "example-egress-stream").toString();

    public static boolean IS_LOCAL_DEV = properties.getOrDefault("IS_LOCAL_DEV", "false").equals("true");
    public static boolean USE_ENHANCED_FANOUT = properties.getOrDefault("USE_ENHANCED_FANOUT", "true").equals("true");
    public static String ENHANCED_FANOUT_NAME = properties.getOrDefault("ENHANCED_FANOUT_NAME", "example-enhanced-fanout").toString();


    public static final AwsRegion getAwsRegion() {

        if (properties.containsKey("AWS_REGION") && !properties.containsKey("AWS_ENDPOINT")){
            return AwsRegion.ofId(properties.get("AWS_REGION").toString());
        } else {

            AwsRegion region = AwsRegion.ofCustomEndpoint(
                    properties.getOrDefault("AWS_ENDPOINT", "https://localhost:4566").toString(),
                    properties.getOrDefault("AWS_REGION", "us-east-1").toString()
            );

            try {
                Field field = region.getClass().getDeclaredField("serviceEndpoint");
                field.setAccessible(true);
                String serviceEndpoint = (String)field.get(region);
                if (serviceEndpoint.toLowerCase(Locale.ROOT).equals("https://host.docker.internal:4566"))
                {
                    field.set(region, "http://host.docker.internal:4566");
                }
            } catch (Exception ex) {
            }
            return region;

        }
    }

    private static final Properties getProperties() {
        // System.getProperties + System.getenv()

        Properties properties = System.getProperties();
        Map<String, String> env = System.getenv();

        properties.putAll(env);

        // If deployed in AWS Managed Flink, then get our config from
        // KinesisAnalyticsRuntime.getApplicationProperties().get("StatefunApplicationProperties")
        try {
            Optional.ofNullable(KinesisAnalyticsRuntime.getApplicationProperties())
                    .map(ap -> ap.get("StatefunApplicationProperties"))
                    .filter(Objects::nonNull)
                    .ifPresent(ap -> properties.putAll(ap));
        }
        catch (Exception x) {
            LOG.warn(x.getMessage(), x);
        }
        return properties;
    }

}
