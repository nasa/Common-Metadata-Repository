(ns cmr.client.graph.protocol
  "This namespace defines the protocols used by CMR graph client.")

(defprotocol CMRGraphAPI
  (^:export get-movie
   [this query-str]
   [this query-str http-options]
   "Search the movie demo graph database for a movie."))
