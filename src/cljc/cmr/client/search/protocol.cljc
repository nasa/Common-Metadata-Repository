(ns cmr.client.search.protocol
  "This namespace defines the protocols used by CMR search client.")

(defprotocol CMRSearchAPI
  (^:export create-variable-association
   [this concept-id collection-data]
   [this concept-id collection-data http-options]
   [this concept-id collection-data query-params http-options]
   "Create an association between a variable and one or more collections.")
  (^:export get-collections
   [this]
   [this http-options]
   [this query-params http-options]
   "Find all collections.")
  (^:export get-concept
   [this concept-id]
   [this concept-id http-options]
   [this concept-id revision-id http-options]
   "Get the contept metadata for associated with the given concept-id.")
  (^:export get-granules
   [this]
   [this http-options]
   [this query-params http-options]
   "Not yet implemented.")
  (^:export get-humanizers
   [this]
   [this http-options]
   "Not yet implemented.")
  (^:export get-tag
   [this tag-id]
   [this tag-id http-options]
   [this tag-id query-params http-options]
   "Not yet implemented.")
  (^:export get-tags
   [this]
   [this http-options]
   [this query-params http-options]
   "Not yet implemented.")
  (^:export get-tiles
   [this]
   [this http-options]
   [this query-params http-options]
   "Not yet implemented.")
  (^:export get-variables
   [this]
   [this http-options]
   [this query-params http-options]
   "Get variables."))
