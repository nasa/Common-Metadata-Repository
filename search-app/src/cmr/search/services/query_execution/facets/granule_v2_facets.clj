(ns cmr.search.services.query-execution.facets.granule-v2-facets
  "Functions for generating v2 granule facets. Similar structure as v2 collection facets, but
  granule fields. First major use case is supporting OPeNDAP virutal directories capability."
  (:require
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as v2-facets]
   [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]))

(def granule-facet-params->elastic-fields
  "Maps the parameter names for the concept-type to the fields in Elasticsearch."
  {:start-date :start-date-doc-values
   :cycle :cycle})

(defmethod v2-facets/facets-v2-params->elastic-fields :granule
  [_]
  granule-facet-params->elastic-fields)

(def granule-facet-params
  "Granule facet params."
  (keys granule-facet-params->elastic-fields))

(def granule-facet-params-with-default-size
  "A map with collection facets parameters and the default term size values."
  (zipmap granule-facet-params
          (repeat (count granule-facet-params) v2-facets/DEFAULT_TERMS_SIZE)))

(defmethod v2-facets/facets-v2-params :granule
  [_]
  granule-facet-params)

(defmethod v2-facets/facets-v2-params-with-default-size :granule
  [_]
  granule-facet-params-with-default-size)

(def granule-fields->aggregation-fields
  "Defines the mapping of granule parameter names to the aggregation parameter names."
  (into {}
        (map (fn [field]
               [field (q2e/query-field->elastic-field field :granule)])
             granule-facet-params)))

(defmethod v2-facets/facet-fields->aggregation-fields :granule
  [_]
  granule-fields->aggregation-fields)

(defmethod v2-facets/v2-facets-root :granule
  [_]
  {:title "Browse Granules"
   :type :group})

(defmethod v2-facets/v2-facets-result-field-in-order :granule
  [_]
  ["Temporal"
   "Spatial"])

(defn single-collection-validation
  "Validates that the provided query is limited to a single collection. We do this to prevent
  expensive aggregation queries that would have to run against more than one granule index."
  [context]
  (let [collection-ids (:query-collection-ids context)
        collection-count (count collection-ids)]
    (when-not (= 1 collection-count)
      [(format "Granule V2 facets are limited to a single collection, but query matched %s collections."
               (if (zero? collection-count) "an undetermined number of" collection-count))])))

(def validations
  "Validation functions to run for v2 granule facets."
  (util/compose-validations [single-collection-validation]))

(defmethod v2-facets/facets-validator :granule
  [_]
  (util/build-validator :bad-request validations))

(def group-nodes-in-order-temporal
  "The titles of temporal facet group nodes in order."
  ["Year" "Month" "Day"])

(def group-nodes-in-order-spatial
  "The titles of spatial facet group nodes in order."
  ["Cycle" "Pass"])

(defn add-temporal-group-nodes-to-facets
  "Adds group nodes (Year, Month, Day) as applicable to the provided facets."
  [facets remaining-levels]
  (let [has-children (not= remaining-levels ["Day"])
        applied? (some? (some true? (map :applied facets)))
        children-facets (for [facet facets]
                          (if (seq (:children facet))
                            (assoc facet :children [(add-temporal-group-nodes-to-facets
                                                      (:children facet)
                                                      (rest remaining-levels))])
                            (assoc facet :has_children has-children)))]
    (v2h/generate-group-node (first remaining-levels) applied? children-facets)))

(defn add-spatial-group-nodes-to-facets
  "Adds group nodes (Cycle, Pass) as applicable to the provided facets."
  [facets remaining-levels]
  (let [has-children (not= remaining-levels ["Pass"])
        applied? (some? (some true? (map :applied facets)))
        children-facets (for [facet facets]
                          (if (seq (:children facet))
                            (assoc facet :children [(add-spatial-group-nodes-to-facets
                                                      (:children facet)
                                                      (rest remaining-levels))])
                            (assoc facet :has_children has-children)))]
    (v2h/generate-group-node (first remaining-levels) applied? children-facets)))


(defn- facet-query-applied?
  "Returns true if the query-params keys match a provided pattern."
  [query-params pattern]
  (let [regex (re-pattern pattern)]
    (->> query-params
         (filter (fn [[k]] (re-matches regex k)))
         seq
         some?)))

(defn- create-temporal-subfacets-map
  "Returns a facet for Temporal queries."
  [base-url query-params aggs]
  (let [subfacets (hv2/hierarchical-bucket-map->facets-v2
                    :temporal-facet
                    (:start-date-doc-values aggs)
                    base-url
                    query-params)]
    (when (seq subfacets)
      (let [applied? (facet-query-applied? query-params "temporal_facet.*")
            updated-subfacets (add-temporal-group-nodes-to-facets (:children subfacets)
                                                                  group-nodes-in-order-temporal)]
        (merge v2h/sorted-facet-map
               (v2h/generate-group-node "Temporal" applied?
                                        [updated-subfacets]))))))
(defn create-cycle-facet
  "Returns a filter facet node for a cycle."
  [base-url query-params applied? aggs]
  (v2h/generate-filter-node 
    base-url
    query-params
    "cycle"
    (str (int (:key aggs)))
    (:doc_count aggs)
    applied?) )

(defn- create-cycle-facets
  "Returns an array of cycle filter facets."
  [base-url query-params applied? buckets]
  (map (partial create-cycle-facet
                base-url
                query-params
                applied?)
       buckets))

(defn create-cycle-pass-facets
  "Creates a cycle filter that also contains pass filters"
  [base-url query-params applied? granule-cycle aggs]
  (let [formatted-children (map #(assoc % :key (str (int (:key %))))
                                (get-in aggs [:pass :buckets]))
        formatted-aggs (assoc-in aggs [:pass :buckets] formatted-children)
        children (hv2/hierarchical-bucket-map->facets-v2
                   :passes
                   formatted-aggs
                   base-url
                   query-params)]
    (when (seq children)
      [(assoc (merge v2h/sorted-facet-map
                     (v2h/generate-filter-node
                       base-url
                       query-params
                       "cycle"
                       granule-cycle
                       (get-in aggs [:aggs :doc_count])
                       applied?))
              :children
              [children])])))

(defn create-spatial-subfacets-map
  "Handle the case of cycle aggregation for V2 facets"
  [base-url query-params aggs]
  (let [applied? (facet-query-applied?
                   query-params "cycle.*")
        buckets (get-in aggs [:cycle :buckets] [])
        subfacets (if (seq buckets)
                    (create-cycle-facets
                      base-url
                      query-params
                      applied?
                      buckets)
                    (create-cycle-pass-facets
                      base-url
                      query-params
                      applied?
                      (get query-params "cycle[]")
                      (:cycle aggs)))
        applied? (facet-query-applied? query-params "cycle.*")
        cycle-root (v2h/generate-group-node "Cycle" applied? subfacets)]
    (when (seq subfacets)
      (merge v2h/sorted-facet-map
             (v2h/generate-group-node "Spatial" false [cycle-root])))))

(defmethod v2-facets/create-v2-facets-by-concept-type :granule
  [concept-type base-url query-params aggs _]
  (remove nil?
          (vector
            (create-temporal-subfacets-map base-url query-params aggs)
            (create-spatial-subfacets-map base-url query-params aggs))))

