package com.flow.collector.model;

public class CollectedSpan {

    public String traceId;
    public String spanId;
    public String parentSpanId;

    public String name;
    public String kind; // SERVER, CLIENT, INTERNAL

    public String serviceName;
    public String nodeId;

    public long startTimeMicros;
    public long durationMicros;
}
