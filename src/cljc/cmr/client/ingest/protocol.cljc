(ns cmr.client.ingest.protocol
  "This namespace defines the protocols used by CMR ingest client.")

(defprotocol CMRIngestAPI
  (^:export get-providers [this]
   "Get a list of providers defined in the system.

   Each element of the list is a hash which constains very basic info on the
   given provider.")
  (^:export create-collection [this provider-id native-id metadata]
                              [this provider-id native-id metadata options]
    "Create a collection.")
  (^:export update-collection [this provider-id native-id metadata]
                              [this provider-id native-id metadata options]
    "Update a collection.")
  (^:export create-variable [this provider-id native-id metadata]
                            [this provider-id native-id metadata options]
    "Create a variable.")
  (^:export update-variable [this provider-id native-id metadata]
                            [this provider-id native-id metadata options]
    "Update a variable."))
