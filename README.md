# Stereo RAG
RAG system meets speech recognition for conversations.  


## How is it special? 
* uses the concept of turn 
* tags conversation by speaker


## Build

Requires JDK 25 and Maven.

```
mvn clean install
```

If `mvn` picks up a different JDK version, set `JAVA_HOME` to a JDK 25 install first:

```
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
```

Run tests only:

```
mvn test
```





## AWS/Kinesis demo 
* **Amazon Connect**: phone number service 
* **Kinesis Video Streams** : stream created automatically by Connect
  * Kinesis uses 8 kHz sampling rate
  * One Kinesis video stream is used per active call
  * IAM permissions matter here — your consumer app needs Kinesis Video Streams read access



```
Customer call ──┬──→ Agent softphone (WebRTC, browser, live conversation)
                └──→ Kinesis Video Streams (parallel copy, both tracks)
                     └──→ stereo RAG (KVS consumer, live processing)
```

```
call ──┬──→ softphone (browser)
       └──→ Kinesis Video Streams 
              └──→ stereo RAG 
```




## Demo idea

# Live Call Assist Demo — Architecture Overview

A demo showing real-time agent assist on top of Amazon Connect: live call
audio is transcribed, processed by a small local LLM for intent/entity
extraction, fed into a RAG lookup, and the resulting document hints are
pushed live into the agent's browser alongside the softphone.

## Goal

While a customer is on a call with an agent, automatically surface relevant
knowledge-base documents in the agent's UI — without the agent having to
search for anything manually.

## Pipeline

```
Customer calls Amazon Connect number
        │
        ▼
Contact flow routes to agent
        │
        ├──→ Agent softphone (WebRTC, embedded via Streams JS in our web page)
        │
        └──→ Kinesis Video Streams (Connect forks a copy of both audio legs)
             │
             ▼
      Java consumer (Kinesis Video Streams Parser Library)
             │
             ▼
           ASR 
             │
             ▼
      Text buffering (per contact-id)
             │
             ▼
      Kafka topic: raw-transcripts
             │
             ▼
      Kafka Streams topology
        ├─ Small local LLM (Gemma 4 E2B/E4B) → intent + entity extraction
        └─ RAG lookup against knowledge base, using extracted intent/entities
             │
             ▼
      Kafka topics: transcript-events, doc-hints  (keyed by contact-id)
             │
             ▼
      WebSocket gateway (Kafka consumer → fan-out to browser clients)
             │
             ▼
      Agent's browser: live transcript + auto-opened document hints
```

## Components

| Component | Role |
|---|---|
| **Amazon Connect** | Phone number, IVR, call routing, softphone/agent workspace |
| **Kinesis Video Streams** | Live audio fork from Connect — separate tracks for customer and agent, does not affect the live call |
| **Java consumer** | Reads KVS via `GetMedia` + Kinesis Video Streams Parser Library, demuxes MKV into PCM |
| **ASR** | Converts PCM audio to text (streaming) |
| **Text buffering** | Accumulates ASR fragments per contact-id until a flush trigger (silence/end-of-utterance, time cap, or size cap), then emits a coherent chunk downstream — avoids calling the LLM on every tiny partial fragment |
| **Gemma 4 (E2B/E4B)** | Small local LLM, run via Ollama or `java-llama.cpp`, used only for lightweight intent + named-entity extraction — not general chat |
| **RAG lookup** | Uses extracted intent/entities to query a knowledge base / vector store for relevant documents |
| **Kafka Streams** | Orchestrates the buffering → LLM → RAG topology; per-contact state via local state stores; topics decouple producer (backend) from consumers (gateway, future dashboards, logging) |
| **WebSocket gateway** | Thin service: consumes `transcript-events` / `doc-hints` topics, forwards to the correct browser connection by contact-id |
| **Agent web page** | Embeds Connect's CCP (softphone) via Streams JS; separately holds a WebSocket connection for live transcript + document hints |

## Key design decisions

- **Audio fork, not audio redirect** — Kinesis Video Streams is a live copy of
  the call audio, not part of the WebRTC call path. If the processing
  pipeline fails, the call is unaffected.
- **Small local LLM for intent/NER, not a general chat model** — lower
  latency, lower cost per call, no external API round-trip for a task that
  doesn't need a large model. Apache 2.0 licensed (Gemma 4), simple to run
  locally via Ollama.
- **Buffer before calling the LLM** — raw ASR fragments are noisy and
  partial; buffering into coherent chunks (by silence detection, time cap,
  or size cap) produces better LLM input and avoids redundant calls.
- **Kafka topics decouple stages** — any future consumer (supervisor
  dashboard, analytics, logging) can subscribe to the same topics without
  touching the producer side.
- **Messaging system → browser needs a bridge** — Kafka (or any broker) does
  not speak to browsers directly; a small WebSocket gateway service consumes
  the output topics and fans out to connected clients keyed by contact-id.

## Open / to-decide

- ASR provider and whether it exposes end-of-utterance / silence detection
  for buffering triggers.
- Knowledge base / vector store choice for the RAG step.
- Local Kafka (Docker Compose) vs. managed (MSK) for the demo environment.





## Build and test without AWS 

```
[Fake audio source] → ASR → buffering → Kafka Streams (Gemma 4 + RAG) → doc-hints/transcript topics → WebSocket gateway → Browser UI
```


