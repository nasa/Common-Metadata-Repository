(ns cmr.search.services.query-service
  "Defines operations for querying for concepts.

  Steps to process a query:

  - Validate parameters
  - Convert parameters into query model
  - Query validation
  - Apply ACLs to query
  - Query simplification
  - Convert Query into Elastic Query Model
  - Send query to Elasticsearch
  - Convert query results into requested format"
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [cmr.elastic-utils.search.es-wrapper :as esq]
   [cmr.common-app.services.search :as common-search]
   [cmr.common.concepts :as cc]
   [cmr.common.log :refer (info)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.search.query-model :as qm]
   [cmr.common.util :as u]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]
   [cmr.elastic-utils.search.es-index :as common-idx]
   [cmr.elastic-utils.search.es-params-converter :as common-params]
   [cmr.elastic-utils.search.query-execution :as qe]
   [cmr.search.api.core :refer [log-search-result-metadata]]
   [cmr.search.data.elastic-search-index :as idx]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.results-handlers.provider-holdings :as ph]
   [cmr.search.services.aql.conversion :as a]
   [cmr.search.services.json-parameters.conversion :as jp]
   [cmr.search.services.parameters.conversion :as p]
   [cmr.search.services.parameters.legacy-parameters :as lp]
   [cmr.search.services.parameters.parameter-validation :as pv]
   [cmr.search.services.parameters.provider-short-name :as psn]
   [cmr.search.services.result-format-helper :as rfh]
   [cmr.spatial.codec :as spatial-codec]
   [cmr.spatial.tile :as tile])
  ;; These must be required here to make multimethod implementations available.
  ;; XXX This is not a good pattern for large software systems; we need to
  ;;     find a different way to accomplish this goal ... possibly use protocols
  ;;     instead.
  (:require
    cmr.search.data.complex-to-simple-converters.attribute
    cmr.search.data.complex-to-simple-converters.has-granules
    cmr.search.data.complex-to-simple-converters.has-granules-or-cwic
    cmr.search.data.complex-to-simple-converters.orbit
    cmr.search.data.complex-to-simple-converters.spatial
    cmr.search.data.complex-to-simple-converters.temporal
    cmr.search.data.complex-to-simple-converters.two-d-coordinate-system
    cmr.elastic-utils.search.elastic-results-to-query-results
    cmr.search.services.aql.converters.attribute
    cmr.search.services.aql.converters.attribute-name
    cmr.search.services.aql.converters.science-keywords
    cmr.search.services.aql.converters.spatial
    cmr.search.services.aql.converters.temporal
    cmr.search.services.aql.converters.two-d-coordinate-system
    cmr.search.services.json-parameters.converters.attribute
    cmr.search.services.parameters.converters.attribute
    cmr.search.services.parameters.converters.collection-query
    cmr.search.services.parameters.converters.equator-crossing-date
    cmr.search.services.parameters.converters.equator-crossing-longitude
    cmr.search.services.parameters.converters.humanizer
    cmr.search.services.parameters.converters.orbit-number
    cmr.search.services.parameters.converters.pass
    cmr.search.services.parameters.converters.platform
    cmr.search.services.parameters.converters.science-keyword
    cmr.search.services.parameters.converters.spatial
    cmr.search.services.parameters.converters.shapefile
    cmr.search.services.parameters.converters.geometry
    cmr.search.services.parameters.converters.temporal
    cmr.search.services.parameters.converters.temporal-facet
    cmr.search.services.parameters.converters.two-d-coordinate-system
    cmr.search.services.query-execution
    cmr.search.services.query-execution.facets.collection-v2-facets
    cmr.search.services.query-execution.facets.facets-results-feature
    cmr.search.services.query-execution.facets.facets-v2-results-feature
    cmr.search.services.query-execution.facets.granule-v2-facets
    cmr.search.services.query-execution.granule-counts-results-feature
    cmr.search.services.query-execution.has-granules-created-at-feature
    cmr.search.services.query-execution.has-granules-results-feature
    cmr.search.services.query-execution.has-granules-or-cwic-results-feature
    cmr.search.services.query-execution.has-granules-revised-at-feature
    cmr.search.services.query-execution.highlight-results-feature
    cmr.search.services.query-execution.tags-results-feature
    cmr.search.validators.attribute
    cmr.search.validators.equator-crossing-date
    cmr.search.validators.equator-crossing-longitude
    cmr.search.validators.orbit-number
    cmr.search.validators.temporal
    cmr.search.validators.validation))

