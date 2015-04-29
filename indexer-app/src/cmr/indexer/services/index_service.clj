(ns cmr.indexer.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.concepts :as cs]
            [cmr.common.date-time-parser :as date]
            [cmr.common.cache :as cache]
            [cmr.transmit.metadata-db :as meta-db]
            [cmr.transmit.index-set :as tis]
            [cmr.transmit.echo.rest :as rest]
            [cmr.transmit.cubby :as cubby]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.elastic-utils.connect :as es-util]
            [cmr.umm.core :as umm]
            [cmr.message-queue.services.queue :as queue]
            [cheshire.core :as cheshire]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.indexer.config :as config]
            [cmr.acl.acl-fetcher :as acl-fetcher]
            [cmr.common.services.errors :as errors]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.message-queue.config :as qcfg]
            [cmr.common.lifecycle :as lifecycle]))

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
  "Reindexes all the collections in the providers given."
  [context provider-ids]

  ;; Refresh the ACL cache.
  ;; We want the latest permitted groups to be indexed with the collections
  (acl-fetcher/refresh-acl-cache context)

  (doseq [provider-id provider-ids]
    (let [collections (->> (meta-db/find-collections context {:provider-id provider-id})
                           ;; Limit to the maximum revision of each concept
                           (group-by :concept-id)
                           vals
                           (map #(last (sort-by :revision-id %))))]
      (bulk-index context [collections]))))

(deftracefn index-concept
  "Index the given concept and revision-id"
  [context concept-id revision-id ignore-conflict]
  (info (format "Indexing concept %s, revision-id %s" concept-id revision-id))
  (when-not (and concept-id revision-id)
    (errors/throw-service-error
      :bad-request
      (format "Concept-id %s and revision-id %s cannot be null" concept-id revision-id)))

  (let [concept-type (cs/concept-id->type concept-id)
        concept-mapping-types (idx-set/get-concept-mapping-types context)
        concept (meta-db/get-concept context concept-id revision-id)
        umm-concept (umm/parse-concept concept)
        delete-time (get-in umm-concept [:data-provider-timestamps :delete-time])]
    (when (or (nil? delete-time) (> (compare delete-time (tk/now)) 0))
      (let [ttl (when delete-time (t/in-millis (t/interval (tk/now) delete-time)))
            concept-index (idx-set/get-concept-index-name context concept-id revision-id concept)
            es-doc (es/concept->elastic-doc context concept umm-concept)]
        (es/save-document-in-elastic
          context
          concept-index
          (concept-mapping-types concept-type) es-doc revision-id ttl ignore-conflict)))))


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
        (idx-set/get-granule-index-name-for-collection context id)
        (concept-mapping-types :granule)
        {:term {:collection-concept-id id}}))))

(deftracefn delete-provider
  "Delete all the concepts within the given provider"
  [context provider-id]
  (info (format "Deleting provider-id %s" provider-id))
  (let [collection-index (get-in (idx-set/get-concept-type-index-names context)
                                 [:collection :collections])
        concept-mapping-types (idx-set/get-concept-mapping-types context)]
    ;; delete the collections
    (es/delete-by-query
      context
      collection-index
      (concept-mapping-types :collection)
      {:term {:provider-id provider-id}})
    ;; delete the granules
    (doseq [index-name (idx-set/get-granule-index-names-for-provider context provider-id)]
      (es/delete-by-query
        context
        index-name
        (concept-mapping-types :granule)
        {:term {:provider-id provider-id}}))))

(deftracefn reset
  "Delegates reset elastic indices operation to index-set app as well as resetting caches"
  [context]
  (cache/reset-caches context)
  (es/reset-es-store context)
  (cache/reset-caches context))

(deftracefn update-indexes
  "Updates the index mappings and settings."
  [context]
  (cache/reset-caches context)
  (es/update-indexes context)
  (cache/reset-caches context))

(deftracefn health
  "Returns the health state of the app."
  [context]
  (let [elastic-health (es-util/health context :db)
        echo-rest-health (rest/health context)
        ;; Cubby health disabled until https://bugs.earthdata.nasa.gov/browse/EI-3348 is completed
        ;; and cubby is available in all environments
        ; cubby-health (cubby/get-cubby-health context)
        metadata-db-health (meta-db/get-metadata-db-health context)
        index-set-health (tis/get-index-set-health context)
        ok? (every? :ok? [elastic-health echo-rest-health
                          ; cubby-health
                          metadata-db-health index-set-health])]
    {:ok? ok?
     :dependencies {:elastic_search elastic-health
                    :echo echo-rest-health
                    ; :cubby cubby-health
                    :metadata-db metadata-db-health
                    :index-set index-set-health}}))

