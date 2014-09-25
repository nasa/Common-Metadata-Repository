(ns cmr.indexer.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.concepts :as cs]
            [cmr.common.date-time-parser :as date]
            [cmr.transmit.metadata-db :as meta-db]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.umm.core :as umm]
            [cheshire.core :as cheshire]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.common.cache :as cache]
            [cmr.acl.acl-cache :as acl-cache]
            [cmr.common.services.errors :as errors]
            [cmr.system-trace.core :refer [deftracefn]]))

(defn filter-expired-concepts
  "Remove concepts that have an expired delete-time."
  [batch]
  (filter (fn [concept]
            (let [delete-time-str (get-in concept [:extra-fields :delete-time])
                  delete-time (when delete-time-str
                                (date/parse-datetime delete-time-str))]
              (or (nil? delete-time)
                  (t/after? delete-time (tk/now)))))
          batch))


(deftracefn bulk-index
  "Index many concepts at once using the elastic bulk api. The concepts to be indexed are passed
  directly to this function - it does not retrieve them from metadata db. The bulk API is
  invoked repeatedly if necessary - processing batch-size concepts each time. Returns the number
  of concepts that have been indexed"
  [context concept-batches]
  (reduce (fn [num-indexed batch]
            (let [batch (es/prepare-batch context (filter-expired-concepts batch))]
              (es/bulk-index context batch)
              (+ num-indexed (count batch))))
          0
          concept-batches))

(deftracefn reindex-provider-collections
  "Reindexes all the collections in a provider."
  [context provider-id]

  ;; Refresh the ACL cache.
  ;; We want the latest permitted groups to be indexed with the collections
  (acl-cache/refresh-acl-cache context)

  (let [collections (meta-db/find-collections context {:provider-id provider-id})]
    (bulk-index context [collections])))

(deftracefn index-concept
  "Index the given concept and revision-id"
  [context concept-id revision-id ignore-conflict]
  (info (format "Indexing concept %s, revision-id %s" concept-id revision-id))
  (let [concept-type (cs/concept-id->type concept-id)
        concept-mapping-types (idx-set/get-concept-mapping-types context)
        concept (meta-db/get-concept context concept-id revision-id)
        umm-concept (umm/parse-concept concept)
        delete-time (get-in umm-concept [:data-provider-timestamps :delete-time])
        ttl (when delete-time (t/in-millis (t/interval (tk/now) delete-time)))
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


(deftracefn clear-cache
  "Delegate reset elastic indices operation to index-set app"
  [context]
  (info "Clearing the indexer application cache")
  (cache/reset-cache (-> context :system :caches :general))
  (acl-cache/reset context))

(deftracefn reset-indexes
  "Delegate reset elastic indices operation to index-set app"
  [context]
  (clear-cache context)
  (es/reset-es-store context)
  (clear-cache context))

(deftracefn update-indexes
  "Updates the index mappings and settings."
  [context]
  (clear-cache context)
  (es/update-indexes context)
  (clear-cache context))
