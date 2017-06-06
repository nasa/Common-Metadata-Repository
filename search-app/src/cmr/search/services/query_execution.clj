(ns cmr.search.services.query-execution
  (:require
   [clojure.set :as set]
   [cmr.common-app.services.search.complex-to-simple :as c2s]
   [cmr.common-app.services.search.elastic-results-to-query-results :as rc]
   [cmr.common-app.services.search.elastic-search-index :as idx]
   [cmr.common-app.services.search.query-execution :as common-qe]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common-app.services.search.related-item-resolver :as related-item-resolver]
   [cmr.common-app.services.search.results-model :as results]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.util :as util]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as mt]
   [cmr.search.services.acl-service :as acl-service]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as fv2rf]
   [cmr.search.services.query-walkers.collection-concept-id-extractor :as ce]
   [cmr.search.services.query-walkers.collection-query-resolver :as r]
   [cmr.search.services.query-walkers.facet-condition-resolver :as facet-condition-resolver]
   [cmr.transmit.config :as tc])
  (:import
   (cmr.common_app.services.search.query_model StringCondition StringsCondition)))

(def specific-elastic-items-format?
  "The set of formats that are supported for the :specific-elastic-items query execution strategy"
  #{:json :atom :csv :opendata})

(defn- specific-items-query?
  "Returns true if the query is only for specific items."
  [{:keys [condition offset page-size] :as query}]
  (and (#{StringCondition StringsCondition} (type condition))
       (= :concept-id (:field condition))
       (= 0 offset)
       (or (= page-size :unlimited)
           (>= page-size (count (:values condition))))))

(defn- direct-db-query?
  "Returns true if the query should be executed directly against the database and bypass elastic."
  [{:keys [result-format result-features all-revisions? sort-keys concept-type] :as query}]
  (and ;;Collections won't be direct transformer queries since their metadata is cached. We'll use
       ;; elastic + the metadata cache for them
       (= :granule concept-type)
       (specific-items-query? query)
       (mt/transformer-supported-format? result-format)
       (not all-revisions?)

       ;; Facets and tags require elastic search
       (not-any? #(contains? #{:facets :tags} %) result-features)
       ;; Sorting hasn't been specified or is set to the default value
       ;; Note that we don't actually sort items by the default sort keys
       ;; See issue CMR-607
       (or (nil? sort-keys) (= (cqm/default-sort-keys concept-type) sort-keys))))

(defn- specific-items-from-elastic-query?
  "Returns true if the query is only for specific items that will come directly from elastic
  search.

  This query type is split out because it is faster to bypass ACLs and apply them afterwards
  than to apply them ahead of time to the query."
  [{:keys [result-format all-revisions?] :as query}]
  (and (specific-items-query? query)
       (not all-revisions?)
       (specific-elastic-items-format? result-format)))

(defn- complicated-facets?
  "Returns true if the query has v2 facets and at least one of the facets fields in the query."
  [{:keys [result-format all-revisions?] :as query}]
  (and (not= false (:complicated-facets query))
       ((set (:result-features query)) :facets-v2)
       (util/any? #(facet-condition-resolver/has-field? query %)
                  fv2rf/facets-v2-params)))

(defn- collection-and-granule-execution-strategy-determiner
  "Determines the execution strategy to use for the given query."
  [query]
  (cond
    (direct-db-query? query) :direct-db
    (specific-items-from-elastic-query? query) :specific-elastic-items
    (complicated-facets? query) :complicated-facets
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

(defmethod common-qe/execute-query :direct-db
  [context query]
  (let [{:keys [result-format skip-acls?]} query
        concept-ids (query->concept-ids query)
        items (metadata-cache/get-latest-formatted-concepts
               context concept-ids result-format skip-acls?)
        results (results/map->Results
                  {:hits (count items)
                   :items items
                   :result-format result-format})]
    (common-qe/post-process-query-result-features context query nil results)))

(defmethod common-qe/execute-query :specific-elastic-items
  [context query]
  (let [processed-query (->> query
                             (common-qe/pre-process-query-result-features context)
                             (r/resolve-collection-queries context)
                             (c2s/reduce-query context))
        elastic-results (idx/execute-query context processed-query)
        query-results (rc/elastic-results->query-results context query elastic-results)
        query-results (if (or (tc/echo-system-token? context) (:skip-acls? query))
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

(defn- get-facets-for-field
  "Returns the facets search result on the given field by executing an elasticsearch query
   with the given field removed from the filter to only retrieve the facet info on that field."
  [context query field]
  (let [query (-> query
                  (facet-condition-resolver/adjust-facet-query field)
                  (assoc :result-features [:facets-v2])
                  (assoc :facet-fields [field])
                  (assoc :page-size 0))]
    (common-qe/execute-query context query)))

(defn- merge-facets
  "Returns the facets by merging the two lists of facets and sort the fields in the correct order."
  [facets others]
  (let [sort-fn (fn [facet] (.indexOf fv2rf/v2-facets-result-field-in-order (:title facet)))]
    (sort-by sort-fn (concat facets others))))

(defn- merge-search-result-facets
  "Returns search result by merging the base result and the facet results."
  [base-result facet-results]
  (let [individual-facets (mapcat #(get-in % [:facets :children]) facet-results)]
    (-> base-result
        (assoc-in [:facets :has_children] true)
        (update-in [:facets :children] #(merge-facets % individual-facets)))))

(defmethod common-qe/execute-query :complicated-facets
  [context query]
  ;; A query can only be a complicated facets query when it has never been executed as part of
  ;; an execution. We set :complicated-facets to false and add the facets fields to be returned
  ;; from aggregate to the query to facilitate the aggregation search and result generation.
  ;; We execute a base query with all the parameters to get the result and facets of fields that
  ;; are not in the query, then we merge this base result with only the facets for each individual
  ;; facet field that is in the query.
  (let [facet-fields-in-query (filter #(facet-condition-resolver/has-field? query %)
                                      fv2rf/facets-v2-params)
        base-facet-fields (set/difference (set fv2rf/facets-v2-params) (set facet-fields-in-query))
        query (assoc query :complicated-facets false :facet-fields base-facet-fields)
        base-result (common-qe/execute-query context query)
        facet-results (map #(get-facets-for-field context query %) facet-fields-in-query)]
    (merge-search-result-facets base-result facet-results)))
