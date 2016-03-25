(ns cmr.bootstrap.data.rebalance-util
  "Utilities for helping with rebalancing a collection."
  (require [cmr.indexer.data.index-set :as index-set]
           [cmr.bootstrap.embedded-system-helper :as helper]
           [clojurewerkz.elastisch.query :as q]
           [clojurewerkz.elastisch.rest.document :as esd]
           [cmr.indexer.data.elasticsearch :as indexer-es]
           [cmr.metadata-db.services.concept-service :as cs]))

(def granule-mapping-type-name
  "The mapping type for granules in Elasticsearch"
  (-> index-set/granule-mapping keys first name))

(defn es-query-for-collection-concept-id
  "Returns an elasticsearch query to find granules in the collection."
  [concept-id]
  {:filtered {:query (q/match-all)
              :filter (q/term :collection-concept-id concept-id)}})

(defn- granule-count-for-collection
  "Gets the granule count for the collection in the elastic index."
  [indexer-context index-name concept-id]
  (let [conn (indexer-es/context->conn indexer-context)
        query (es-query-for-collection-concept-id concept-id)]
    (:count (esd/count conn index-name granule-mapping-type-name query))))

(defn rebalancing-collection-counts
  "Returns the counts of a rebalancing collection from metadata db, the small collections index,
   and the separate index."
  [context concept-id]
  (let [indexer-context {:system (helper/get-indexer (:system context))}
        index-names (index-set/fetch-concept-type-index-names
                     indexer-context index-set/index-set-id)
        granule-index-names (get-in index-names [:index-names :granule])
        ;; Small collections
        small-coll-index (:small_collections granule-index-names)
        small-count (granule-count-for-collection indexer-context small-coll-index concept-id)]
        ;; Metadata db counts will be added as part of CMR-2569
        ; mdb-context {:system (helper/get-metadata-db (:system context))}]

    (if-let [sep-index (get granule-index-names (keyword concept-id))]
      {:small-collections small-count
       :separate-index (granule-count-for-collection indexer-context sep-index concept-id)}
      {:small-collections small-count})))

(defn delete-collection-granules-from-small-collections
  "Deletes by query any granules in small collections for the given collection"
  [context concept-id]
  (let [indexer-context {:system (helper/get-indexer (:system context))}
        index-names (index-set/fetch-concept-type-index-names
                     indexer-context index-set/index-set-id)
        small-coll-index (get-in index-names [:index-names :granule :small_collections])
        granule-mapping-type-name (-> index-set/granule-mapping keys first name)]
    (indexer-es/delete-by-query
     indexer-context small-coll-index granule-mapping-type-name
     (es-query-for-collection-concept-id concept-id))))