(def query-aggregation-size
  "Page size for query aggregations. This should be large enough
  to contain all collections to allow searching for collections
  from granules."
  50000)

(defn- sanitize-aql-params
  "When content-type is not set for aql searches, the aql will get mistakenly parsed into params.
  This function removes it, santizes the params and returns the end result."
  [params]
  (-> (select-keys params (filter keyword? (keys params)))
      common-params/sanitize-params))

(def deleted-granule-index-name
  "The name of the index in elastic search. Duplicated from indexer app."
  "1_deleted_granules")

(def deleted-granule-type-name
  "The name of the mapping type within the cubby elasticsearch index. Duplicated from indexer app."
  "deleted-granule")

;; CMR-2553 Remove this function.
(defn- drop-ignored-params
  "At times, there are unsupported search params in parameter search that we simply want to ignore
  rather than raise error. This function removes tag-namespace from the params and returns the
  params."
  [params]
  (let [drop-params-fn (fn [p]
                         (if (map? p)
                           (let [p (dissoc p :tag-namespace)]
                             (when (seq p) p))
                           p))]

    (-> params
        drop-params-fn
        (update :options drop-params-fn)
        (update :exclude drop-params-fn)
        ;; exclude parameter processing can't handle nil value, so we remove it if it is nil.
        u/remove-nil-keys)))

(defn- add-tag-data-to-params
  "Returns the params with tag-data added if necessary"
  [tag-data params]
  (if tag-data
    (assoc params :tag-data tag-data)
    params))

(defn- make-concepts-tag-data
  "Utility function for extracting tag data from params.

  Note that the tag-data param has tag-key as its key, which could be any
  arbitrary string. sanitize-params function traded data integrity for easier
  processing, e.g. it converts \"tag1\" into :tag-1 and it doesn't work for
  tag-keys. To work around this problem, we extract the tag-data param out
  first, then add it back in after the sanitize-params call."
  [params]
  (or (:tag-data params) (:tag_data params)))

(defn- create-and-validate-concepts-query-parameters
  ([context concept-type params]
   (->> params
        (make-concepts-tag-data)
        (create-and-validate-concepts-query-parameters
         context concept-type params)))
  ([context concept-type params tag-data]
   (->> params
        common-params/sanitize-params
        (add-tag-data-to-params tag-data)
        ;; CMR-2553 remove the following line
        drop-ignored-params
        ;; handle legacy parameters
        lp/replace-parameter-aliases
        (lp/process-legacy-multi-params-conditions concept-type)
        (lp/replace-science-keywords-or-option concept-type)
        (psn/replace-provider-short-names context)
        (pv/validate-parameters concept-type))))

(defn make-concepts-query
  "Utility function for generating an elastic-ready query."
  ([context concept-type params]
   (common-params/parse-parameter-query
    context
    concept-type
    (create-and-validate-concepts-query-parameters
     context concept-type params)))
  ([context concept-type params tag-data]
   (common-params/parse-parameter-query
    context
    concept-type
    (create-and-validate-concepts-query-parameters
     context concept-type params tag-data))))

(defn generate-query-conditions-for-parameters
  "Given the query params, generate the conditions for elastic search. Returns
  just the search conditions."
  [context concept-type params]
  (let [params (create-and-validate-concepts-query-parameters
                context concept-type params)]
    (common-params/generate-param-query-conditions context concept-type params)))

(defn find-concepts-by-parameters
  "Executes a search for concepts using the given parameters. The concepts will be returned with
  concept id and native provider id along with hit count and timing info."
  [context concept-type params]
  (let [tag-data (make-concepts-tag-data params)
        [query-creation-time query] (u/time-execution
                                     (make-concepts-query
                                      context concept-type params tag-data))
        [find-concepts-time results] (u/time-execution
                                      (common-search/find-concepts
                                       context concept-type query))
        total-took (+ query-creation-time find-concepts-time)
        scroll-id (:scroll-id results)
        search-after (:search-after context)
        result-format (rfh/printable-result-format (:result-format query))
        log-message (log-search-result-metadata (:hits results) (name concept-type)
                                                total-took (:client-id context) (:token context) result-format
                                                "with params %s" (pr-str params))]
    (info (cond
            scroll-id (format "%s, scroll-id: %s." log-message (str (hash scroll-id)))
            search-after (format "%s, search-after: %s, new search-after: %s."
                                 log-message
                                 (json/encode search-after)
                                 (:search-after results))
            :else (format "%s." log-message)))
    (assoc results :took total-took)))

