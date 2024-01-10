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
   (cmr.common_app.services.search.query_model Query StringCondition StringsCondition ConditionGroup)
   (cmr.search.models.query CollectionQueryCondition)))

(def specific-elastic-items-format?
  "The set of formats that are supported for the :specific-elastic-items query execution strategy"
  #{:json :atom :csv :opendata})

(defn- specific-items-query?
  "Returns true if the query is only for specific items."
  [{:keys [condition offset page-size gran-specific-items-query?] :as query}]
  (or gran-specific-items-query?
      (and (#{StringCondition StringsCondition} (type condition))
           (= :concept-id (:field condition))
           (= 0 offset)
           (or (= page-size :unlimited)
               (>= page-size (count (:values condition)))))))

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
  [{:keys [result-format all-revisions? concept-type] :as query}]
  (and (not= false (:complicated-facets query))
       ((set (:result-features query)) :facets-v2)
       (util/any-true? #(facet-condition-resolver/has-field? query %)
                       (fv2rf/facets-v2-params concept-type))))

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
    (class query)))

(defmethod query->concept-ids Query
  [query]
  (query->concept-ids (:condition query)))

(defmethod query->concept-ids ConditionGroup
  [query]
  (mapcat query->concept-ids (:conditions query)))

(defmethod query->concept-ids CollectionQueryCondition
  [query]
  nil)

(defmethod query->concept-ids StringCondition
  [condition]
  [(:value condition)])

(defmethod query->concept-ids StringsCondition
  [condition]
  (:values condition))

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

(defn- filter-query-results-with-acls
  "Returns the query results after ACLs are applied to filter out items
  that the current user does not have access to."
  [context query-results]
  (let [original-item-count (count (:items query-results))
        items (acl-service/filter-concepts context (:items query-results))
        item-count (count items)]
    (-> query-results
        (assoc :items items)
        (update :hits - (- original-item-count item-count)))))

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
                        (filter-query-results-with-acls context query-results))]
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

