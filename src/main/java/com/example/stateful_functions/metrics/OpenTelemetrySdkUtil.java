package com.example.stateful_functions.metrics;

import com.example.stateful_functions.Configuration;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.protocol.types.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class OpenTelemetrySdkUtil {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetrySdkUtil.class);

    private static boolean initialized = false;
    private static OpenTelemetrySdk sdk = null;

    private static OpenTelemetrySdk initialized(OpenTelemetrySdk _sdk) {
        initialized = true;
        sdk = _sdk;
        return sdk;
    }

    public static String getServiceName() {
        return Configuration.OTEL_SERVICE_NAME + '-' + Configuration.ENVIRONMENT;
    }

    public static synchronized OpenTelemetrySdk getSdk() {
        if (initialized) {
            return sdk;
        }

        // In the logging output, obfuscate headers values where 'key' is in the header name
        String headersToLog;
        if (StringUtils.isNotEmpty(Configuration.OTEL_EXPORTER_OTLP_HEADERS)) {
            List<String> items = new ArrayList<>();
            String[] headerArray = Configuration.OTEL_EXPORTER_OTLP_HEADERS.split(",");
            for (String header : headerArray) {
                String[] keyValue = header.split("=");
                if (keyValue.length == 2) {
                    if (keyValue[0].contains("key")) {
                        items.add(String.format("%s=%s", keyValue[0],
                                new String(Base64.getEncoder().encode(keyValue[1].getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)));
                    }
                    else {
                        items.add(header);
                    }
                }
                else {
                    items.add(header);
                }
            }
            headersToLog = StringUtils.joinWith(",", items);
        }
        else {
            headersToLog = Configuration.OTEL_EXPORTER_OTLP_HEADERS;
        }

        LOG.warn("Initializing with OTEL_SDK_DISABLED={}, OTEL_EXPORTER_OTLP_PROTOCOL='{}', OTEL_EXPORTER_OTLP_ENDPOINT='{}', OTEL_EXPORTER_OTLP_HEADERS='{}'",
                Configuration.OTEL_SDK_DISABLED,
                Configuration.OTEL_EXPORTER_OTLP_PROTOCOL,
                Configuration.OTEL_EXPORTER_OTLP_ENDPOINT,
                headersToLog);

        if (Configuration.OTEL_SDK_DISABLED) {
            LOG.warn("OpenTelemetry SDK is disabled. Skipping initialization.");
            return initialized(null);
        }

        if (StringUtils.isEmpty(Configuration.OTEL_EXPORTER_OTLP_PROTOCOL)) {
            LOG.warn("OTLP protocol is not set. Skipping initialization.");
            return initialized(null);
        }

        if (StringUtils.isEmpty(Configuration.OTEL_EXPORTER_OTLP_ENDPOINT)) {
            LOG.warn("OTLP endpoint is not set. Skipping initialization.");
            return initialized(null);
        }

        Resource serviceNameResourceResource = Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), getServiceName()));

        Map<String,String> headers = new HashMap<>();

        if (StringUtils.isNotEmpty(Configuration.OTEL_EXPORTER_OTLP_HEADERS)) {
            String[] headerArray = Configuration.OTEL_EXPORTER_OTLP_HEADERS.split(",");
            for (String header : headerArray) {
                String[] keyValue = header.split("=");
                if (keyValue.length == 2) {
                    headers.put(keyValue[0], keyValue[1]);
                }
                else {
                    LOG.warn("Header value '{}' parsed to {} token(s). Expected exactly 2", header, keyValue.length);
                }
            }
        }

        final AggregationTemporalitySelector aggregationTemporalitySelector;
        switch (Configuration.OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE.toUpperCase()) {
            case "DELTA":
                aggregationTemporalitySelector = AggregationTemporalitySelector.deltaPreferred();
                break;
            case "CUMULATIVE":
                aggregationTemporalitySelector = AggregationTemporalitySelector.alwaysCumulative();
                break;
            case "LOWMEMORY":
                aggregationTemporalitySelector = AggregationTemporalitySelector.lowMemory();
                break;
            default:
                LOG.warn("Unsupported OTLP metrics temporality preference: '{}'. Defaulting to DELTA.", Configuration.OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE);
                aggregationTemporalitySelector = AggregationTemporalitySelector.deltaPreferred();
                break;
        }

        final MetricExporter metricExporter;
        final String metricEndpoint = Configuration.OTEL_EXPORTER_OTLP_ENDPOINT + "/v1/metrics";
        switch (Configuration.OTEL_EXPORTER_OTLP_PROTOCOL) {
            case "http/protobuf":
                LOG.info("Using OTLP HTTP/Protobuf");
                OtlpHttpMetricExporterBuilder builder = OtlpHttpMetricExporter.builder()
                        .setHeaders(() -> headers)
                        .setEndpoint(metricEndpoint)
                        .setTimeout(Duration.ofMillis(Configuration.OTEL_METRIC_EXPORT_TIMEOUT))
                        .setAggregationTemporalitySelector(aggregationTemporalitySelector);
                metricExporter = builder.build();
                break;
            case "grpc":
                LOG.info("Using OTLP gRPC");
                metricExporter = OtlpGrpcMetricExporter.builder()
                        .setHeaders(() -> headers)
                        .setEndpoint(metricEndpoint)
                        .setTimeout(Duration.ofMillis(Configuration.OTEL_METRIC_EXPORT_TIMEOUT))
                        .setAggregationTemporalitySelector(aggregationTemporalitySelector)
                        .build();
                break;
            default:
                LOG.warn("Unsupported OTLP protocol: '{}'.", Configuration.OTEL_EXPORTER_OTLP_PROTOCOL);
                return initialized(null);
        }
        MetricReader periodicReader =
                PeriodicMetricReader.builder(metricExporter)
                        .setInterval(Duration.ofMillis(Configuration.OTEL_METRIC_EXPORT_INTERVAL))
                        .build();

        SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
                .addResource(serviceNameResourceResource)
                .registerMetricReader(periodicReader)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(sdkMeterProvider::close));
        sdk =
                OpenTelemetrySdk.builder()
                        .setMeterProvider(sdkMeterProvider)
                        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                        .build();
        return initialized(sdk);
    }
}
