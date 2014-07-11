(ns cmr.indexer.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.concepts :as cs]
            [cmr.transmit.metadata-db :as meta-db]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.umm.core :as umm]
            [cheshire.core :as cheshire]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.common.cache :as cache]
            [cmr.common.services.errors :as errors]
            [cmr.system-trace.core :refer [deftracefn]]))

(deftracefn bulk-index
  "Index many concepts at once using the elastic bulk api. The concepts to be indexed are passed
  directly to this function - it does not retrieve them from metadata db. The bulk API is
  invoked repeatedly if necessary - processing batch-size concepts each time. Returns the number
  of concepts that have been indexed"
  [context concept-batches]
  (reduce (fn [num-indexed batch]
            (let [batch (es/prepare-batch context batch)]
              (es/bulk-index context batch)
              (+ num-indexed (count batch))))
          0
          concept-batches))

(deftracefn index-concept
  "Index the given concept and revision-id"
  [context concept-id revision-id ignore-conflict]
  (info (format "Indexing concept %s, revision-id %s" concept-id revision-id))
  (let [concept-type (cs/concept-id->type concept-id)
        concept-mapping-types (idx-set/get-concept-mapping-types context)
        concept (meta-db/get-concept context concept-id revision-id)
        umm-concept (umm/parse-concept concept)
        delete-time (get-in umm-concept [:data-provider-timestamps :delete-time])
        ttl (when delete-time (t/in-millis (t/interval (t/now) delete-time)))
        concept-index (idx-set/get-concept-index-name context concept-id revision-id concept)
        es-doc (es/concept->elastic-doc context concept umm-concept)]
    (when-not (and ttl (<= ttl 0))
      (es/save-document-in-elastic
        context
        concept-index
        (concept-mapping-types concept-type) es-doc revision-id ttl ignore-conflict))))


(deftracefn delete-concept
  "Delete the concept with the given id"
  [context id revision-id ignore-conflict]
  (info (format "Deleting concept %s, revision-id %s" id revision-id))
  ;; Assuming ingest will pass enough info for deletion
  ;; We should avoid making calls to metadata db to get the necessary info if possible
  (let [concept-type (cs/concept-id->type id)
        concept-index (idx-set/get-concept-index-name context id revision-id)
        concept-mapping-types (idx-set/get-concept-mapping-types context)]
    (es/delete-document
      context
      concept-index
      (concept-mapping-types concept-type) id revision-id ignore-conflict)
    (when (= :collection concept-type)
      (es/delete-by-query
        context
        (idx-set/get-index-name-for-granule-delete context id)
        (concept-mapping-types :granule)
        {:term {:collection-concept-id id}}))))


(deftracefn reset-indexes
  "Delegate reset elastic indices operation to index-set app"
  [context]
  (cache/reset-cache (-> context :system :cache))
  (es/reset-es-store context)
  (cache/reset-cache (-> context :system :cache)))


