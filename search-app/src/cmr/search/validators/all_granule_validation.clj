(ns cmr.search.validators.all-granule-validation
  "Validates that queries against all granules do not contain a spatial condition. This is an expensive
   query for the CMR. See CMR-2458."
  (:require
   [cmr.common.config :refer [defconfig]]
   [cmr.search.services.query-walkers.condition-extractor :as extractor])
  (:import
   (cmr.common_app.services.search.query_model StringCondition StringsCondition)
   (cmr.search.models.query SpatialCondition)))

(defconfig all-granules-page-depth-limit
  "The depth limit for all granules queries. These are expensive and impact other query performance.
   See CMR-3488."
  {:type Long
   :default 10000})

(def granule-limiting-search-fields
  #{:concept-id :provider :provider-id :short-name :entry-title :version :entry-id :collection-concept-id})

(defn- granule-limiting-condition?
  "Returns true if the condition limits the query to granules within a set of collections."
  [_path condition]
  (and (or (instance? StringCondition condition)
           (instance? StringsCondition condition))
       (contains? granule-limiting-search-fields (:field condition))))

(defn- all-granules-query?
  "Returns true if the query is for all granules, ie. it does not limit the query to one or more
   collections."
  [query]
  (empty? (extractor/extract-conditions query granule-limiting-condition?)))

(defn- spatial-query?
  "Returns true if the query uses spatial conditions"
  [query]
  (some? (seq (extractor/extract-conditions query (fn [_ c] (instance? SpatialCondition c))))))

(defn no-all-granules-with-spatial
  "Validates that the query is not an all granules query with spatial conditions"
  [query]
  (when (and (all-granules-query? query) (spatial-query? query))
    [(str "The CMR does not allow querying across granules in all collections with a spatial"
          " condition. You should limit your query using conditions that identify one or more"
          " collections such as provider, concept_id, short_name, or entry_title.")]))

(defn all-granules-exceeds-page-depth-limit
  "Validates that the query is not an all granules query that pages beyond the depth limit."
  [query]
  (when (and (all-granules-query? query)
             (> (:offset query) (all-granules-page-depth-limit)))
    [(format
      (str "The paging depth (page_num * page_size or offset) of [%d] "
           "exceeds the limit of %d for an all granules query. "
           "You should limit your query using conditions that identify one or more "
           "collections such as provider, concept_id, short_name, or entry_title.")
      (:offset query) (all-granules-page-depth-limit))]))

(defn no-all-granules-with-scroll
  "Validates that the query is not an all granules query if it is a scroll query."
  [query]
  (when (and (:scroll query) 
             (all-granules-query? query) 
             
             ;; Subsequent calls to scroll look like all-granules queries since the query
             ;; is empty. Anything with a scroll-id is a subsequent scroll request, so we ignore 
             ;; those.
             (not (:scroll-id query)))
    [(str "The CMR does not allow querying across granules in all collections when scrolling. "
          "You should limit your query using conditions that identify one or more collections"
          " such as provider, concept_id, short_name, or entry_title.")]))

