package com.flow.collector;

import com.flow.collector.model.CollectedSpan;

import java.util.*;
import java.util.stream.Collectors;

public class TraceTreeResponse {

    public String traceId;
    public String rootSpanId;
    public List<Node> nodes;              // flat nodes for UI
    public List<String> criticalPath;     // spanIds on critical path
    public String status;                // OK / NOT_FOUND
    public String message;

    public static class Node {
        public String spanId;
        public String parentSpanId;
        public String name;
        public String kind;
        public String serviceName;
        public String nodeId;

        public long startTimeMicros;
        public long durationMicros;

        // derived
        public long endTimeMicros;
        public int depth;
        public long selfTimeMicros;
        public List<String> children = new ArrayList<>();
    }

    public static TraceTreeResponse notFound(String traceId) {
        TraceTreeResponse r = new TraceTreeResponse();
        r.traceId = traceId;
        r.status = "NOT_FOUND";
        r.message = "Trace not found";
        r.nodes = List.of();
        r.criticalPath = List.of();
        return r;
    }

    public static TraceTreeResponse from(String traceId, List<CollectedSpan> spans) {
        TraceTreeResponse r = new TraceTreeResponse();
        r.traceId = traceId;
        r.status = "OK";
        r.message = "OK";

        // 1) Build nodes map
        Map<String, Node> byId = new HashMap<>();
        for (CollectedSpan s : spans) {
            Node n = new Node();
            n.spanId = s.spanId;
            n.parentSpanId = s.parentSpanId;
            n.name = s.name;
            n.kind = s.kind;
            n.serviceName = s.serviceName;
            n.nodeId = s.nodeId;
            n.startTimeMicros = s.startTimeMicros;
            n.durationMicros = s.durationMicros;
            n.endTimeMicros = s.startTimeMicros + s.durationMicros;
            byId.put(n.spanId, n);
        }

        // 2) Link children
        for (Node n : byId.values()) {
            if (n.parentSpanId != null) {
                Node p = byId.get(n.parentSpanId);
                if (p != null) {
                    p.children.add(n.spanId);
                }
            }
        }

        // 3) Find root(s)
        List<Node> roots = byId.values().stream()
                .filter(n -> n.parentSpanId == null || !byId.containsKey(n.parentSpanId))
                .sorted(Comparator.comparingLong(a -> a.startTimeMicros))
                .collect(Collectors.toList());

        Node root = roots.get(0); // pick first if multiple
        r.rootSpanId = root.spanId;

        // 4) Compute depth via DFS
        computeDepth(byId, root.spanId, 0);

        // 5) Compute self-time (exclusive)
        for (Node n : byId.values()) {
            long childrenCovered = computeChildrenCoveredMicros(byId, n);
            long self = n.durationMicros - childrenCovered;
            n.selfTimeMicros = Math.max(0, self);
        }

        // 6) Compute critical path (longest path by duration)
        r.criticalPath = computeCriticalPath(byId, root.spanId);

        // 7) Return stable ordered list (depth + start time)
        r.nodes = byId.values().stream()
                .sorted(Comparator
                        .comparingInt((Node n) -> n.depth)
                        .thenComparingLong(n -> n.startTimeMicros))
                .collect(Collectors.toList());

        return r;
    }

    private static void computeDepth(Map<String, Node> byId, String spanId, int depth) {
        Node n = byId.get(spanId);
        if (n == null) return;
        n.depth = depth;
        for (String childId : n.children) {
            computeDepth(byId, childId, depth + 1);
        }
    }

    /**
     * Exclusive time approximation:
     * Sum of child durations clipped to this span window, without overlap merging.
     * Good enough for phase 1.
     */
    private static long computeChildrenCoveredMicros(Map<String, Node> byId, Node parent) {
        if (parent.children.isEmpty()) return 0;

        // Build child intervals within parent window
        List<long[]> intervals = new ArrayList<>();
        long pStart = parent.startTimeMicros;
        long pEnd = parent.endTimeMicros;

        for (String cid : parent.children) {
            Node c = byId.get(cid);
            if (c == null) continue;

            long s = Math.max(pStart, c.startTimeMicros);
            long e = Math.min(pEnd, c.endTimeMicros);
            if (e > s) intervals.add(new long[]{s, e});
        }

        // Merge overlaps
        intervals.sort(Comparator.comparingLong(a -> a[0]));
        long covered = 0;
        long curS = -1, curE = -1;

        for (long[] in : intervals) {
            if (curS < 0) {
                curS = in[0];
                curE = in[1];
            } else if (in[0] <= curE) {
                curE = Math.max(curE, in[1]);
            } else {
                covered += (curE - curS);
                curS = in[0];
                curE = in[1];
            }
        }
        if (curS >= 0) covered += (curE - curS);

        return covered;
    }

    /**
     * Critical path:
     * Choose the child chain with maximum total duration (simple longest-path DFS).
     * Phase 1 assumes tree, not DAG.
     */
    private static List<String> computeCriticalPath(Map<String, Node> byId, String rootId) {
        List<String> best = new ArrayList<>();
        dfsCritical(byId, rootId, new ArrayList<>(), best);
        return best;
    }

    private static long dfsCritical(Map<String, Node> byId, String id, List<String> path, List<String> best) {
        Node n = byId.get(id);
        if (n == null) return 0;

        path.add(id);

        long bestChildSum = 0;
        List<String> bestChildPath = null;

        for (String cid : n.children) {
            List<String> tmp = new ArrayList<>();
            long childSum = dfsCritical(byId, cid, tmp, new ArrayList<>());
            if (childSum > bestChildSum) {
                bestChildSum = childSum;
                bestChildPath = tmp;
            }
        }

        // total here = this duration + best child path sum
        long total = n.durationMicros + bestChildSum;

        // build full path = current node + best child path
        List<String> full = new ArrayList<>();
        full.add(id);
        if (bestChildPath != null) full.addAll(bestChildPath);

        // compare by total (store best full path)
        long bestTotal = sumDuration(byId, best);
        if (total > bestTotal) {
            best.clear();
            best.addAll(full);
        }

        return total;
    }

    private static long sumDuration(Map<String, Node> byId, List<String> path) {
        long sum = 0;
        for (String id : path) {
            Node n = byId.get(id);
            if (n != null) sum += n.durationMicros;
        }
        return sum;
    }
}
