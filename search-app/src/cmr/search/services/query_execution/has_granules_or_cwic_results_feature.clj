(ns cmr.search.services.query-execution.has-granules-or-cwic-results-feature
  "This enables the :has-granules-or-cwic feature for collection search results. When it is enabled
  collection search results will include a boolean flag indicating whether the collection has
  any granules at all as indicated by provider holdings."
  (:require
   [cmr.common-app.services.search.elastic-search-index :as common-esi]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :refer [defjob]]
   [cmr.search.data.elastic-search-index :as idx]))

(def REFRESH_HAS_GRANULES_OR_CWIC_MAP_JOB_INTERVAL
  "The frequency in seconds of the refresh-has-granules-or-cwic-map-job"
  ;; default to 1 hour
  3600)

(def has-granule-cache-key
  :has-granules-or-cwic-map)

(defconfig cwic-tag
  "has-granules-or-cwic should also return any collection with configured cwic-tag"
  {:default "org.ceos.wgiss.cwic.granules.prod"})

(defn create-has-granules-or-cwic-map-cache
  "Returns a 'cache' which will contain the cached has granules map."
  []
  (mem-cache/create-in-memory-cache))

(defn get-cwic-collections
  "Returns the collection granule count by searching elasticsearch by aggregation"
  [context provider-ids]
  (let [condition (nf/parse-nested-condition :tags {:tag-key (cwic-tag)} false true)
        query (qm/query {:concept-type :collection
                         :condition condition})
        results (common-esi/execute-query context query)]
    (into {}
          (for [coll-id (map :_id (get-in results [:hits :hits]))]
            [coll-id 1]))))

(defn- collection-granule-counts->has-granules-or-cwic-map
  "Converts a map of collection ids to granule counts to a map of collection ids to true or false
  of whether the collection has any granules"
  [coll-gran-counts]
  (into {} (for [[coll-id num-granules] coll-gran-counts]
             [coll-id (> num-granules 0)])))

(defn refresh-has-granules-or-cwic-map
  "Gets the latest provider holdings and updates the has-granules-or-cwic-map stored in the cache."
  [context]
  (let [has-granules-or-cwic-map (collection-granule-counts->has-granules-or-cwic-map
                                  (merge
                                   (idx/get-collection-granule-counts context nil)
                                   (get-cwic-collections context nil)))]
    (cache/set-value (cache/context->cache context has-granule-cache-key)
                     :has-granules-or-cwic has-granules-or-cwic-map)))

(defn get-has-granules-or-cwic-map
  "Gets the cached has granules map from the context which contains collection ids to true or false
  of whether the collections have granules or not. If the has-granules-or-cwic-map has not yet been cached
  it will retrieve it and cache it."
  [context]
  (let [has-granules-or-cwic-map-cache (cache/context->cache context has-granule-cache-key)]
    (cache/get-value has-granules-or-cwic-map-cache
                     :has-granules-or-cwic
                     (fn []
                       (collection-granule-counts->has-granules-or-cwic-map
                        (merge
                         (idx/get-collection-granule-counts context nil)
                         (get-cwic-collections context nil)))))))

(defjob RefreshHasGranulesOrCwicMapJob
  [ctx system]
  (refresh-has-granules-or-cwic-map {:system system}))

(def refresh-has-granules-or-cwic-map-job
  {:job-type RefreshHasGranulesOrCwicMapJob
   :interval REFRESH_HAS_GRANULES_OR_CWIC_MAP_JOB_INTERVAL})
