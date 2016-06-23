(ns cmr.search.services.query-execution.facets.facets-v2-results-feature
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require [cmr.common-app.services.search.query-execution :as query-execution]
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

(defn- facets-v2-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request faceted results
  from a collection search. Size specifies the number of results to return. Only a subset of the
  facets are returned in the v2 facets, specifically those that help enable dataset discovery."
  [size]
  {:science-keywords (hv2/nested-facet :science-keywords size)
   :platform (v2h/terms-facet :platform-sn size)
   :instrument (v2h/terms-facet :instrument-sn size)
   :data-center (v2h/terms-facet :data-center size)
   :project (v2h/terms-facet :project-sn2 size)
   :processing-level-id (v2h/terms-facet :processing-level-id size)})

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
        missing-terms (remove #(some (set [(str/lower-case %)]) all-facet-values) search-terms)]
    (reduce #(conj %1 [%2 0]) value-counts missing-terms)))

(defn- create-flat-v2-facets
  "Parses the elastic aggregations and generates the v2 facets for all flat fields."
  [elastic-aggregations base-url query-params]
  (let [flat-fields [:platform :instrument :data-center :project :processing-level-id]]
    (remove nil?
      (for [field-name flat-fields
            :let [search-terms-from-query (lh/get-values-for-field query-params field-name)
                  value-counts (add-terms-with-zero-matching-collections
                                (frf/buckets->value-count-pairs (field-name elastic-aggregations))
                                search-terms-from-query)
                  snake-case-field (csk/->snake_case_string field-name)
                  applied? (some? (or (get query-params snake-case-field)
                                      (get query-params (str snake-case-field "[]"))))
                  children (map (v2h/generate-filter-node base-url query-params field-name applied?)
                                value-counts)]]
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

(defn create-v2-facets
  "Create the facets v2 response. Parses an elastic aggregations result and returns the facets."
  [context aggs]
  (let [base-url (collection-search-root-url context)
        query-params (parse-params (:query-string context) "UTF-8")
        facets (concat (hv2/create-hierarchical-v2-facets aggs base-url query-params)
                       (create-flat-v2-facets aggs base-url query-params))]
    (if (seq facets)
      (assoc v2-facets-root :has_children true :children facets)
      (assoc v2-facets-root :has_children false))))

(defmethod query-execution/pre-process-query-result-feature :facets-v2
  [_ query _]
  ;; With CMR-1101 we will support a parameter to specify the number of terms to return. For now
  ;; always use the DEFAULT_TERMS_SIZE
  (assoc query :aggregations (facets-v2-aggregations DEFAULT_TERMS_SIZE)))

(defmethod query-execution/post-process-query-result-feature :facets-v2
  [context _ {:keys [aggregations]} query-results _]
  (assoc query-results :facets (create-v2-facets context aggregations)))
