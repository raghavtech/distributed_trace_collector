package com.flow.collector;

import com.flow.collector.model.CollectedSpan;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/trace")
public class TraceViewController {

    // Reuse your existing store (inject it or reference it)
    // For simplicity, assume you expose it as a singleton or bean.
    private final TraceStore store;

    public TraceViewController(TraceStore store) {
        this.store = store;
    }

    @GetMapping("/{traceId}/tree")
    public TraceTreeResponse getTree(@PathVariable String traceId) {
        List<CollectedSpan> spans = store.get(traceId);

        if (spans == null || spans.isEmpty()) {
            return TraceTreeResponse.notFound(traceId);
        }

        return TraceTreeResponse.from(traceId, spans);
    }
}
