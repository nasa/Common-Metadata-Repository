(ns cmr.search.services.query-execution
  (:require [cmr.search.models.query :as qm]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.search.services.transformer :as t]
            [cmr.search.models.results :as results]
            [cmr.search.data.elastic-results-to-query-results :as rc]
            [cmr.search.data.complex-to-simple :as c2s]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.search.services.query-walkers.collection-query-resolver :as r]
            [cmr.search.services.query-walkers.collection-concept-id-extractor :as ce]
            [cmr.search.services.acl-service :as acl-service]
            [cmr.search.services.query-walkers.related-item-resolver :as related-item-resolver])
  (:import cmr.search.models.query.StringsCondition
           cmr.search.models.query.StringCondition))

(def specific-elastic-items-format?
  "The set of formats that are supported for the :specific-elastic-items query execution strategy"
  #{:json :atom :csv :opendata})

(def metadata-result-item-fields
  "Fields of a metadata search result item"
  [:concept-id :revision-id :collection-concept-id :format :metadata])

(defn- specific-items-query?
  "Returns true if the query is only for specific items."
  [{:keys [condition concept-type page-num page-size] :as query}]
  (and (#{StringCondition StringsCondition} (type condition))
       (= :concept-id (:field condition))
       (= page-num 1)
       (or (= page-size :unlimited)
           (>= page-size (count (:values condition))))))

(defn- direct-transformer-query?
  "Returns true if the query should be executed directly against the transformer and bypass elastic."
  [{:keys [result-format result-features all-revisions? sort-keys] :as query}]
  (and (specific-items-query? query)
       (t/transformer-supported-format? result-format)
       (not all-revisions?)
       ;; Facets requires elastic search
       (not-any? #(= % :facets) result-features)
       ;; sorting has been left at the default level
       ;; Note that we don't actually sort items by the default sort keys
       ;; See issue CMR-607
       (nil? sort-keys)))


(defn- specific-items-from-elastic-query?
  "Returns true if the query is only for specific items that will come directly from elastic search.
  This query type is split out because it is faster to bypass ACLs and apply them afterwards
  than to apply them ahead of time to the query."
  [{:keys [result-format all-revisions?] :as query}]
  (and (specific-items-query? query)
       (not all-revisions?)
       (specific-elastic-items-format? result-format)))

(defn- query->execution-strategy
  "Determines the execution strategy to use for the given query."
  [query]
  (cond
    (direct-transformer-query? query) :direct-transformer
    (specific-items-from-elastic-query? query) :specific-elastic-items
    :else :elastic))

(defmulti query->concept-ids
  "Extract concept ids from a concept-id only query"
  (fn [query]
    (class (:condition query))))

(defmethod query->concept-ids StringCondition
  [query]
  [(get-in query [:condition :value])])

(defmethod query->concept-ids StringsCondition
  [query]
  (get-in query [:condition :values]))

(defmulti pre-process-query-result-feature
  "Applies result feature changes to the query before it is executed. Should return the query with
  any changes necessary to apply the feature."
  (fn [context query feature]
    feature))

(defmethod pre-process-query-result-feature :default
  [context query feature]
  ; Does nothing by default
  query)

(defmulti post-process-query-result-feature
  "Processes the results found by the query to add additional information or other changes
  based on the particular feature enabled by the user."
  (fn [context query elastic-results query-results feature]
    feature))

(defmethod post-process-query-result-feature :default
  [context query elastic-results query-results feature]
  ; Does nothing by default
  query-results)

(defn pre-process-query-result-features
  "Applies each result feature change to the query before it is executed. Returns the updated query."
  [context query]
  (reduce (partial pre-process-query-result-feature context)
          query
          (:result-features query)))

(defn post-process-query-result-features
  "Processes result features that execute after a query results have been found."
  [context query elastic-results query-results]
  (reduce (partial post-process-query-result-feature context query elastic-results)
          query-results
          (:result-features query)))

(defmulti execute-query
  "Executes the query using the most appropriate mechanism"
  (fn [context query]
    (query->execution-strategy query)))

(defmethod execute-query :direct-transformer
  [context query]
  (let [{:keys [result-format skip-acls?]} query
        concept-ids (query->concept-ids query)
        tresults (t/get-latest-formatted-concepts context concept-ids result-format skip-acls?)
        items (map #(select-keys % metadata-result-item-fields) tresults)
        results (results/map->Results {:hits (count items) :items items :result-format result-format})]
    (post-process-query-result-features context query nil results)))

(defmethod execute-query :specific-elastic-items
  [context query]
  (let [processed-query (->> query
                             (pre-process-query-result-features context)
                             (r/resolve-collection-queries context)
                             (c2s/reduce-query context))
        elastic-results (idx/execute-query context processed-query)
        query-results (rc/elastic-results->query-results context query elastic-results)
        query-results (if (:skip-acls? query)
                        query-results
                        (update-in query-results [:items]
                                   (partial acl-service/filter-concepts context)))]
    (post-process-query-result-features context query elastic-results query-results)))

(defmulti concept-type-specific-query-processing
  "Performs processing on the context and the query specific to the concept type being searched"
  (fn [context query]
    (:concept-type query)))

(defmethod concept-type-specific-query-processing :granule
  [context query]
  (let [processed-query (r/resolve-collection-queries context query)
        collection-ids (ce/extract-collection-concept-ids processed-query)]
    [(assoc context
            :query-collection-ids collection-ids
            :query-concept-type (:concept-type query))
     processed-query]))

(defmethod concept-type-specific-query-processing :collection
  [context query]
  [context (related-item-resolver/resolve-related-item-conditions query context)])

(defmethod concept-type-specific-query-processing :default
  [context query]
  [context query])

(defmethod execute-query :elastic
  [context query]
  (let [pre-processed-query (pre-process-query-result-features context query)
        [context processed-query] (concept-type-specific-query-processing
                                    context pre-processed-query)
        elastic-results (->> processed-query
                             (#(if (:skip-acls? %)
                                 %
                                 (acl-service/add-acl-conditions-to-query context %)))
                             (c2s/reduce-query context)
                             (idx/execute-query context))
        query-results (rc/elastic-results->query-results context pre-processed-query elastic-results)]
    (post-process-query-result-features context query elastic-results query-results)))

