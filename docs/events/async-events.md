# Async Events — Phase 12

Phase 12 publishes committed Commerce order/payment events from `commerce.outbox_events`. Business transactions insert outbox rows with the local order/payment change; a separate publisher drains `PENDING` rows and updates only `status`, `published_at` and `retry_count`.

The event envelope is stable across publishers:

- `eventId`
- `eventType`
- `eventVersion`
- `aggregateId`
- `occurredAt`
- `correlationId`
- `payload`

Commerce supports three publisher modes through `commerce.outbox.publisher` / `OUTBOX_PUBLISHER`:

- `noop` acknowledges rows without an external broker for isolated deployments and tests.
- `local` records/logs events in-process for local development and failure testing.
- `kafka` sends the JSON envelope to `commerce.outbox.kafka.topic` / `OUTBOX_KAFKA_TOPIC` using Spring Kafka.

If publication fails, the publisher increments `retry_count` and leaves the row `PENDING`. The committed order/payment transaction is not rolled back. Consumers must deduplicate by `eventId`.

The AsyncAPI contract is [`contracts/asyncapi/fresveg-events.yaml`](../../contracts/asyncapi/fresveg-events.yaml).
