(ns cmr.client
  "The top-level namespace for the CMR client library.

  This namespce contains references to the three CMR services that provide
  endpoints for client access."
  (:require
   [cmr.client.ac :as ac]
   [cmr.client.graph :as graph]
   [cmr.client.ingest :as ingest]
   [cmr.client.search :as search]))
