(ns cmr.search.services.query-execution.facets.facets-v2-results-feature
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require [cmr.common-app.services.search.query-execution :as query-execution]
            [cmr.common-app.services.kms-fetcher :as kms-fetcher]
            [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
            [cmr.search.services.query-execution.facets.links-helper :as lh]
            [camel-snake-kebab.core :as csk]
            [cmr.common.util :as util]
            [ring.util.codec :as codec]
            [clojure.string :as str]
            [cmr.transmit.connection :as conn]))

(def UNLIMITED_TERMS_SIZE
  "The maximum number of results to return from any terms query"
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

(defn- parse-hierarchical-bucket-v2
  "Parses the elasticsearch aggregations response and generates version 2 facets."
  [parent-field field-hierarchy bucket-map base-link query-params]
  (when-let [field (first field-hierarchy)]
    (let [value-counts (for [bucket (get-in bucket-map [field :buckets])
                             :let [sub-facets (parse-hierarchical-bucket-v2
                                                parent-field
                                                (rest field-hierarchy)
                                                bucket
                                                base-link
                                                query-params)
                                   snake-parent-field (csk/->snake_case_string parent-field)
                                   snake-field (csk/->snake_case_string field)
                                   subfield-reg-ex (re-pattern (str snake-parent-field ".*"
                                                                    snake-field ".*"))
                                   relevant-query-params (filter (fn [[k v]] (re-matches subfield-reg-ex k)) query-params)
                                   some-term-applied? (some? (seq relevant-query-params))
                                   field-name (format "%s[0][%s]" snake-parent-field snake-field)
                                   [type links] (if some-term-applied?
                                                    (lh/create-hierarchical-links base-link query-params field-name (:key bucket))
                                                    [:apply (lh/create-hierarchical-apply-link base-link query-params
                                                                                               field-name (:key bucket))])]]
                         (merge sorted-facet-map
                                {:has_children false
                                 :applied false}
                                sub-facets
                                {:title (:key bucket)
                                 :applied (= type :remove)
                                 :links links
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
                                               (lh/create-links base-link query-params field-name term)
                                               [:apply (lh/create-apply-link base-link query-params
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

(defn- create-hierarchical-v2-facets
  "Helper function to generate the v2 facets for all hierarchical fields."
  [elastic-aggregations base-link query-params]
  (let [hierarchical-fields [:science-keywords]]
    (keep (fn [field]
              (when-let [sub-facets (hierarchical-bucket-map->facets-v2
                                      field (field elastic-aggregations)
                                      base-link query-params)]
                (let [field-reg-ex (re-pattern
                                    (str (csk/->snake_case_string field)
                                         ".*"))
                      applied? (some? (seq (filter (fn [[k v]]
                                                     (re-matches field-reg-ex k))
                                                   query-params)))]
                  (merge sorted-facet-map
                         (assoc sub-facets
                                :title (field fields->human-readable-label)
                                :applied applied?)))))
          hierarchical-fields)))

(defn- create-flat-v2-facets
  "Helper function to generate the v2 facets for all flat fields."
  [elastic-aggregations base-link query-params]
  (let [flat-fields [:platform :instrument :data-center :project :processing-level-id]]
    (flat-bucket-map->facets-v2
     (select-keys elastic-aggregations flat-fields)
     flat-fields
     base-link
     query-params)))

(defn create-v2-facets
  "Create the facets v2 response. Takes an elastic aggregations result and returns the facets."
  [context elastic-aggregations query]
  (let [search-public-conf (get-in context [:system :search-public-conf])
        base-link (format "%s/collections.json"
                          (conn/root-url
                            (assoc search-public-conf
                                   :context (:relative-root-url search-public-conf))))
        query-params (parse-params (:query-string context) "UTF-8")
        ;; TODO get rid of debug
        ; _ (println "Query params: " query-params)
        facets (concat (create-hierarchical-v2-facets elastic-aggregations base-link query-params)
                       (create-flat-v2-facets elastic-aggregations base-link query-params))]
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
