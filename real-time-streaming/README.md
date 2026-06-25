# CMR Realtime Architecture Skeleton

This directory is a repo-shaped skeleton for adding realtime ingest, indexing, and access
capabilities to the Common Metadata Repository without modifying the local CMR checkout.

It mirrors likely integration points in the current CMR codebase:

- `message-queue-lib`: shared realtime event envelope and validation helpers.
- `ingest-app`: provider-facing realtime event ingest endpoint and publisher.
- `indexer-app`: event handler stub for projecting realtime metadata into search indexes.
- `search-app`: realtime discovery and event-stream routes.
- `subscription`: optional downstream integration for existing CMR subscription notifications.
- `docs`: architecture rationale and copy/paste integration notes.

The skeleton intentionally avoids choosing Kafka, Pulsar, Kinesis, or SNS/SQS as the durable
event log. CMR already abstracts queue publishing through `message-queue-lib`, so the first
implementation should land on the existing queue abstraction and only later swap the backing
transport if the latency and replay requirements demand it.

