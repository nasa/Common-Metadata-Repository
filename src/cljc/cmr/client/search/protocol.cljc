(ns cmr.client.search.protocol
  "This namespace defines the protocols used by CMR search client.")

(defprotocol CMRSearchAPI
  (^:export get-collections
   [this]
   [this http-options]
   [this query-params http-options])
  (^:export get-concept
   [this concept-id http-options]
   [this concept-id revision-id http-options])
  (^:export get-granules [this http-options] [this query-params http-options])
  (^:export get-humanizers [this] [this http-options])
  (^:export get-tag
   [this tag-id http-options]
   [this tag-id query-params http-options])
  (^:export get-tags [this http-options] [this query-params http-options])
  (^:export get-tiles [this http-options] [this query-params http-options])
  (^:export get-variables
   [this http-options]
   [this query-params http-options]))
