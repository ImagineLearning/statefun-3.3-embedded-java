package com.example.stateful_functions.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.SimpleCounter;

public class JetstreamMetricCounter implements Counter {

    private final Counter flinkCounter = new SimpleCounter();

    private final LongGauge otelGauge;
    private final Attributes otelAttributes;

    public JetstreamMetricCounter(LongGauge otelGauge, Attributes otelAttributes) {
        this.otelGauge = otelGauge;
        this.otelAttributes = otelAttributes;
    }

    @Override
    public void inc() {
        flinkCounter.inc();
        otelGauge.set(flinkCounter.getCount(), otelAttributes);
    }

    @Override
    public void inc(long l) {
        otelGauge.set(flinkCounter.getCount() + l, otelAttributes);
        flinkCounter.inc(l);

    }

    @Override
    public void dec() {
        flinkCounter.dec();
        otelGauge.set(flinkCounter.getCount(), otelAttributes);
    }

    @Override
    public void dec(long l) {
        otelGauge.set(flinkCounter.getCount() - l, otelAttributes);
        flinkCounter.dec(l);
    }

    @Override
    public long getCount() {
        return flinkCounter.getCount();
    }
}
