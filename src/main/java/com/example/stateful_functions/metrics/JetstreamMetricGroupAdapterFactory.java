package com.example.stateful_functions.metrics;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.statefun.flink.core.metrics.MetricGroupAdapterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JetstreamMetricGroupAdapterFactory implements MetricGroupAdapterFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JetstreamMetricGroupAdapterFactory.class);

    @Override
    public MetricGroup createMetricGroupAdapter(MetricGroup metricGroup) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = JetstreamMetricGroupAdapterFactory.class.getClassLoader();
            }
            Class<?> sdkUtilClass = classLoader.loadClass("com.example.stateful_functions.metrics.OpenTelemetrySdkUtil");
            Object sdk =  sdkUtilClass.getMethod("getSdk").invoke(null);
            if (sdk instanceof OpenTelemetrySdk) {
                LOG.warn("Creating JetstreamMetricGroupAdapter with OpenTelemetry SDK: {}@{}", sdk.getClass().getName(), sdk.hashCode());
                return new JetstreamMetricGroupAdapter(metricGroup, (OpenTelemetrySdk) sdk);
            }
        } catch (Exception x) {
            LOG.error(x.getMessage(), x);
            LOG.warn("Returning original MetricGroup due to errors resolving SDK");
        }

        LOG.warn("OpenTelemetry SDK is disabled. Returning original MetricGroup.");
        return metricGroup;
    }
}
