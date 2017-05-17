(ns cmr.search.services.query-execution.facets.facets-v2-results-feature
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require [cmr.common.util :as util]
            [cmr.common-app.services.search.query-execution :as query-execution]
            [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
            [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]
            [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
            [cmr.search.services.query-execution.facets.links-helper :as lh]
            [camel-snake-kebab.core :as csk]
            [ring.util.codec :as codec]
            [clojure.string :as str]
            [clojure.set :as set]
            [cmr.transmit.connection :as conn]))

(def UNLIMITED_TERMS_SIZE
  "The maximum number of allowed results to return from any terms query."
  10000)

(def DEFAULT_TERMS_SIZE
  "The default limit for the number of results to return from any terms query for v2 facets."
  50)

(def facets-v2-params->elastic-fields
  "Defines the mapping of the base search parameters for the v2 facets fields to its field names
   in elasticsearch."
  {:science-keywords :science-keywords.humanized
   :platform :platform-sn.humanized2
   :instrument :instrument-sn.humanized2
   :data-center :organization.humanized2
   :project :project-sn.humanized2
   :processing-level-id :processing-level-id.humanized2})

(def facets-v2-params
  "The base search parameters for the v2 facets fields."
  (keys facets-v2-params->elastic-fields))

(def facet-fields->aggregation-fields
  "Defines the mapping between facet fields to aggregation fields."
  (into {}
        (map (fn [field] [field (keyword (str (name field) "-h"))]) facets-v2-params)))

(def v2-facets-result-field-in-order
  "Defines the v2 facets result field in order"
  ["Keywords" "Platforms" "Instruments" "Organizations" "Projects" "Processing levels"])

(defn- facet-query
  "Returns the facet query for the given facet field"
  [facet-field size query-params]
  (if (= :science-keywords facet-field)
    (let [sk-depth (hv2/get-depth-for-hierarchical-field query-params :science-keywords-h)]
      (hv2/nested-facet (facets-v2-params->elastic-fields facet-field) size sk-depth))
    (v2h/prioritized-facet (facets-v2-params->elastic-fields facet-field) size)))

(defn- facets-v2-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request faceted results
  from a collection search. Size specifies the number of results to return. Only a subset of the
  facets are returned in the v2 facets, specifically those that help enable dataset discovery."
  [size query-params facet-fields]
  (into {}
        (for [field facet-fields]
          [(facet-fields->aggregation-fields field) (facet-query field size query-params)])))

(def v2-facets-root
  "Root element for the facet response"
  {:title "Browse Collections"
   :type :group})

(defn- add-terms-with-zero-matching-collections
  "Takes a sequence of tuples and a sequence of search terms. The tuples are of the form search term
  and number of matching collections. For any search term provided that is not in the tuple of value
  counts, a new tuple is added with the search term and a count of 0."
  [value-counts search-terms]
  (let [all-facet-values (map #(str/lower-case (first %)) value-counts)
        ;; Look for each of the search terms in the returned facet values compared in a case
        ;; insensitive manner. Although the comparison is case insensitive the missing-terms will
        ;; contain any of the search terms that do not appear in the facet values in their
        ;; original case.
        missing-terms (remove #(some (set [(str/lower-case %)]) all-facet-values) search-terms)]
    (reduce #(conj %1 [%2 0]) value-counts missing-terms)))

(defn- create-prioritized-v2-facets
  "Parses the elastic aggregations and generates the v2 facets for all flat fields."
  [elastic-aggregations facet-fields base-url query-params]
  (let [flat-fields (map facet-fields->aggregation-fields facet-fields)]
    (remove nil?
      (for [field-name flat-fields
            :let [search-terms-from-query (lh/get-values-for-field query-params field-name)
                  value-counts (add-terms-with-zero-matching-collections
                                (frf/buckets->value-count-pairs (get-in elastic-aggregations [field-name :values]))
                                search-terms-from-query)
                  snake-case-field (csk/->snake_case_string field-name)
                  applied? (some? (or (get query-params snake-case-field)
                                      (get query-params (str snake-case-field "[]"))))
                  children (map (v2h/generate-filter-node base-url query-params field-name applied?)
                                (sort-by first util/compare-natural-strings value-counts))]]
        (when (seq children)
          (v2h/generate-group-node (field-name v2h/fields->human-readable-label) applied?
                                   children))))))

(defn- parse-params
  "Parse parameters from a query string into a map. Taken directly from ring code."
  [params encoding]
  (let [params (codec/form-decode params encoding)]
    (if (map? params) params {})))

(defn- collection-search-root-url
  "The root URL for executing a collection search against the CMR."
  [context]
  (let [public-search-config (set/rename-keys (get-in context [:system :public-conf])
                                              {:relative-root-url :context})]
    (format "%s/collections.json" (conn/root-url public-search-config))))

(defn- create-v2-facets
  "Create the facets v2 response. Parses an elastic aggregations result and returns the facets."
  [context aggs facet-fields]
  (let [base-url (collection-search-root-url context)
        query-params (parse-params (:query-string context) "UTF-8")
        flat-facet-fields (remove #{:science-keywords} facet-fields)
        hierarchical-facets (when ((set facet-fields) :science-keywords)
                              (hv2/create-hierarchical-v2-facets aggs base-url query-params))
        facets (concat hierarchical-facets
                       (create-prioritized-v2-facets aggs flat-facet-fields base-url query-params))]
    (if (seq facets)
      (assoc v2-facets-root :has_children true :children facets)
      (assoc v2-facets-root :has_children false))))

(defmethod query-execution/pre-process-query-result-feature :facets-v2
  [{:keys [query-string]} query _]
  (let [query-params (parse-params query-string "UTF-8")
        facet-fields (:facet-fields query)
        facet-fields (if facet-fields facet-fields facets-v2-params)]
    ;; With CMR-1101 we will support a parameter to specify the number of terms to return. For now
    ;; always use the DEFAULT_TERMS_SIZE
    (assoc query :aggregations
           (facets-v2-aggregations DEFAULT_TERMS_SIZE query-params facet-fields))))

(defmethod query-execution/post-process-query-result-feature :facets-v2
  [context {:keys [facet-fields]} {:keys [aggregations]} query-results _]
  (let [facet-fields (if facet-fields facet-fields facets-v2-params)]
    (assoc query-results :facets (create-v2-facets context aggregations facet-fields))))