(defn find-concepts-by-json-query
  "Executes a search for concepts using the given JSON. The concepts will be returned with
  concept id and native provider id along with hit count and timing info."
  [context concept-type params json-query]
  (let [[query-creation-time query] (u/time-execution
                                     (jp/parse-json-query concept-type
                                                          (common-params/sanitize-params params)
                                                          json-query))
        search-after (:search-after context)
        query (merge query
                     (when search-after {:search-after search-after}))
        [find-concepts-time results] (u/time-execution
                                      (common-search/find-concepts context
                                                                   concept-type
                                                                   query))
        total-took (+ query-creation-time find-concepts-time)
        result-format (rfh/printable-result-format (:result-format query))
        log-message (log-search-result-metadata (:hits results) (name concept-type) total-took
                                                (:client-id context) (:token context) result-format
                                                "with JSON Query %s and query params %s" json-query (pr-str params))]
    (info (if search-after
            (format "%s, search-after: %s, new search-after: %s."
                    log-message
                    (json/encode search-after)
                    (:search-after results))
            (format "%s." log-message)))
    (assoc results :took total-took)))

(defn find-concepts-by-aql
  "Executes a search for concepts using the given aql. The concepts will be returned with
  concept id and native provider id along with hit count and timing info."
  [context params aql]
  (let [params (-> params
                   sanitize-aql-params
                   lp/replace-parameter-aliases)
        [query-creation-time query] (u/time-execution (a/parse-aql-query params aql))
        concept-type (:concept-type query)
        [find-concepts-time results] (u/time-execution
                                      (common-search/find-concepts context
                                                                   concept-type
                                                                   query))
        total-took (+ query-creation-time find-concepts-time)
        result-format (rfh/printable-result-format (:result-format query))
        log-message (log-search-result-metadata (:hits results) (name concept-type)
                                                total-took (:client-id context) (:token context) result-format
                                                "with aql: %s" aql)]
    (info log-message)
    (assoc results :took total-took)))

(defn- throw-id-not-found
  [concept-id]
  (errors/throw-service-error
   :not-found
   (format "Concept with concept-id [%s] could not be found." concept-id)))

(defn- throw-concept-revision-not-found
  [concept-id revision-id]
  (errors/throw-service-error
   :not-found
   (format
    "Concept with concept-id [%s] and revision-id [%s] could not be found."
    concept-id
    revision-id)))

(defn find-concept-by-id
  "Executes a search to metadata-db and returns the concept with the given cmr-concept-id."
  [context result-format concept-id]
  (if (contains? #{:atom :json :stac} result-format)
    ;; do a query and use single-result->response
    (let [query (common-params/parse-parameter-query context
                                                     (cc/concept-id->type concept-id)
                                                     {:page-size 1
                                                      :concept-id concept-id
                                                      :result-format result-format})
          results (qe/execute-query context query)]
      (when (zero? (:hits results))
        (throw-id-not-found concept-id))
      {:results (common-search/single-result->response context query results)
       :result-format result-format})
    ;; else
    (let [concept (first (metadata-cache/get-latest-formatted-concepts
                          context [concept-id] result-format))]
      (when-not concept
        (throw-id-not-found concept-id))
      {:results (:metadata concept)
       :result-format (mt/mime-type->format (:format concept))})))

(defn find-concept-by-id-and-revision
  "Executes a search to metadata-db and returns the concept with the given concept-id and
  revision-id."
  [context result-format concept-id revision-id]
  ;; We don't store revision id in the search index, so we can't use shortcuts for json/atom
  ;; like we do in find-concept-by-id.
  (let [concept (metadata-cache/get-formatted-concept
                 context concept-id revision-id result-format)]
    (when-not concept
      (throw-concept-revision-not-found concept-id revision-id))
    {:results (:metadata concept)
     :result-format (mt/mime-type->format (:format concept))}))

(defn get-granule-timeline
  "Finds granules and returns the results as a list of intervals of granule counts per collection."
  [context params]
  (let [query (->> params
                   common-params/sanitize-params
                   ;; handle legacy parameters
                   lp/replace-parameter-aliases
                   (lp/process-legacy-multi-params-conditions :granule)
                   (lp/replace-science-keywords-or-option :granule)
                   pv/validate-timeline-parameters
                   (p/timeline-parameters->query context)
                   (common-search/validate-query context))
        [execution-time results] (u/time-execution
                                  (common-search/search-results->response
                                   context query (qe/execute-query context query)))]
    {:results results :took execution-time :result-format mt/json}))

(defn- get-collections-by-providers
  "Returns all collections limited optionally by the given provider ids"
  ([context skip-acls?]
   (get-collections-by-providers context nil skip-acls?))
  ([context provider-ids skip-acls?]
   (let [query-condition (if (empty? provider-ids)
                           qm/match-all
                           (qm/string-conditions :provider-id provider-ids))
         query (qm/query {:concept-type :collection
                          :condition query-condition
                          :page-size :unlimited
                          :result-format :query-specified
                          :result-fields [:entry-title :provider-id]
                          :skip-acls? skip-acls?})
         results (qe/execute-query context query)]
     (:items results))))

(defn get-data-json-collections
  [context]
  (let [condition (common-params/parameter->condition context
                                                      :collection
                                                      :tag-key
                                                      "gov.nasa.eosdis"
                                                      {})
        query (qm/query {:concept-type :collection
                         :condition condition
                         :page-size :unlimited
                         :result-format :opendata
                         :skip-acls? false})
        results (qe/execute-query context query)]
    (common-search/search-results->response context query results)))

(defn get-provider-holdings
  "Executes elasticsearch search to get provider holdings"
  [context params]
  (let [{:keys [provider-id echo-compatible]} (u/map-keys->kebab-case params)
        ;; make sure provider-ids is sequential
        provider-ids (if (or (nil? provider-id) (sequential? provider-id))
                       provider-id
                       [provider-id])
        ;; get all collections limited by the list of providers in json format
        collections (get-collections-by-providers context provider-ids true)
        ;; get a mapping of collection to granule count
        collection-granule-count (idx/get-collection-granule-counts context provider-ids)
        ;; combine the granule count into collections to form provider holdings
        provider-holdings (map
                           #(assoc % :granule-count (get
                                                     collection-granule-count
                                                     (:concept-id %)
                                                     0))
                           collections)]
    [provider-holdings
     (ph/provider-holdings->string
      (:result-format params) provider-holdings {:echo-compatible? (= "true" echo-compatible)})]))

