package com.example.stateful_functions;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.statefun.sdk.kinesis.auth.AwsRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.lang.reflect.Field;
import java.util.*;

public class Configuration {


    private static Logger LOG = LoggerFactory.getLogger(Configuration.class);

    public static Properties properties = getProperties();

    public static final String ENVIRONMENT = properties.getOrDefault("ENVIRONMENT", "local").toString();
    public static String NAMESPACE = properties.getOrDefault("NAMESPACE", "jetstream-local").toString();

    public static String AWS_REGION = properties.getOrDefault("AWS_REGION", "us-east-1").toString();
    public static boolean OTEL_SDK_DISABLED = properties.getOrDefault("OTEL_SDK_DISABLED", "true").equals("true");
    public static String OTEL_SERVICE_NAME = properties.getOrDefault("OTEL_SERVICE_NAME", "jetstream-statefun").toString();
    public static String OTEL_EXPORTER_OTLP_ENDPOINT = properties.getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "").toString();
    public static String OTEL_EXPORTER_OTLP_HEADERS = properties.getOrDefault("OTEL_EXPORTER_OTLP_HEADERS", "").toString();
    public static String OTEL_EXPORTER_OTLP_PROTOCOL = properties.getOrDefault("OTEL_EXPORTER_OTLP_PROTOCOL", "").toString();
    public static String OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE =
            properties.getOrDefault("OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE","Delta").toString();
    public static long OTEL_METRIC_EXPORT_INTERVAL =
            Long.parseLong(properties.getOrDefault("OTEL_METRIC_EXPORT_INTERVAL", "60000").toString());
    public static long OTEL_METRIC_EXPORT_TIMEOUT =
            Long.parseLong(properties.getOrDefault("OTEL_METRIC_EXPORT_TIMEOUT", "30000").toString());


    public static String INGRESS_KINESIS_STREAM_NAME = properties.getOrDefault("EVENTS_INGRESS_STREAM_DEFAULT", "example-ingress-stream").toString();
    public static String EGRESS_KINESIS_STREAM_NAME = properties.getOrDefault("EVENTS_EGRESS_STREAM_DEFAULT", "example-egress-stream").toString();

    public static boolean IS_LOCAL_DEV = properties.getOrDefault("IS_LOCAL_DEV", "false").equals("true");


    public static boolean USE_ENHANCED_FANOUT = properties.getOrDefault("USE_ENHANCED_FANOUT", "true").equals("true");
    public static String ENHANCED_FANOUT_NAME = properties.getOrDefault("ENHANCED_FANOUT_NAME", "example-enhanced-fanout").toString();

    public static String APP_VERSION = properties.getOrDefault("app.version", "0.1").toString();


    public static final AwsRegion getAwsRegion() {
        return getAwsRegion(properties);
    }

    private static AwsRegion getAwsRegion(Properties _properties) {

        if (_properties.containsKey("AWS_REGION") && !_properties.containsKey("AWS_ENDPOINT")){
            return AwsRegion.ofId(_properties.get("AWS_REGION").toString());
        } else {

            AwsRegion region = AwsRegion.ofCustomEndpoint(
                    _properties.getOrDefault("AWS_ENDPOINT", "https://localhost:4566").toString(),
                    _properties.getOrDefault("AWS_REGION", "us-east-1").toString()
            );

            try {
                Field field = region.getClass().getDeclaredField("serviceEndpoint");
                field.setAccessible(true);
                String serviceEndpoint = (String)field.get(region);
                if (serviceEndpoint.toLowerCase(Locale.ROOT).equals("https://host.docker.internal:4566"))
                {
                    field.set(region, "http://host.docker.internal:4566");
                }
            }
            catch (Exception ex) {
            }
            return region;
        }
    }

    private static Properties getProperties() {
        // System.getProperties + System.getenv()

        Properties properties = new Properties();
        try {
            properties.load(Configuration.class.getResourceAsStream("/application.properties"));
        } catch (Exception x) {
            LOG.warn(x.getMessage(), x);
        }

        properties.putAll(System.getProperties());
        properties.putAll(System.getenv());

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

        return resolveSecretValueReferences(properties);
    }

    // Replace property values of the form "!secret:<secret-name>" with the actual secret value from AWS Secrets Manager
    private static Properties resolveSecretValueReferences(Properties properties) {

        // Find property key/value pairs where the value has a secret reference
        Map<String,String> propertiesWithSecretsResolved = new HashMap<>();
        properties.stringPropertyNames().forEach(propertyName -> {
            String propertyValue = properties.getProperty(propertyName);
            if (propertyValue.startsWith("!secret:")) {
                String secretName = propertyValue.substring(8);
                propertiesWithSecretsResolved.put(propertyName, secretName);
            }
        });

        // Nothing to do if there are no secrets to resolve
        if (propertiesWithSecretsResolved.isEmpty()) {
            return properties;
        }

        String awsRegionId = properties.get("AWS_REGION").toString();
        LOG.info("Resolving configuration secrets using AWS_REGION={}", awsRegionId);

        // Create a Secrets Manager client
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(awsRegionId))
                .build()) {

            // For each property with a secret reference, get the secret value from AWS Secrets Manager and replace
            // the value in the properties map
            propertiesWithSecretsResolved.entrySet().forEach(entry -> {
                String propertyName = entry.getKey();
                String secretName = entry.getKey();

                GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                        .secretId(secretName)
                        .build();

                try {
                    properties.put(propertyName, client.getSecretValue(getSecretValueRequest).secretString());
                }
                catch (Exception e) {
                    LOG.warn("Failed to get secret value for {}", secretName, e);
                }
            });
        }
        catch (Exception e) {
            LOG.warn("Failed init SecretsManagerClient: ", e);
        }

        return properties;
    }
}
