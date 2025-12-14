package com.flow.collector.controller;

import com.flow.collector.TraceStore;
import com.flow.collector.model.CollectedSpan;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/spans")
public class SpanIngestController {

    private final TraceStore store;

    public SpanIngestController(TraceStore store) {
        this.store = store;
    }

    @PostMapping
    public void ingest(@RequestBody List<CollectedSpan> spans) {
        store.ingest(spans);
    }

    @GetMapping("/{traceId}")
    public List<CollectedSpan> get(@PathVariable String traceId) {
        List<CollectedSpan> spans = store.get(traceId);
        return spans != null ? spans : List.of();
    }
}
