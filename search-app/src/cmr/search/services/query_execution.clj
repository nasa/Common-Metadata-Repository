(ns cmr.search.services.query-execution
  (:require [cmr.search.services.transformer :as t]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.search.services.query-walkers.collection-query-resolver :as r]
            [cmr.search.services.query-walkers.collection-concept-id-extractor :as ce]
            [cmr.search.services.acl-service :as acl-service]
            [cmr.common-app.services.search.query-execution :as common-qe]
            [cmr.common-app.services.search.elastic-search-index :as idx]
            [cmr.common-app.services.search.query-model :as cqm]
            [cmr.common-app.services.search.complex-to-simple :as c2s]
            [cmr.common-app.services.search.results :as results]
            [cmr.common-app.services.search.elastic-results-to-query-results :as rc]
            [cmr.common-app.services.search.related-item-resolver :as related-item-resolver])
  (:import [cmr.common_app.services.search.query_model
            StringCondition StringsCondition]))

(def specific-elastic-items-format?
  "The set of formats that are supported for the :specific-elastic-items query execution strategy"
  #{:json :atom :csv :opendata})

(def metadata-result-item-fields
  "Fields of a metadata search result item"
  [:concept-id :revision-id :collection-concept-id :format :metadata])

(defn- specific-items-query?
  "Returns true if the query is only for specific items."
  [{:keys [condition page-num page-size] :as query}]
  (and (#{StringCondition StringsCondition} (type condition))
       (= :concept-id (:field condition))
       (= page-num 1)
       (or (= page-size :unlimited)
           (>= page-size (count (:values condition))))))

(defn- direct-transformer-query?
  "Returns true if the query should be executed directly against the transformer and bypass elastic."
  [{:keys [result-format result-features all-revisions? sort-keys concept-type] :as query}]
  (and (specific-items-query? query)
       (t/transformer-supported-format? result-format)
       (not all-revisions?)
       ;; Facets requires elastic search
       (not-any? #(= % :facets) result-features)
       ;; Sorting hasn't been specified or is set to the default value
       ;; Note that we don't actually sort items by the default sort keys
       ;; See issue CMR-607
       (or (nil? sort-keys) (= (cqm/default-sort-keys concept-type) sort-keys))))

(defn- specific-items-from-elastic-query?
  "Returns true if the query is only for specific items that will come directly from elastic search.
  This query type is split out because it is faster to bypass ACLs and apply them afterwards
  than to apply them ahead of time to the query."
  [{:keys [result-format all-revisions?] :as query}]
  (and (specific-items-query? query)
       (not all-revisions?)
       (specific-elastic-items-format? result-format)))

(defn- collection-and-granule-execution-strategy-determiner
  "Determines the execution strategy to use for the given query."
  [query]
  (cond
    (direct-transformer-query? query) :direct-transformer
    (specific-items-from-elastic-query? query) :specific-elastic-items
    :else :elasticsearch))

(defmethod common-qe/query->execution-strategy :collection
  [query]
  (collection-and-granule-execution-strategy-determiner query))

(defmethod common-qe/query->execution-strategy :granule
  [query]
  (collection-and-granule-execution-strategy-determiner query))


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

(defmethod common-qe/execute-query :direct-transformer
  [context query]
  (let [{:keys [result-format skip-acls?]} query
        concept-ids (query->concept-ids query)
        tresults (t/get-latest-formatted-concepts context concept-ids result-format skip-acls?)
        items (map #(select-keys % metadata-result-item-fields) tresults)
        results (results/map->Results {:hits (count items) :items items :result-format result-format})]
    (common-qe/post-process-query-result-features context query nil results)))

(defmethod common-qe/execute-query :specific-elastic-items
  [context query]
  (let [processed-query (->> query
                             (common-qe/pre-process-query-result-features context)
                             (r/resolve-collection-queries context)
                             (c2s/reduce-query context))
        elastic-results (idx/execute-query context processed-query)
        query-results (rc/elastic-results->query-results context query elastic-results)
        query-results (if (:skip-acls? query)
                        query-results
                        (update-in query-results [:items]
                                   (partial acl-service/filter-concepts context)))]
    (common-qe/post-process-query-result-features context query elastic-results query-results)))

(defmethod common-qe/concept-type-specific-query-processing :granule
  [context query]
  (let [processed-query (r/resolve-collection-queries context query)
        collection-ids (ce/extract-collection-concept-ids processed-query)]
    [(assoc context
            :query-collection-ids collection-ids
            :query-concept-type (:concept-type query))
     processed-query]))

(defmethod common-qe/concept-type-specific-query-processing :collection
  [context query]
  [context (related-item-resolver/resolve-related-item-conditions query context)])



