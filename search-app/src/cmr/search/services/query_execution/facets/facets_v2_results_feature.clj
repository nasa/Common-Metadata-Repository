(ns cmr.search.services.query-execution.facets.facets-v2-results-feature
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
   [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
   [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]
   [cmr.search.services.query-execution.facets.links-helper :as lh]
   [cmr.search.services.query-execution.facets.cycle-facets :as cycle-facets]
   [cmr.search.services.query-execution.facets.temporal-facets :as temporal-facets]
   [cmr.transmit.connection :as conn]
   [ring.util.codec :as codec]))

(def UNLIMITED_TERMS_SIZE
  "The maximum number of allowed results to return from any terms query."
  10000)

(def DEFAULT_TERMS_SIZE
  "The default limit for the number of results to return from any terms query for v2 facets."
  50)

(defmulti facets-v2-params->elastic-fields
  "Maps the parameter names for the concept-type to the fields in Elasticsearch."
  (fn [concept-type]
    concept-type))

(defmulti facets-v2-params
  "Facets query params by concept-type"
  (fn [concept-type]
    concept-type))

(defmulti facets-v2-params-with-default-size
  "Returns a map of facets query params with DEFAULT_TERMS_SIZE value by concept-type"
  (fn [concept-type]
    concept-type))

(defmulti facet-fields->aggregation-fields
  "Defines the mapping between facet fields to aggregation fields."
  (fn [concept-type]
    concept-type))

(defmulti v2-facets-result-field-in-order
  "Defines the v2 facets result field in order by concept-type"
  (fn [concept-type]
    concept-type))

(defn- facet-query
  "Returns the facet query for the given facet field"
  [context concept-type facet-field size query-params]
  (case facet-field
    (:platforms :science-keywords :variables)
    (let [hierarchical-field (keyword (str (name facet-field) "-h"))
          depth (hv2/get-depth-for-hierarchical-field query-params hierarchical-field)]
      (hv2/nested-facet (get (facets-v2-params->elastic-fields concept-type) facet-field) size depth))

    :start-date
    (temporal-facets/temporal-facet query-params)

    :two-d-coordinate-system-name
    {:terms {:field (keyword (name (get (facets-v2-params->elastic-fields concept-type) facet-field)))
             :size size}}

    :cycle
    (cycle-facets/cycle-facet query-params)

    :horizontal-data-resolution-range
    (v2h/prioritized-range-facet context (get (facets-v2-params->elastic-fields concept-type) facet-field))

    :latency
     (v2h/terms-facet (get (facets-v2-params->elastic-fields concept-type) facet-field) size)

    ;; else
    (v2h/prioritized-facet (get (facets-v2-params->elastic-fields concept-type) facet-field) size)))

(defn- facets-v2-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request faceted results
  from a collection search. values in facet-fields-map specifies the number of results to return for the facet.
  Only a subset of the facets are returned in the v2 facets, specifically those that help enable dataset discovery."
  [context concept-type query-params facet-fields-map]
  (into {}
        (for [field (keys facet-fields-map)]
          [(get (facet-fields->aggregation-fields concept-type) field)
           (facet-query context concept-type field (field facet-fields-map) query-params)])))

