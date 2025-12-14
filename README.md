Each JVM runs an independent Flow Java Agent with its own HTTP exporter.
Spans are sent asynchronously via HTTP POST to the Flow Collector.
There is no persistent connection or coordination between services.



