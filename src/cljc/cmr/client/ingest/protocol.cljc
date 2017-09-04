(ns cmr.client.ingest.protocol
  "This namespace defines the protocols used by CMR ingest client.")

(defprotocol CMRIngestAPI
  (^:export get-providers [this]))
