(ns cmr.search.services.query-execution.facets-v2-results-feature
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require [cmr.common-app.services.search.query-execution :as query-execution]
            [cmr.common-app.services.kms-fetcher :as kms-fetcher]
            [cmr.search.services.query-execution.facets-results-feature :as frf]
            [camel-snake-kebab.core :as csk]))


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
  {:data-center (terms-facet :data-center size)
   :project (terms-facet :project-sn2 size)
   :platform (terms-facet :platform-sn size)
   :instrument (terms-facet :instrument-sn size)
   :processing-level-id (terms-facet :processing-level-id size)
   :science-keywords (nested-facet :science-keywords size)})

(def v2-facets-root
  "Root element for the facet response"
  {:title "Browse Collections"
   :type :group
   :has_children true})

(def fields->human-readable-label
  "Map of facet fields to their human readable label."
  {:data-center "Organizations"
   :project "Projects"
   :platform "Platforms"
   :instrument "Instruments"
   :processing-level-id "Processing levels"
   :science-keywords "Keywords"})

(defn- parse-hierarchical-bucket-v2
  "Parses the elasticsearch aggregations response for hierarchical fields."
  [field-hierarchy bucket-map]
  (when-let [field (first field-hierarchy)]
    (let [empty-response {:has_children false
                          :type :group}
          value-counts (for [bucket (get-in bucket-map [field :buckets])
                             :let [sub-facets (parse-hierarchical-bucket-v2 (rest field-hierarchy)
                                                                            bucket)]]
                         (merge {:has_children false}
                                (when-not (= sub-facets empty-response)
                                  sub-facets)
                                {:title (:key bucket)
                                 :count (get-in bucket [:coll-count :doc_count] (:doc_count bucket))
                                 :type :filter}))]
      (if (seq value-counts)
        (let [field-snake-case (csk/->snake_case_string field)]
          {:title field-snake-case
           :type :group
           :has_children true
           :children value-counts})
        empty-response))))

(defn hierarchical-bucket-map->facets-v2
  "Takes a map of elastic aggregation results for a nested field. Returns a hierarchical facet for
  that field."
  [field bucket-map]
  (parse-hierarchical-bucket-v2 (field kms-fetcher/nested-fields-mappings) bucket-map))

(defn flat-bucket-map->facets-v2
  "Takes a map of elastic aggregation results containing keys to buckets and a list of the bucket
  names. Returns a facet map of those specific names with value count pairs"
  [bucket-map field-names]
  (for [field-name field-names
        ; :let [value-counts (frf/buckets->value-count-pairs (get bucket-map field-name))]
        :let [value-counts (frf/buckets->value-count-pairs (field-name bucket-map))
              has-children (some? (seq value-counts))
              ; base-node {:title (get fields->human-readable-label (keyword field-name))}
              base-node {:title (field-name fields->human-readable-label)
                         :type :group
                         ;; TODO with another ticket
                         ;  :applied ;; either true or false
                         :has_children has-children}]]
    ; (println "The field name is ")
    (if has-children
      (assoc base-node :children (map (fn [[term count]]
                                        {:title term
                                         :type :filter
                                         ;; TODO with another ticket
                                         ;; applied ;; either true or false
                                         :count count
                                         ;; TODO with another ticket
                                         ; :links ;; Apply or remove the given term to the provided search
                                         :has_children false})
                                      value-counts))
      base-node)))

;; :value-counts [["Larc" 3] ["Dist" 1] ["GSFC" 1] ["Proc" 1]]
(defn create-v2-facets
  "Create the facets v2 response. Takes an elastic aggregations result and returns the facets."
  [elastic-aggregations]
  (println "Elastic aggregations response" elastic-aggregations)
  (let [flat-fields [:data-center :project :platform :instrument :processing-level-id]
        hierarchical-fields [:science-keywords]
        facets (concat (flat-bucket-map->facets-v2
                        (apply dissoc elastic-aggregations hierarchical-fields) flat-fields)
                       (map (fn [field]
                              (assoc (hierarchical-bucket-map->facets-v2
                                      field (field elastic-aggregations))
                                     :title (field fields->human-readable-label)))
                            hierarchical-fields))]
    (assoc v2-facets-root :children facets)))

; (defn create-v2-facets-orig
;   "Create the facets v2 response. Takes an elastic aggregations result and returns the facets."
;   [elastic-aggregations]
;   (println "Elastic aggregations response" elastic-aggregations)
;   (concat (frf/bucket-map->facets (apply dissoc elastic-aggregations [:science-keywords])
;                                   [:data-center :project :platform :instrument :processing-level-id])
;           (map (fn [field]
;                  (assoc (frf/hierarchical-bucket-map->facets field (field elastic-aggregations))
;                         :field (csk/->snake_case_string field)))
;                [:science-keywords])))

(defmethod query-execution/pre-process-query-result-feature :facets-v2
  [context query feature]
  (assoc query :aggregations (facets-v2-aggregations DEFAULT_TERMS_SIZE)))

(defmethod query-execution/post-process-query-result-feature :facets-v2
  [_ _ {:keys [aggregations]} query-results _]
  (assoc query-results :facets (create-v2-facets aggregations)))
