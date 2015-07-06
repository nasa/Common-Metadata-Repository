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
            [cmr.search.models.query :as qm]
            [cmr.common.mime-types :as mt]

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

            ;; aql
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.services.aql.converters.temporal]
            [cmr.search.services.aql.converters.spatial]
            [cmr.search.services.aql.converters.science-keywords]
            [cmr.search.services.aql.converters.attribute]
            [cmr.search.services.aql.converters.attribute-name]
            [cmr.search.services.aql.converters.two-d-coordinate-system]

            ;; Validation
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.temporal]
            [cmr.search.validators.date-range]
            [cmr.search.validators.attribute]
            [cmr.search.validators.numeric-range]
            [cmr.search.validators.orbit-number]
            [cmr.search.validators.equator-crossing-longitude]
            [cmr.search.validators.equator-crossing-date]

            ;; Complex to simple converters
            [cmr.search.data.complex-to-simple-converters.attribute]
            [cmr.search.data.complex-to-simple-converters.orbit]
            [cmr.search.data.complex-to-simple-converters.temporal]
            [cmr.search.data.complex-to-simple-converters.spatial]
            [cmr.search.data.complex-to-simple-converters.two-d-coordinate-system]

            ;; Query Results Features
            [cmr.search.services.query-execution.granule-counts-results-feature]
            [cmr.search.services.query-execution.has-granules-results-feature :as hgrf]
            [cmr.search.services.query-execution.facets-results-feature]

            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.parameters.parameter-validation :as pv]
            [cmr.search.services.query-execution :as qe]
            [cmr.search.results-handlers.provider-holdings :as ph]
            [cmr.search.services.transformer :as t]
            [cmr.metadata-db.services.search-service :as mdb-search]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.common.concepts :as cc]
            [cmr.common.services.errors :as errors]
            [cmr.common.util :as u]
            [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clojure.string :as s]
            [cmr.spatial.tile :as tile]
            [cmr.spatial.codec :as spatial-codec]
            [cmr.common.log :refer (debug info warn error)]))

(deftracefn validate-query
  "Validates a query model. Throws an exception to return to user with errors.
  Returns the query model if validation is successful so it can be chained with other calls."
  [context query]
  (let [errors (v/validate query)]
    (when-not (empty? errors)
      (errors/throw-service-errors :bad-request errors))
    query))

(defmulti search-results->response
  "Converts query search results into a string response."
  (fn [context query results]
    (:result-format query)))

(defmulti single-result->response
  "Returns a string representation of a single concept in the format
  specified in the query."
  (fn [context query results]
    (:result-format query)))

(defn- sanitize-sort-key
  "Sanitizes a single sort key preserving the direction character."
  [sort-key]
  (if-let [[_ dir-char field] (re-find #"([^a-zA-Z])?(.*)" sort-key)]
    (str dir-char (csk/->kebab-case field))
    sort-key))

(defn- remove-empty-params
  "Returns the params after removing the ones with value of an empty string
  or string with just whitespaces"
  [params]
  (let [not-empty-string? (fn [value]
                            (not (and (string? value) (= "" (s/trim value)))))]
    (into {} (filter (comp not-empty-string? second) params))))

(defn- sanitize-params
  "Manipulates the parameters to make them easier to process"
  [params]
  (-> params
      remove-empty-params
      u/map-keys->kebab-case
      (update-in [:sort-key] #(when % (if (sequential? %)
                                        (map sanitize-sort-key % )
                                        (sanitize-sort-key %))))))

(defn- sanitize-aql-params
  "When content-type is not set for aql searches, the aql will get mistakenly parsed into params.
  This function removes it, santizes the params and returns the end result."
  [params]
  (-> (select-keys params (filter keyword? (keys params)))
      sanitize-params))

(defn- find-concepts
  "Common functionality for find-concepts-by-parameters and find-concepts-by-aql."
  [context concept-type params query-creation-time query]
  (validate-query context query)
  (let [[query-execution-time results] (u/time-execution (qe/execute-query context query))
        took (+ query-creation-time query-execution-time)
        [result-gen-time result-str] (u/time-execution
                                       (search-results->response
                                         context query (assoc results :took took)))
        total-took (+ query-creation-time query-execution-time result-gen-time)]
    (debug "query-creation-time:" query-creation-time
           "query-execution-time:" query-execution-time
           "result-gen-time:" result-gen-time)
    {:results result-str :hits (:hits results) :took took :total-took total-took
     :result-format (:result-format query)}))

