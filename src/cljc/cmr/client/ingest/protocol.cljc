(ns cmr.client.ingest.protocol
  "This namespace defines the protocols used by CMR ingest client.")

(defprotocol CMRIngestAPI
  (^:export get-providers [this]
   "Get a list of providers defined in the system.

   Each element of the list is a hash which constains very basic info on the
   given provider."))
