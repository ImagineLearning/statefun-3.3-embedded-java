package com.example.stateful_functions.metrics;

import com.example.stateful_functions.Configuration;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.metrics.*;
import org.apache.flink.runtime.metrics.groups.AbstractMetricGroup;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter for Flink's MetricGroup which creates wrappers around Counters that also report via OpenTelemetry.
 */
public class JetstreamMetricGroupAdapter implements MetricGroup {

    private final MetricGroup adapted;
    private final OpenTelemetrySdk openTelemetrySdk;

    private static final char METRIC_SCOPE_DELIMITER = '.';

    // Exclude this list of attributes from the OpenTelemetry metrics
    private static final Set<String> EXCLUDE_ATTRIBUTES =  Set.of(
            "host",             // ="10.89.0.23"
            "job_id",           // ="279f294c8e2048d912878c11fd1f1348"
            "operator_id",      // ="6b87a4870d0e21cecbbe271bd893cfcc"
            // "operator_name",    // ="functions"
            "subtask_index",    // ="0"
            "task_attempt_id",  // ="7dfc52028fab716222444f4f29d14905"
            "task_attempt_num", // ="0"
            "task_id",          // ="31284d56d1e2112b0f20099ee448a6a9"
            "task_name",        // ="feedback-union -> functions -> Sink: rad-output-egress-egress"
            "tm_id"             // ="10.89.0.23:35601-565f93"
    );
    
    public JetstreamMetricGroupAdapter(MetricGroup adapted, OpenTelemetrySdk openTelemetrySdk) {
        this.adapted = adapted;
        this.openTelemetrySdk = openTelemetrySdk;
    }

    @Override
    public Counter counter(String name) {

        final String flinkScope;
        final Map<String,String> variables;
        if (adapted instanceof AbstractMetricGroup) {
            AbstractMetricGroup<?> abstractMetricGroup = (AbstractMetricGroup<?>) adapted;
            flinkScope = abstractMetricGroup.getLogicalScope(CharacterFilter.NO_OP_FILTER, METRIC_SCOPE_DELIMITER);
            variables = adapted.getAllVariables().entrySet().stream()
                    .map(entry -> Map.entry(StringUtils.removeEnd(StringUtils.removeStart(entry.getKey(), "<"),">"), entry.getValue()))
                    .filter(entry -> !EXCLUDE_ATTRIBUTES.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        else {
            flinkScope = "unresolvable.scope";
            variables = Map.of("adaptedClass", adapted.getClass().getName());
        }
        LongGauge otelGauge = openTelemetrySdk.getMeter(OpenTelemetrySdkUtil.getServiceName())
                .gaugeBuilder(flinkScope + METRIC_SCOPE_DELIMITER + name)
                .ofLongs()
                .build();
        AttributesBuilder attributesBuilder = Attributes.builder();
        variables.forEach(attributesBuilder::put);
        attributesBuilder.put("service.namespace", Configuration.NAMESPACE);

        return counter(name, new JetstreamMetricCounter(otelGauge, attributesBuilder.build()));
    }

    @Override
    public <C extends Counter> C counter(String name, C counter) {
        return adapted.counter(name, counter);
    }

    @Override
    public <T, G extends Gauge<T>> G gauge(String name, G g) {
        return adapted.gauge(name, g);
    }

    @Override
    public <H extends Histogram> H histogram(String name, H h) {
        return adapted.histogram(name, h);
    }

    @Override
    public <M extends Meter> M meter(String name, M m) {
        return adapted.meter(name, m);
    }

    @Override
    public MetricGroup addGroup(String name) {
        return adapted.addGroup(name);
    }

    @Override
    public MetricGroup addGroup(String key, String value) {
        return adapted.addGroup(key, value);
    }

    @Override
    public String[] getScopeComponents() {
        return adapted.getScopeComponents();
    }

    @Override
    public Map<String, String> getAllVariables() {
        return adapted.getAllVariables();
    }

    @Override
    public String getMetricIdentifier(String metricName) {
        return adapted.getMetricIdentifier(metricName);
    }

    @Override
    public String getMetricIdentifier(String metricName, CharacterFilter characterFilter) {
        return adapted.getMetricIdentifier(metricName, characterFilter);
    }
}
