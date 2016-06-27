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
  (:require [cmr.search.data.elastic-search-index :as idx]
            [cmr.common-app.services.search.query-model :as qm]
            [cmr.search.services.result-format-helper :as rfh]
            [cmr.common.mime-types :as mt]
            [cmr.common-app.services.search :as common-search]
            [cmr.common-app.services.search.params :as common-params]

            ;; parameter-converters
            ;; These must be required here to make multimethod implementations available.
            [cmr.search.services.parameters.conversion :as p]
            [cmr.search.services.parameters.converters.collection-query]
            [cmr.search.services.parameters.converters.temporal]
            [cmr.search.services.parameters.converters.attribute]
            [cmr.search.services.parameters.converters.orbit-number]
            [cmr.search.services.parameters.converters.equator-crossing-longitude]
            [cmr.search.services.parameters.converters.equator-crossing-date]
            [cmr.search.services.parameters.converters.spatial]
            [cmr.search.services.parameters.converters.science-keyword]
            [cmr.search.services.parameters.converters.two-d-coordinate-system]

            ;; json converters
            [cmr.search.services.json-parameters.conversion :as jp]
            [cmr.search.services.json-parameters.converters.attribute]

            ;; aql
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.services.aql.converters.temporal]
            [cmr.search.services.aql.converters.spatial]
            [cmr.search.services.aql.converters.science-keywords]
            [cmr.search.services.aql.converters.attribute]
            [cmr.search.services.aql.converters.attribute-name]
            [cmr.search.services.aql.converters.two-d-coordinate-system]

            ;; Validation
            [cmr.search.validators.validation]
            [cmr.search.validators.temporal]
            [cmr.search.validators.attribute]
            [cmr.search.validators.orbit-number]
            [cmr.search.validators.equator-crossing-longitude]
            [cmr.search.validators.equator-crossing-date]

            ;; Complex to simple converters
            [cmr.search.data.complex-to-simple-converters.attribute]
            [cmr.search.data.complex-to-simple-converters.orbit]
            [cmr.search.data.complex-to-simple-converters.temporal]
            [cmr.search.data.complex-to-simple-converters.spatial]
            [cmr.search.data.complex-to-simple-converters.two-d-coordinate-system]
            cmr.search.data.complex-to-simple-converters.has-granules

            ;; Query Results Features
            [cmr.search.services.query-execution]
            [cmr.search.services.query-execution.granule-counts-results-feature]
            [cmr.search.services.query-execution.has-granules-results-feature :as hgrf]
            [cmr.search.services.query-execution.facets.facets-results-feature]
            [cmr.search.services.query-execution.facets.facets-v2-results-feature]
            [cmr.search.services.query-execution.highlight-results-feature]
            [cmr.search.services.query-execution.tags-results-feature]

            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.parameters.provider-short-name :as psn]
            [cmr.search.services.parameters.parameter-validation :as pv]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.search.results-handlers.provider-holdings :as ph]
            [cmr.search.services.transformer :as t]
            [cmr.metadata-db.services.search-service :as mdb-search]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.api.route-helpers :as rh]
            [cmr.common.concepts :as cc]
            [cmr.common.services.errors :as errors]
            [cmr.common.util :as u]
            [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clojure.string :as s]
            [cmr.spatial.tile :as tile]
            [cmr.spatial.codec :as spatial-codec]
            [cmr.common.log :refer (debug info warn error)]))


(defn- sanitize-aql-params
  "When content-type is not set for aql searches, the aql will get mistakenly parsed into params.
  This function removes it, santizes the params and returns the end result."
  [params]
  (-> (select-keys params (filter keyword? (keys params)))
      common-params/sanitize-params))

;; CMR-2553 Remove this function.
(defn- drop-ignored-params
  "At times, there are unsupported search params in parameter search that we simply want to ignore
  rather than raise error. This function removes tag-namespace from the params and returns the params."
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

(defn find-concepts-by-parameters
  "Executes a search for concepts using the given parameters. The concepts will be returned with
  concept id and native provider id along with hit count and timing info."
  [context concept-type params]
  (let [;; tag-data param has tag-key as its key, which could be any arbitrary string.
        ;; sanitize-params function traded data integrity for easier processing, e.g.
        ;; it converts "tag1" into :tag-1 and it doesn't work for tag-keys.
        ;; To work around this problem, we extract the tag-data param out first,
        ;; then add it back in after the sanitize-params call.
        tag-data (or (:tag-data params) (:tag_data params))
        [query-creation-time query] (u/time-execution
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
                                           (pv/validate-parameters concept-type)
                                           (common-params/parse-parameter-query concept-type)))
        [find-concepts-time results] (u/time-execution
                                       (common-search/find-concepts context
                                                                    concept-type
                                                                    query))
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d %ss in %d ms in format %s with params %s."
                  (:hits results) (name concept-type) total-took
                  (rfh/printable-result-format (:result-format query)) (pr-str params)))
    (assoc results :took total-took)))

