(ns cmr.client.graph.protocol
  "This namespace defines the protocols used by CMR graph client.")

(defprotocol CMRGraphAPI
  ;; Demo Graph API
  (^:export get-movie
   [this query-str]
   [this query-str http-options]
   "Search the movie demo graph database for a movie.")
  ;; Actual CMR Graph API
  (^:export get-collection-url-relation
   [this concept-id]
   [this concept-id http-options]
   "Find all the collections that share related URLs."))
