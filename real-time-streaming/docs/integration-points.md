# Integration Points

These notes show where the skeleton would fit if moved into the real CMR repo.

## `ingest-app/src/cmr/ingest/api/routes.clj`

Add:

```clojure
[cmr.ingest.api.realtime.events :as realtime-events]
```

Inside the existing provider context:

```clojure
realtime-events/routes
```

This keeps realtime publication under provider-scoped ingest permissions:

```http
POST /ingest/providers/{provider-id}/realtime/events
```

## `indexer-app/src/cmr/indexer/services/event_handler.clj`

Add:

```clojure
[cmr.indexer.services.realtime.event-handler :as realtime-handler]
```

Then add a handler:

```clojure
(defmethod handle-ingest-event :realtime-granule-event
  [context all-revisions-index? msg]
  (realtime-handler/handle-realtime-event context all-revisions-index? msg))
```

This keeps the realtime projection in the same queue-driven indexing path as current concept
updates.

## `search-app/src/cmr/search/api/routes.clj`

Add:

```clojure
[cmr.search.api.realtime.routes :as realtime-routes]
```

Then include before broad concept search routes:

```clojure
realtime-routes/routes
```

This creates explicit realtime routes without changing the meaning of existing `/search/granules`
queries.

## `message-queue-lib`

The skeleton uses a shared envelope namespace instead of adding provider-specific maps directly
to ingest or indexer. That gives CMR one place to validate event shape and version it later.

## `subscription`

Optional only. Existing subscription processing remains a notification service. If integrated,
it should consume realtime metadata events after authorization/query matching, not become the
transport for data streaming.
