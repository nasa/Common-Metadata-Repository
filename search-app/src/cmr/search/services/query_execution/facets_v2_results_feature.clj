(ns cmr.search.services.query-execution.facets-v2-results-feature
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require [cmr.common-app.services.search.query-execution :as query-execution]
            [cmr.common-app.services.kms-fetcher :as kms-fetcher]
            [cmr.search.services.query-execution.facets-results-feature :as frf]
            [camel-snake-kebab.core :as csk]
            [cmr.common.util :as util]
            [ring.util.codec :as codec]
            [clojure.string :as str]))


(def UNLIMITED_TERMS_SIZE
  "The maximum number of results to return from any terms query"
  ;; FIXME: We shouldn't try to handle this many different values.
  ;; We should have a limit and if that's exceeded in the elastic response we should note that in
  ;; the values returned. This can be handled as a part of CMR-1101.
  10000)

(def DEFAULT_TERMS_SIZE
  "The maximum number of results to return from any terms query"
  50)

(defn- terms-facet
  "Construct a terms query to be applied for the given field. Size specifies the number of results
  to return."
  [field size]
  {:terms {:field field :size size}})

(defn- hierarchical-aggregation-builder
  "Build an aggregations query for the given hierarchical field."
  [field field-hierarchy size]
  (when-let [subfield (first field-hierarchy)]
    {subfield {:terms {:field (str (name field) "." (name subfield))
                       :size size}
               :aggs (merge {:coll-count frf/collection-count-aggregation}
                            (hierarchical-aggregation-builder field (rest field-hierarchy) size))}}))

(defn- nested-facet
  "Returns the nested aggregation query for the given hierarchical field. Size specifies the number
  of results to return."
  [field size]
  {:nested {:path field}
   :aggs (hierarchical-aggregation-builder field
                                           (remove #{:url} (field kms-fetcher/nested-fields-mappings))
                                           size)})

(defn- facets-v2-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request faceted results
  from a collection search. Size specifies the number of results to return. Only a subset of the
  facets are returned in the v2 facets, specifically those that help enable dataset discovery."
  [size]
  {:science-keywords (nested-facet :science-keywords size)
   :platform (terms-facet :platform-sn size)
   :instrument (terms-facet :instrument-sn size)
   :data-center (terms-facet :data-center size)
   :project (terms-facet :project-sn2 size)
   :processing-level-id (terms-facet :processing-level-id size)})

(def v2-facets-root
  "Root element for the facet response"
  {:title "Browse Collections"
   :type :group})

(def fields->human-readable-label
  "Map of facet fields to their human readable label."
  {:data-center "Organizations"
   :project "Projects"
   :platform "Platforms"
   :instrument "Instruments"
   :processing-level-id "Processing levels"
   :science-keywords "Keywords"})

(def sorted-facet-map
  "A map that sorts the keys of the facet map so it is presented in a pleasing way to Users
  of the API. The nested hierarchical maps API can be hard to understand if the maps are ordered
  randomly."
  (util/key-sorted-map [:title :type :applied :count :links :has_children :children]))

(defn- generate-query-string
  "Creates a query string from a root URL and a map of query params"
  [base-link query-params]
  (format "%s?%s" base-link (codec/form-encode query-params)))

(defn- create-apply-link
  "Create a link to apply a term."
  [base-link query-params field-name term]
  (let [param-name (str (csk/->snake_case_string field-name) "[]")
        existing-values (flatten [(get query-params param-name)])
        updated-query-params (assoc query-params param-name (keep identity (conj existing-values term)))]
    {:apply (generate-query-string base-link updated-query-params)}))

(defn- create-remove-link
  "Create a link to remove a term from a query."
  [base-link query-params field-name term]
  (let [param-name (str (csk/->snake_case_string field-name) "[]")
        existing-values (get query-params param-name)
        updated-query-params (if (coll? existing-values)
                                 (update query-params param-name
                                         (fn [_]
                                           (remove (fn [value]
                                                     (= (str/lower-case term) value))
                                                   (map str/lower-case existing-values))))
                                 (dissoc query-params param-name))]
    {:remove (generate-query-string base-link updated-query-params)}))

(defn- create-links
  "Creates either a remove or an apply link based on whether this particular term is already
  selected within a query. Returns a tuple of the type of link created and the link itself."
  [base-link query-params field-name term]
  (println "Checking for field" field-name)
  (let [terms-for-field (get query-params (str (csk/->snake_case_string field-name) "[]"))
        _ (when terms-for-field (println "The terms are" terms-for-field))
        term-exists (some #{(str/lower-case term)} (if (coll? terms-for-field)
                                                     (map str/lower-case terms-for-field)
                                                     [(str/lower-case terms-for-field)]))]
    (if term-exists
      [:remove (create-remove-link base-link query-params field-name term)]
      [:apply (create-apply-link base-link query-params field-name term)])))