(defn- get-collections-with-deleted-revisions
  "Executes elasticsearch searches to find collections that have deleted revisions.
   Returns a list of concept-ids of the collections."
  [context params]
  ;; Find all collection revisions that are deleted satisfying the original search params
  (let [query (make-concepts-query context :collection params)
        condition (gc/and-conds
                   [(:condition query)
                    (qm/boolean-condition :deleted true)])
        results (common-idx/execute-query context
                                          (-> query
                                              (assoc :all-revisions? true)
                                              (assoc :condition condition)
                                              (assoc :page-size :unlimited)))
        coll-concept-ids (map #(get-in % [:_source :concept-id])
                              (get-in results [:hits :hits]))]
    (distinct coll-concept-ids)))

(defn- get-visible-collections
  "Returns the concept ids of collections that are visible from the given collection concept ids"
  [context coll-concept-ids]
  (when (seq coll-concept-ids)
    (let [condition (qm/string-conditions :concept-id coll-concept-ids true)
          query (qm/query {:concept-type :collection
                           :condition condition
                           :page-size :unlimited
                           :result-format :query-specified
                           :result-fields []})
          results (qe/execute-query context query)]
      (map :concept-id (:items results)))))

(defn- get-highest-visible-revisions
  "Returns the query and the highest visible collection revisions search result of the given
   collection concept ids in the given result format."
  [context coll-concept-ids result-format]
  (when (seq coll-concept-ids)
    ;; find all collection revisions, then filter out the highest revisions
    ;; and replace the hits and items in results with those of the highest revisions.
    (let [condition (gc/and-conds
                     [(qm/string-conditions :concept-id coll-concept-ids true)
                      (qm/boolean-condition :deleted false)])
          query (qm/query {:concept-type :collection
                           :condition condition
                           :all-revisions? true
                           :page-size :unlimited
                           :result-format result-format})
          results (qe/execute-query context query)
          highest-coll-revisions (u/map-values
                                  #(apply max (map :revision-id %))
                                  (group-by :concept-id (:items results)))
          highest-revisions (filter
                             (fn [coll]
                               ((set highest-coll-revisions)
                                [(:concept-id coll) (:revision-id coll)]))
                             (:items results))]
      (-> results
          (assoc :items highest-revisions)
          (assoc :hits (count highest-revisions))))))