(defn- update-facets
  "Update the orig-facets-with-count using the info in the all-facets-with-count."
  [orig-facets-with-count all-facets-with-count]
  (for [title-val (map :title orig-facets-with-count)
        :let [match (some #(when (= title-val (:title %)) %) all-facets-with-count)]
        :when (some? match)]
    match))

(defn- get-facets-with-count
  "Extract out the facet part that contains title and count, amoung other things:
  [{:title \"t1\" :count 0} {:title \"NonExist\" :count 0} {:title \"t3\" :count 1}]
  from the facets result.  Note: facets-result is the result from a particular field,
  so there is only one child under :facets :children, which is why we use first to
  get to the child."
  [facets-result]
  (-> facets-result
      (get-in [:facets :children])
      first
      :children))

(defn- get-facets-for-field-again?
  "Check to see if any facet count in facets-for-field is 0
  and that facets-size-for-field is not set to return all facets.
  If so, return true."
  [facets-size-for-field facets-for-field]
  (let [facets-with-count (get-facets-with-count facets-for-field)]
    (and (or (nil? facets-size-for-field)
             ;; facets-size-for-field could be a string or a long so reflection will be used
             (< (Integer. facets-size-for-field) fv2rf/UNLIMITED_TERMS_SIZE))
         (some #(= 0 (:count %)) facets-with-count))))

(defn- update-facets-for-field
  "Update the counts in facets-for-field with the counts in all-facets-for-field.
  orig-facets-with-count is like:
  [{:title \"t1\" :count 0} {:title \"NonExist\" :count 0} {:title \"t3\" :count 1}]
  all-facets-with-count is like:
  [{:title \"t1\" :count 1} {:title \"NonExist\" :count 0} {:title \"t3\" :count 1}
   {:title \"t4\" :count 2} {:title \"t5\" :count 1}]
  updated-orig-facets-with-count is:
  [{:title \"t1\" :count 1} {:title \"NonExist\" :count 0} {:title \"t3\" :count 1}]"
  [facets-for-field all-facets-for-field]
  (let [orig-first-facets-children (first (get-in facets-for-field [:facets :children]))
        orig-facets-with-count (get-facets-with-count facets-for-field)
        all-facets-with-count  (get-facets-with-count all-facets-for-field)
        ;; replace the original facets with the facets in all-facets that have the same :title.
        updated-orig-facets-with-count (update-facets orig-facets-with-count all-facets-with-count)
        updated-orig-first-facets-children
        (assoc orig-first-facets-children :children updated-orig-facets-with-count)]
    ;;Return the facets-for-field with the first facets children being the updated-orig-first-facets-children
    (assoc-in facets-for-field [:facets :children] [updated-orig-first-facets-children])))

(defn- get-facets-for-field
  "Returns the facets search result on the given field by executing an elasticsearch query
  with the given field removed from the filter to only retrieve the facet info on that field."
  [context query field]
  (let [query (-> query
                  (facet-condition-resolver/adjust-facet-query field)
                  (assoc :result-features [:facets-v2])
                  (assoc :facet-fields [field])
                  (assoc :page-size 0))
        facets-size-map (:facets-size query)
        facets-size-for-field (field facets-size-map)
        facets-for-field (common-qe/execute-query context query)]
    ;; Check if any facet contains 0 count, if so, and the
    ;; facets-size-for-field is not showing all facets, then we will try to
    ;; call get-facets-for-field again - with the query being query-with-all-facets-size.
    (if (get-facets-for-field-again? facets-size-for-field facets-for-field)
      (let [query-with-all-facets-size
            (assoc query :facets-size (merge facets-size-map {field fv2rf/UNLIMITED_TERMS_SIZE}))
            all-facets-for-field (get-facets-for-field context query-with-all-facets-size field)]
        (update-facets-for-field facets-for-field all-facets-for-field))
      facets-for-field)))

(defn- merge-facets
  "Returns the facets by merging the two lists of facets and sort the fields in the correct order.
  If a facet with the same title already exists in others, overwrite that facet with the one
  provided in facets."
  [concept-type facets others]
  (let [facets-sort-fn (fn [facet]
                         (let [ordered-fields (fv2rf/v2-facets-result-field-in-order concept-type)]
                           (.indexOf ordered-fields (:title facet))))
        facet-titles (set (map :title facets))
        unique-others (remove #(contains? facet-titles (:title %)) others)]
    (sort-by facets-sort-fn (concat facets unique-others))))

(defn- merge-search-result-facets
  "Returns search result by merging the base result and the facet results."
  [concept-type base-result facet-results]
  (let [individual-facets (mapcat #(get-in % [:facets :children]) facet-results)]
    (-> base-result
        (assoc-in [:facets :has_children] true)
        (update-in [:facets :children] #(merge-facets concept-type % individual-facets)))))

(defmethod common-qe/execute-query :complicated-facets
  [context query]
  ;; A query can only be a complicated facets query when it has never been executed as part of
  ;; an execution. We set :complicated-facets to false and add the facets fields to be returned
  ;; from aggregate to the query to facilitate the aggregation search and result generation.
  ;; We execute a base query with all the parameters to get the result and facets of fields that
  ;; are not in the query, then we merge this base result with only the facets for each individual
  ;; facet field that is in the query.
  (let [concept-type (:concept-type query)
        facet-fields-in-query (filter #(facet-condition-resolver/has-field? query %)
                                      (fv2rf/facets-v2-params concept-type))
        base-facet-fields (set/difference (set (fv2rf/facets-v2-params concept-type))
                                          (set facet-fields-in-query))
        query (assoc query :complicated-facets false :facet-fields base-facet-fields)
        base-result (common-qe/execute-query context query)
        facet-results (map #(get-facets-for-field context query %) facet-fields-in-query)
        merge-results (merge-search-result-facets concept-type base-result facet-results)]
    merge-results))
