(ns cmr.search.services.query-execution.facets-results-feature
  "This is enables returning facets with collection search results"
  (:require [cmr.search.services.query-execution :as query-execution]
            [cmr.search.models.results :as r]
            [camel-snake-kebab.core :as csk]
            [clojure.data.xml :as x]))

(defn- terms-facet
  [field]
  ;; We shouldn't try to handle this many different values.
  ;; We should have a limit and if that's exceeded in the elastic response we should note that in the values returned.
  ;; This can be handled as a part of CMR-1101
  {:terms {:field field :size 10000}})

(def ^:private collection-count-aggregation
  "Used to build an aggregation to get a count of unique concepts included in the current nested
  aggregation."
  {:reverse_nested {}
   :aggs {:concept-id {:terms {:field :concept-id :size 1}}}})

(def ^:private science-keyword-hierarchy
  "List containing the elements within the science keyword hierarchy from top to bottom."
  [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3 :detailed-variable])

(defn- science-keyword-aggregations-helper
  "Build the science keyword aggregations query."
  [field-hierarchy]
  (when-let [field (first field-hierarchy)]
    (let [remaining-fields (rest field-hierarchy)
          next-field (first remaining-fields)
          terms {:field (str "science-keywords." (name field))}
          aggs (if next-field
                 {:coll-count collection-count-aggregation
                  next-field (science-keyword-aggregations-helper remaining-fields)}
                 {:coll-count collection-count-aggregation})]
      {:terms terms
       :aggs aggs})))

(def ^:private science-keyword-aggregations
  "Builds a nested aggregation query for science-keyword-aggregations in a hierarchical fashion"
  {:nested {:path :science-keywords}
   :aggs {:category (science-keyword-aggregations-helper science-keyword-hierarchy)}})

(def flat-facet-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request facetted results
  from a collection search."
  {:archive-center (terms-facet :archive-center)
   :project (terms-facet :project-sn)
   :platform (terms-facet :platform-sn)
   :instrument (terms-facet :instrument-sn)
   :sensor (terms-facet :sensor-sn)
   :two-d-coordinate-system-name (terms-facet :two-d-coord-name)
   :processing-level-id (terms-facet :processing-level-id)
   :category (terms-facet :category)
   :topic (terms-facet :topic)
   :term (terms-facet :term)
   :variable-level-1 (terms-facet :variable-level-1)
   :variable-level-2 (terms-facet :variable-level-2)
   :variable-level-3 (terms-facet :variable-level-3)
   :detailed-variable (terms-facet :detailed-variable)})

(def hierarchical-facet-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request facetted results
  from a collection search."
  {:archive-center (terms-facet :archive-center)
   :project (terms-facet :project-sn)
   :platform (terms-facet :platform-sn)
   :instrument (terms-facet :instrument-sn)
   :sensor (terms-facet :sensor-sn)
   :two-d-coordinate-system-name (terms-facet :two-d-coord-name)
   :processing-level-id (terms-facet :processing-level-id)
   :science-keywords science-keyword-aggregations})

(defn- facet-aggregations
  "Return facet aggregations to use depending on whether science keywords are flat or hierarchical"
  [nested-science-keywords?]
  (if nested-science-keywords? hierarchical-facet-aggregations flat-facet-aggregations))

(defmethod query-execution/pre-process-query-result-feature :facets
  [context query feature]
  (assoc query :aggregations (facet-aggregations (:nested-science-keywords? query))))

(defn- buckets->value-count-pairs
  "Processes an elasticsearch aggregation response of buckets to a sequence of value and count tuples"
  [bucket-map]
  (->> bucket-map
       :buckets
       (map (fn [{v :key n :doc_count}]
              [v n]))))

(defn- bucket-map->facets
  "Takes a map of elastic aggregation results containing keys to buckets and a list of the bucket
  names. Returns a facet map of those specific names with value count pairs"
  [bucket-map field-names]
  (for [field-name field-names]
    (r/map->Facet
      {:field (csk/->snake_case_string field-name)
       :value-counts (buckets->value-count-pairs (get bucket-map field-name))})))

(defn- science-keywords-bucket-helper
  "Helper to parse the elasticsearch aggregations response for science-keywords."
  [field-hierarchy aggregations-for-field]
  (when-let [field (first field-hierarchy)]
    (let [remaining-fields (rest field-hierarchy)
          next-field (first remaining-fields)
          buckets (get aggregations-for-field :buckets)
          response (r/map->HierarchicalFacet {:field field
                                              :value-count-maps []})]
      (->> (for [bucket buckets
                 :let [field-key (get bucket :key)
                       coll-count (get-in bucket [:coll-count :doc_count])
                       sub-aggregations-for-field (when next-field
                                                    (select-keys (get bucket next-field)
                                                                 [:coll-count :buckets]))]]
             (if (seq (get sub-aggregations-for-field :buckets))
               (r/map->ValueCountMaps
                 {:value field-key
                  :count coll-count
                  :sub-facets (science-keywords-bucket-helper remaining-fields
                                                              sub-aggregations-for-field)})
               (r/map->ValueCountMaps
                 {:value field-key
                  :count coll-count})))
           (into [])
           (assoc response :value-count-maps)))))

