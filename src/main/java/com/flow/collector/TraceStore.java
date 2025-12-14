package com.flow.collector;

import com.flow.collector.model.CollectedSpan;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TraceStore {

    private final Map<String, List<CollectedSpan>> traces = new ConcurrentHashMap<>();

    public void ingest(List<CollectedSpan> spans) {
        for (CollectedSpan s : spans) {
            traces.computeIfAbsent(s.traceId, k -> Collections.synchronizedList(new ArrayList<>()))
                  .add(s);
        }
    }

    public List<CollectedSpan> get(String traceId) {
        return traces.getOrDefault(traceId, List.of());
    }
}