(defn find-concepts-by-json-query
  "Executes a search for concepts using the given JSON. The concepts will be returned with
  concept id and native provider id along with hit count and timing info."
  [context concept-type params json-query]
  (let [[query-creation-time query] (u/time-execution
                                      (-> (jp/parse-json-query concept-type
                                                               (common-params/sanitize-params params)
                                                               json-query)))

        [find-concepts-time results] (u/time-execution
                                       (common-search/find-concepts context
                                                                    concept-type
                                                                    query))
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d %ss in %d ms in format %s with JSON Query %s and query params %s."
                  (:hits results) (name concept-type) total-took
                  (rfh/printable-result-format (:result-format query)) json-query (pr-str params)))
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
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d %ss in %d ms in format %s with aql: %s."
                  (:hits results) (name concept-type) total-took
                  (rfh/printable-result-format (:result-format query)) aql))
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
      "Concept with concept-id [%s] and revision-id [%s] could not be found." concept-id revision-id)))

(defn find-concept-by-id
  "Executes a search to metadata-db and returns the concept with the given cmr-concept-id."
  [context result-format concept-id]
  (if (contains? #{:atom :json} result-format)
    ;; do a query and use single-result->response
    (let [query (common-params/parse-parameter-query (cc/concept-id->type concept-id)
                                                     {:page-size 1
                                                      :concept-id concept-id
                                                      :result-format result-format})
          results (qe/execute-query context query)]
      (when (zero? (:hits results))
        (throw-id-not-found concept-id))
      {:results (common-search/single-result->response context query results)
       :result-format result-format})
    ;; else
    (let [concept (first (t/get-latest-formatted-concepts context [concept-id] result-format))]
      (when-not concept
        (throw-id-not-found concept-id))
      {:results (:metadata concept)
       :result-format (rfh/mime-type->search-result-format (:format concept))})))

(defn find-concept-by-id-and-revision
  "Executes a search to metadata-db and returns the concept with the given concept-id and
  revision-id."
  [context result-format concept-id revision-id]
  ;; We don't store revision id in the search index, so we can't use shortcuts for json/atom
  ;; like we do in find-concept-by-id.
  (let [concept (t/get-formatted-concept context concept-id revision-id result-format)]
    (when-not concept
      (throw-concept-revision-not-found concept-id revision-id))
    {:results (:metadata concept)
     :result-format (rfh/mime-type->search-result-format (:format concept))}))

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
                   p/timeline-parameters->query)
        results (qe/execute-query context query)]
    (common-search/search-results->response context query results)))

(defn get-collections-by-providers
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
                          :fields [:entry-title :provider-id]
                          :skip-acls? skip-acls?})
         results (qe/execute-query context query)]
     (:items results))))


(defn get-provider-holdings
  "Executes elasticsearch search to get provider holdings"
  [context params]
  (let [{:keys [provider-id echo-compatible]} (u/map-keys->kebab-case params)
        ;; make sure provider-ids is sequential
        provider-ids (if (or (nil? provider-id) (sequential? provider-id))
                       provider-id
                       [provider-id])
        ;; get all collections limited by the list of providers in json format
        collections (get-collections-by-providers context provider-ids false)
        ;; get a mapping of collection to granule count
        collection-granule-count (idx/get-collection-granule-counts context provider-ids)
        ;; combine the granule count into collections to form provider holdings
        provider-holdings (map #(assoc % :granule-count (get collection-granule-count (:concept-id %) 0))
                               collections)]
    [provider-holdings
     (ph/provider-holdings->string
       (:result-format params) provider-holdings {:echo-compatible? (= "true" echo-compatible)})]))

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
      (apply clojure.set/union
             (for [[param-name values] spatial-params
                   value (if (sequential? values) values [values])]
               (shape-param->tile-set param-name value)))
      (tile/all-tiles))))