(defn add-terms-with-zero-matching-collections
  "Takes a sequence of tuples and a sequence of search terms. The tuples are of the form search term
  and number of matching collections. For any search term provided that is not in the tuple of value
  counts, a new tuple is added with the search term and a count of 0."
  [value-counts search-terms]
  (let [all-facet-values (map #(string/lower-case (first %)) value-counts)
        ;; Look for each of the search terms in the returned facet values compared in a case
        ;; insensitive manner. Although the comparison is case insensitive the missing-terms will
        ;; contain any of the search terms that do not appear in the facet values in their
        ;; original case.
        missing-terms (remove #(some (set [(string/lower-case %)]) all-facet-values) search-terms)]
    (reduce #(conj %1 [%2 0]) value-counts missing-terms)))

(defn count-value-pairs
  "Processes an elasticsearch aggregation response of buckets to a sequence of value and count
   tuples. For the horizontal-data-resolution-range parameter, if all buckets contain 0 items then
   pass back an empty sequence. We want the range facets to behave the same as the other facets
   when the search result contains 0 records in the range facets."
  [field-name aggregations]
  (if (= field-name :horizontal-data-resolution-range)
    (let [aggs (get-in aggregations [field-name :values])
          some-values? (->> aggs
                            :buckets
                            (map :doc_count)
                            (some #(< 0 %)))]
      (if some-values?
        ;; Removes value pairs where the doc count is zero to be more in line with other facets
        (filter #(< 0 (get % 1))
         (frf/buckets->value-count-pairs
          (get-in aggregations [field-name :values])))
        (sequence nil)))
    (frf/buckets->value-count-pairs
     (get-in aggregations [field-name :values]))))

(defn create-prioritized-v2-facets
  "Parses the elastic aggregations and generates the v2 facets for all flat fields."
  ([concept-type elastic-aggregations facet-fields base-url query-params]
   (create-prioritized-v2-facets concept-type elastic-aggregations facet-fields base-url query-params true))
  ([concept-type elastic-aggregations facet-fields base-url query-params sort?]
   (let [flat-fields (remove nil? (map #(get (facet-fields->aggregation-fields concept-type) %) facet-fields))]
     (remove nil?
             (for [field-name flat-fields
                   :let [search-terms-from-query (lh/get-values-for-field query-params field-name)
                         value-counts (add-terms-with-zero-matching-collections
                                        (count-value-pairs field-name elastic-aggregations)
                                        search-terms-from-query)
                         snake-case-field (csk/->snake_case_string field-name)
                         applied? (some? (or (get query-params snake-case-field)
                                             (get query-params (str snake-case-field "[]"))))
                         children (map (v2h/filter-node-generator base-url query-params field-name applied?)
                                       (if sort?
                                         (sort-by first util/compare-natural-strings value-counts)
                                         value-counts))]]
               (when (seq children)
                 (v2h/generate-group-node (field-name v2h/fields->human-readable-label) applied?
                                          children)))))))

(defn- parse-params
  "Parse parameters from a query string into a map. Taken directly from ring code."
  [params encoding]
  (let [params (codec/form-decode params encoding)]
    (if (map? params) params {})))

(defn- get-base-url
  "Returns the base-url to use in facet links."
  [context concept-type]
  (let [public-search-config (set/rename-keys (get-in context [:system :public-conf])
                                              {:relative-root-url :context})]
    (format "%s/%ss.json"
            (conn/root-url public-search-config)
            (name concept-type))))

(defmulti v2-facets-root
  "V2 facets root for each concept-type."
  (fn [concept-type]
    concept-type))

(defmulti create-v2-facets-by-concept-type
  "Mapping of concept type to the function used to create the v2 facets for that concept type."
  (fn [concept-type base-url query-params aggs facet-fields]
    concept-type))

(defn- create-v2-facets
  "Create the facets v2 response. Parses an elastic aggregations result and returns the facets."
  [context concept-type aggs facet-fields]
  (let [base-url (get-base-url context concept-type)
        query-params (parse-params (:query-string context) "UTF-8")
        v2-facets (create-v2-facets-by-concept-type concept-type
                                                    base-url query-params aggs facet-fields)]
    (if (seq v2-facets)
      (assoc (v2-facets-root concept-type) :has_children true :children v2-facets)
      (assoc (v2-facets-root concept-type) :has_children false))))

(defmulti facets-validator
  "Mapping of concept type to the validator to run for that concept type."
  (fn [concept-type]
    concept-type))

;; Do not perform any validations by default
(defmethod facets-validator :default
  [_]
  nil)

(defn- get-facet-fields-map
  "Returns a map with the keys being the keys in facet-fields-list
  and the values being the related facet size in the facets-size map.
  If the value is not present in facets-size-map, use the default value.
  Note: facets-v2-params-with-default-size contains all the keys in facet-fields-list."
  [concept-type facet-fields-list facets-size-map]
  (select-keys (merge (facets-v2-params-with-default-size concept-type) facets-size-map)
               facet-fields-list))

(defmethod query-execution/pre-process-query-result-feature :facets-v2
  [context query _]
  (let [query-string (:query-string context)
        concept-type (:concept-type query)
        facet-fields (:facet-fields query)
        facet-fields (or facet-fields (facets-v2-params concept-type))
        facets-size (:facets-size query)
        facet-fields-map (get-facet-fields-map concept-type facet-fields facets-size)
        query-params (parse-params query-string "UTF-8")]
    (when-let [validator (facets-validator concept-type)]
      (validator context))
    (let [aggs-query (facets-v2-aggregations context concept-type query-params facet-fields-map)]
      (assoc query :aggregations aggs-query))))

(defmethod query-execution/post-process-query-result-feature :facets-v2
  [context query elastic-results query-results _]
  (let [concept-type (:concept-type query)
        facet-fields (:facet-fields query)
        facet-fields (or facet-fields (facets-v2-params concept-type))
        aggregations (:aggregations elastic-results)]
    (assoc query-results :facets (create-v2-facets context concept-type aggregations facet-fields))))