(defn- parse-hierarchical-bucket-v2
  "Parses the elasticsearch aggregations response to generate version 2 facets."
  [parent-field field-hierarchy bucket-map base-link query-params]
  (when-let [field (first field-hierarchy)]
    (let [value-counts (for [bucket (get-in bucket-map [field :buckets])
                             :let [sub-facets (parse-hierarchical-bucket-v2
                                                parent-field
                                                (rest field-hierarchy)
                                                bucket
                                                base-link
                                                query-params)]]
                         (merge sorted-facet-map
                                {:has_children false}
                                sub-facets
                                {:title (:key bucket)
                                 :count (get-in bucket [:coll-count :doc_count] (:doc_count bucket))
                                 :type :filter}))]
      (when (seq value-counts)
        {:title (csk/->snake_case_string field)
         :type :group
         :has_children true
         :children value-counts}))))

(defn hierarchical-bucket-map->facets-v2
  "Takes a map of elastic aggregation results for a nested field. Returns a hierarchical facet for
  that field."
  [field bucket-map base-link query-params]
  (parse-hierarchical-bucket-v2 field (field kms-fetcher/nested-fields-mappings) bucket-map
                                base-link query-params))

(defn flat-bucket-map->facets-v2
  "Takes a map of elastic aggregation results containing keys to buckets and a list of the bucket
  names. Returns a facet map of those specific names with value count pairs"
  [bucket-map field-names base-link query-params]
  (remove nil?
    (for [field-name field-names
          :let [value-counts (frf/buckets->value-count-pairs (field-name bucket-map))
                has-children (some? (seq value-counts))
                some-term-applied? (some? (get query-params
                                               (str (csk/->snake_case_string field-name) "[]")))]]
      (when has-children
        {:title (field-name fields->human-readable-label)
         :type :group
         :applied some-term-applied?
         :has_children true
         :children (map (fn [[term count]]
                          (let [[type links] (if some-term-applied?
                                               (create-links base-link query-params field-name term)
                                               [:apply (create-apply-link base-link query-params
                                                                          field-name term)])]
                           (merge sorted-facet-map
                                  {:title term
                                   :type :filter
                                   :applied (= type :remove)
                                   :links links
                                   :count count
                                   :has_children false})))
                        value-counts)}))))

(comment
 (proto/saved-values)
 (proto/clear-saved-values!))

(defn- parse-params
  "Parse parameters from a query string into a map. Taken directly from ring code."
  [params encoding]
  (let [params (codec/form-decode params encoding)]
    (if (map? params) params {})))

(defn create-v2-facets
  "Create the facets v2 response. Takes an elastic aggregations result and returns the facets."
  [context elastic-aggregations query]
  (let [flat-fields [:platform :instrument :data-center :project :processing-level-id]
        hierarchical-fields [:science-keywords]
        base-link "http://localhost:3003/collections.json"
        query-params (parse-params (:query-string context) "UTF-8")
        facets (concat (keep (fn [field]
                                (when-let [sub-facets (hierarchical-bucket-map->facets-v2
                                                        field (field elastic-aggregations)
                                                        base-link query-params)]
                                  (assoc sub-facets :title (field fields->human-readable-label))))
                             hierarchical-fields)
                       (flat-bucket-map->facets-v2
                        (apply dissoc elastic-aggregations hierarchical-fields)
                        flat-fields
                        base-link
                        query-params))]
    (if (seq facets)
      (assoc v2-facets-root :has_children true :children facets)
      (assoc v2-facets-root :has_children false))))

(defmethod query-execution/pre-process-query-result-feature :facets-v2
  [_ query _]
  ;; TODO Add in ticket number mentioning we will support a parameter specifying a number of terms
  (assoc query :aggregations (facets-v2-aggregations DEFAULT_TERMS_SIZE)))

(defmethod query-execution/post-process-query-result-feature :facets-v2
  [context query {:keys [aggregations]} query-results _]
  (assoc query-results :facets (create-v2-facets context aggregations query)))