(defn- science-keywords-bucket->facets
  "Takes a map of elastic aggregation results for science keywords. Returns a hierarchical facet
  map of science keywords."
  [bucket-map]
  (science-keywords-bucket-helper science-keyword-hierarchy
                                  (get bucket-map (first science-keyword-hierarchy))))

(defmethod query-execution/post-process-query-result-feature :facets
  [context query elastic-results query-results feature]
  (let [aggs (:aggregations elastic-results)]
    (if (:nested-science-keywords? query)
      (let [science-keywords-bucket (:science-keywords aggs)
            science-keywords-facets (science-keywords-bucket->facets science-keywords-bucket)]
        (assoc query-results
               :facets (concat (bucket-map->facets
                                 (dissoc aggs :science-keywords)
                                 [:archive-center :project :platform :instrument :sensor
                                  :two-d-coordinate-system-name :processing-level-id])
                               [(r/map->ScienceKeywordsFacet {:field :science-keywords
                                                              :sub-facets science-keywords-facets})])))
      (let [facets (bucket-map->facets
                     aggs
                     ;; Specified here so that order will be consistent with results
                     [:archive-center :project :platform :instrument :sensor
                      :two-d-coordinate-system-name :processing-level-id :category :topic :term
                      :variable-level-1 :variable-level-2 :variable-level-3 :detailed-variable])]
        (assoc query-results :facets facets)))))

(defn key-with-prefix
  [prefix k]
  (if prefix
    (symbol (str prefix ":" (name k)))
    k))

(defn facet->xml-element
  [ns-prefix facet]
  (let [{:keys [field value-counts value-count-maps]} facet
        with-prefix (partial key-with-prefix ns-prefix)]
    (x/element (with-prefix :facet) {:field (name field)}
               (if value-count-maps
                 (do
                   (println "CDD : value-count-maps are:" value-count-maps)
                   (x/element (with-prefix :value) (:value value-count-maps))
                   (println "CDD: value")
                   (x/element (with-prefix :count) (:count value-count-maps))
                   (when-let [sub-facets (:facets value-count-maps)]
                     (println "*** CDD: sub-facets are:" sub-facets)
                     ; (cmr.common.dev.capture-reveal/capture sub-facets)
                     #_(x/element (key-with-prefix ns-prefix :facets) {} (map (partial facet->xml-element ns-prefix) sub-facets))))
                 (for [[value value-count] value-counts]
                   (x/element (with-prefix :value) {:count value-count} value))))))

(defn facets->xml-element
  "Helper function for converting a facet result into an XML element"
  ([facets]
   (facets->xml-element nil facets))
  ([ns-prefix facets]
   (when facets
     (x/element (key-with-prefix ns-prefix :facets) {}
                (map (partial facet->xml-element ns-prefix) facets)))))

(def cmr-facet-name->echo-facet-keyword
  "A mapping of CMR facet names to ECHO facet keywords"
  {"archive_center" :archive-center
   "project" :campaign-sn
   "platform" :platform-sn
   "instrument" :instrument-sn
   "sensor" :sensor-sn
   "two_d_coordinate_system_name" :twod-coord-name
   "processing_level_id" :processing-level
   "category" :category-keyword
   "topic" :topic-keyword
   "term" :term-keyword
   "variable_level_1" :variable-level-1-keyword
   "variable_level_2" :variable-level-2-keyword
   "variable_level_3" :variable-level-3-keyword
   "detailed_variable" :detailed-variable-keyword})

(defn facet->echo-xml-element
  [facet]
  (let [{:keys [field value-counts]} facet
        field-key (cmr-facet-name->echo-facet-keyword field)]
    (x/element field-key {:type "array"}
               (for [[value value-count] value-counts]
                 (x/element field-key {}
                            (x/element :term {} value)
                            (x/element :count {:type "integer"} value-count))))))

(defn facets->echo-xml-element
  "Helper function for converting facets into an XML element in echo format"
  [facets]
  (when facets
    (x/element :hash {}
               (map facet->echo-xml-element facets))))

(defn- value-count->echo-json
  "Returns value count in echo json format"
  [value-count]
  (let [[value value-count] value-count]
    {:count value-count
     :term value}))

(defn facet->echo-json
  [facet]
  (let [{:keys [field value-counts]} facet
        field-key (csk/->snake_case_string (cmr-facet-name->echo-facet-keyword field))]
    [field-key (map value-count->echo-json value-counts)]))

(defn facets->echo-json
  "Helper function for converting facets into an XML element in echo format"
  [facets]
  (when facets
    (into {} (map facet->echo-json facets))))