(deftracefn find-concepts-by-parameters
  "Executes a search for concepts using the given parameters. The concepts will be returned with
  concept id and native provider id along with hit count and timing info."
  [context concept-type params]
  (let [[query-creation-time query] (u/time-execution
                                      (->> params
                                           sanitize-params
                                           ;; handle legacy parameters
                                           lp/replace-parameter-aliases
                                           (lp/process-legacy-multi-params-conditions concept-type)
                                           (lp/replace-science-keywords-or-option concept-type)

                                           (pv/validate-parameters concept-type)
                                           (p/parse-parameter-query concept-type)))
        results (find-concepts context concept-type params query-creation-time query)]
    (info (format "Found %d %ss in %d ms in format %s with params %s."
                  (:hits results) (name concept-type) (:total-took results) (:result-format query)
                  (pr-str params)))
    results))

(deftracefn find-concepts-by-json-query
  "Executes a search for concepts using the given JSON. The concepts will be returned with
  concept id and native provider id along with hit count and timing info."
  [context concept-type params json-query]
  (let [[query-creation-time query] (u/time-execution
                                      (jp/parse-json-query concept-type
                                                           (sanitize-params params)
                                                           json-query))
        results (find-concepts context concept-type params query-creation-time query)]
    ;; TODO refactor this out into find-concepts or some common function
    (info (format "Found %d %ss in %d ms in format %s with JSON Query %s and query params %s."
                  (:hits results) (name concept-type) (:total-took results) (:result-format query)
                  json-query (pr-str params)))
    results))

(deftracefn find-concepts-by-aql
  "Executes a search for concepts using the given aql. The concepts will be returned with
  concept id and native provider id along with hit count and timing info."
  [context params aql]
  (let [params (-> params
                   sanitize-aql-params
                   lp/replace-parameter-aliases)
        [query-creation-time query] (u/time-execution (a/parse-aql-query params aql))
        concept-type (:concept-type query)
        results (find-concepts context concept-type params query-creation-time query)]
    (info (format "Found %d %ss in %d ms in format %s with aql: %s."
                  (:hits results) (name concept-type) (:total-took results) (:result-format query)
                  aql))
    results))

(defn- throw-id-not-found
  [concept-id]
  (errors/throw-service-error
    :not-found
    (format "Concept with concept-id: %s could not be found" concept-id)))

(deftracefn find-concept-by-id
  "Executes a search to metadata-db and returns the concept with the given cmr-concept-id."
  [context result-format concept-id]
  (if (contains? #{:atom :json} result-format)
    ;; do a query and use single-result->response
    (let [query (p/parse-parameter-query (cc/concept-id->type concept-id)
                                         {:page-size 1
                                          :concept-id concept-id
                                          :result-format result-format})
          results (qe/execute-query context query)]
      (when (zero? (:hits results))
        (throw-id-not-found concept-id))
      {:results (single-result->response context query results)
       :result-format result-format})
    ;; else
    (let [concept (first (t/get-latest-formatted-concepts context [concept-id] result-format))]
      (when-not concept
        (throw-id-not-found concept-id))
      {:results (:metadata concept) :result-format (mt/mime-type->format (:format concept))})))

(deftracefn find-concept-revisions
  "Uses the metadata-db to find concept revisions for the given parameters"
  [context params]
  ;; Prepare params as if they were sent over HTTP to metadata db
  (let [params (u/map-keys->kebab-case params)]
    (mdb-search/find-concepts (t/context->metadata-db-context context) params)))

(deftracefn get-granule-timeline
  "Finds granules and returns the results as a list of intervals of granule counts per collection."
  [context params]
  (let [query (->> params
                   sanitize-params
                   ;; handle legacy parameters
                   lp/replace-parameter-aliases
                   (lp/process-legacy-multi-params-conditions :granule)
                   (lp/replace-science-keywords-or-option :granule)
                   pv/validate-timeline-parameters
                   p/timeline-parameters->query)
        results (qe/execute-query context query)]
    (search-results->response context query results)))

(deftracefn get-collections-by-providers
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


(deftracefn get-provider-holdings
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

(deftracefn find-tiles-by-geometry
  "Gets all the tile coordinates for the given input parameters. The function returns all the tile
  coordinates if the input parameters does not include any spatial parameters"
  [context params]
  (let [spatial-params (->> params
                            remove-empty-params
                            u/map-keys->kebab-case
                            (pv/validate-tile-parameters)
                            (#(select-keys % [:bounding-box :point :line :polygon])))]
    (if (seq spatial-params)
      (apply clojure.set/union
             (for [[param-name values] spatial-params
                   value (if (sequential? values) values [values])]
               (shape-param->tile-set param-name value)))
      (tile/all-tiles))))
