Each JVM runs an independent Flow Java Agent with its own HTTP exporter.
Spans are sent asynchronously via HTTP POST to the Flow Collector.
There is no persistent connection or coordination between services.



<img width="1558" height="691" alt="image" src="https://github.com/user-attachments/assets/30657c62-c842-4b4e-93ab-71eb818e2756" />
