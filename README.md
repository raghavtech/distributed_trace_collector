Each JVM runs an independent Flow Java Agent with its own HTTP exporter.
Spans are sent asynchronously via HTTP POST to the Flow Collector.
There is no persistent connection or coordination between services.


┌────────────────────────────┐
│          Client            │
│   (Browser / curl / API)   │
└─────────────┬──────────────┘
              │ HTTP Request
              ▼
┌────────────────────────────────────────────────┐
│               Service A (JVM)                  │
│        (Tomcat / Spring Boot / Servlet)        │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │           Flow Java Agent                │  │
│  │  (Byte Buddy Instrumentation)            │  │
│  │                                          │  │
│  │  SERVER span                             │  │
│  │  GET /api/employees                      │  │
│  │      │                                   │  │
│  │      ├─ INTERNAL span                    │  │
│  │      │   EmployeeService                 │  │
│  │      │                                   │  │
│  │      └─ CLIENT span                      │  │
│  │          HTTP GET /api/payroll           │  │
│  │          (injects traceparent header)    │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│   Batched span export (JSON over HTTP)         │
│                │                               │
│                ▼                               │
│     ┌───────────────────────────────┐          │
│     │        Flow Collector         │◀─────────┘
│     └───────────────────────────────┘          │ 
│                                                │
└────────────────────────────────────────────────┘
                   │
                   │ HTTP (traceparent)
                   ▼
┌────────────────────────────────────────────────┐
│               Service B (JVM)                  │
│        (Tomcat / Spring Boot / Servlet)        │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │           Flow Java Agent                │  │
│  │                                          │  │
│  │  SERVER span                             │  │
│  │  GET /api/payroll                        │  │
│  │      │                                   │  │
│  │      └─ INTERNAL span                    │  │
│  │          PayrollController               │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│   Batched span export (JSON over HTTP)         │
│                │                               │
│                ▼                               │
│     ┌────────────────────────────────┐         │
│     │        Flow Collector          │◀────────┘
│     └────────────────────────────────┘         │
│                                                │
└────────────────────────────────────────────────┘


                 ┌──────────────────────────────────────────────┐
                 │              Flow Collector                  │
                 │        (Spring Boot / REST API)              │
                 │                                              │
                 │  ┌───────────────────────────────────────┐   │
                 │  │        TraceStore (in-memory)         │   │
                 │  │                                       │   │
                 │  │  traceId → spans from Service A + B   │   │
                 │  └───────────────────────────────────────┘   │
                 │                                              │
                 │  ┌───────────────────────────────────────┐   │
                 │  │   Trace Tree Builder                  │   │
                 │  │   • Parent/child linking              │   │
                 │  │   • Depth computation                 │   │
                 │  │   • Self-time calculation             │   │
                 │  │   • Critical path detection           │   │
                 │  └───────────────────────────────────────┘   │
                 │                                              │
                 │  REST APIs:                                  │
                 │   • POST /spans                              │
                 │   • GET  /spans/{traceId}                    │
                 │   • GET  /trace/{traceId}/tree               │
                 └──────────────────────────────────────────────┘
                                   │
                                   │ JSON
                                   ▼
┌───────────────────────────────────────────────────────────────┐
│            Trace Visualization UI (HTML / JS)                 │
│                                                               │
│  • Dynatrace-style call tree                                  │
│  • Cross-service execution flow                               │
│  • Critical path highlighting                                 │
│  • Duration & self-time per node                              │
└───────────────────────────────────────────────────────────────┘