(defn get-deleted-collections
  "Executes elasticsearch searches to find collections that are deleted.
   This only finds collections that are truly deleted and not searchable.
   Collections that are deleted, then later ingested again are not included in the result.
   Returns references to the highest collection revision prior to the collection tombstone."
  [context params]
  (pv/validate-deleted-collections-params params)
  ;; 1/ Find all collection revisions that are deleted satisfying the revision-date query -> c1
  ;; 2/ Filters out any collections c1 that still exists -> c2
  ;; 3/ Find all collection revisions for the c2, return the highest revisions that are visible
  (let [start-time (System/currentTimeMillis)
        result-format (:result-format params)
        coll-concept-ids (get-collections-with-deleted-revisions context params)
        visible-concept-ids (get-visible-collections context coll-concept-ids)
        ;; Find the concept ids that are still deleted
        deleted-concept-ids (seq (set/difference
                                  (set coll-concept-ids)
                                  (set visible-concept-ids)))
        results (get-highest-visible-revisions context deleted-concept-ids result-format)
        ;; when results is nil, hits is 0
        hits (get results :hits 0)
        results (or results {:hits hits :items []})
        total-took (- (System/currentTimeMillis) start-time)
        ;; construct the response results string
        results-str (common-search/search-results->response
                     context
                     ;; pass in a fake query to get the desired response format
                     (qm/query {:concept-type :collection
                                :result-format result-format})
                     (assoc results :took total-took))
        pr-result-format (rfh/printable-result-format result-format)
        log-message (log-search-result-metadata hits "deleted collections"
                                                total-took (:client-id context) (:token context) pr-result-format
                                                "with params %s" (pr-str params))]
    (info log-message)
    {:results results-str
     :hits hits
     :result-format result-format}))

(defn- make-deleted-granules-query
  "With given params, construct query for deleted granules"
  [params]
  (let [{:keys [parent-collection-id provider revision-date]} params
        filters [{:range {:revision-date {:gte revision-date}}}]
        filters (if (seq provider)
                  (conj filters {:term {:provider-id provider}})
                  filters)
        filters (if (seq parent-collection-id)
                  (conj filters {:term {:parent-collection-id parent-collection-id}})
                  filters)]
    {:query {:bool {:must (esq/match-all)
                    :filter {:bool {:filter filters}}}}
     :_source [:concept-id :revision-date :provider-id
               :granule-ur :parent-collection-id]}))

(defn get-deleted-granules
  "Executes elasticsearch searches to find collections that are deleted from a given revision-date.
   This only finds granules that are truly deleted and not searchable.
   granules that are deleted, then later ingested again are not included in the result."
  [context params]
  (let [start-time (System/currentTimeMillis)
        params (dissoc (common-params/sanitize-params params) :sort-key)
        _ (pv/validate-deleted-granules-params params)
        query (make-deleted-granules-query params)
        results (es-helper/search (common-idx/context->conn context cmr.elastic-utils.config/gran-elastic-name)
                                  deleted-granule-index-name
                                  deleted-granule-type-name
                                  query)
        result-format (:result-format params)
        hits (or (get-in results [:hits :total :value]) 0)
        items (map :_source (get-in results [:hits :hits]))
        total-took (- (System/currentTimeMillis) start-time)
        results {:hits hits :items items :took total-took}
        ;; construct the response results string
        results-str (json/generate-string (:items results))
        pr-result-format (rfh/printable-result-format result-format)
        log-message (log-search-result-metadata (:hits results) "deleted granules"
                                                total-took (:client-id context) (:token context) pr-result-format
                                                "with params %s" (pr-str params))]
    (info log-message)
    {:results results-str
     :hits hits
     :result-format result-format}))

(defn- shape-param->tile-set
  "Converts a shape of given type to the set of tiles which the shape intersects"
  [spatial-type shape]
  (set (tile/geometry->tiles (spatial-codec/url-decode spatial-type shape))))

(defn find-tiles-by-geometry
  "Gets all the tile coordinates for the given input parameters. The function returns all the tile
  coordinates if the input parameters does not include any spatial parameters"
  [context params]
  (let [spatial-params (->> params
                            common-params/remove-empty-params
                            u/map-keys->kebab-case
                            (pv/validate-tile-parameters)
                            (#(select-keys % [:bounding-box :point :line :polygon])))]
    (if (seq spatial-params)
      (apply set/union
             (for [[param-name values] spatial-params
                   value (if (sequential? values) values [values])]
               (shape-param->tile-set param-name value)))
      (tile/all-tiles))))

;; TODO fix me, right now defaulting to gran cluster only
(defn clear-scroll
  "Clear the scroll context for the given scroll id"
  [context scroll-id]
  (es-helper/clear-scroll (common-idx/context->conn context cmr.elastic-utils.config/gran-elastic-name) scroll-id))
