(ns cmr.search.services.query-execution.facets.facets-results-feature
  "This enables returning facets with collection search results"
  (:require [cmr.common-app.services.search.query-execution :as query-execution]
            [cmr.common-app.services.kms-fetcher :as kms-fetcher]
            [cmr.common-app.services.search.results-model :as r]
            [camel-snake-kebab.core :as csk]
            [clojure.data.xml :as x]))

(def UNLIMITED_TERMS_SIZE
  "The maximum number of results to return from any terms query"
  ;; FIXME: We shouldn't try to handle this many different values.
  ;; We should have a limit and if that's exceeded in the elastic response we should note that in
  ;; the values returned. This can be handled as a part of CMR-1101.
  10000)

(defn- terms-facet
  [field]
  {:terms {:field field :size UNLIMITED_TERMS_SIZE}})

(def collection-count-aggregation
  "Used to build an aggregation to get a count of unique concepts included in the current nested
  aggregation."
  {:reverse_nested {}
   :aggs {:concept-id {:terms {:field :concept-id :size 1}}}})

(def hierarchical-facet-order
  "Order in which hierarchical facets are returned in the facet response."
  [:data-centers :archive-centers :platforms :instruments :science-keywords :location-keywords])

(defn- hierarchical-aggregation-builder
  "Build an aggregations query for the given hierarchical field."
  [field field-hierarchy]
  (when-let [subfield (first field-hierarchy)]
    {subfield {:terms {:field (str (name field) "." (name subfield))
                       :size UNLIMITED_TERMS_SIZE}
               :aggs (merge {:coll-count collection-count-aggregation}
                            (hierarchical-aggregation-builder field (rest field-hierarchy)))}}))

(defn- nested-facet
  "Returns the nested aggregation query for the given hierarchical field."
  [field]
  ;; Removing field :url because it's necessary in keyword search but not in facets.
  {:nested {:path field}
   :aggs (hierarchical-aggregation-builder field (remove #{:url} (field kms-fetcher/nested-fields-mappings)))})

(def ^:private flat-facet-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request faceted results
  from a collection search."
  {:data-center (terms-facet :data-center)
   :archive-center (terms-facet :archive-center)
   :project (terms-facet :project-sn2)
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

(def ^:private hierarchical-facet-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request faceted results
  from a collection search."
  {:data-centers (nested-facet :data-centers)
   :archive-centers (nested-facet :archive-centers)
   :project (terms-facet :project-sn2)
   :platforms (nested-facet :platforms)
   :instruments (nested-facet :instruments)
   :sensor (terms-facet :sensor-sn)
   :two-d-coordinate-system-name (terms-facet :two-d-coord-name)
   :processing-level-id (terms-facet :processing-level-id)
   :location-keywords (nested-facet :location-keywords)
   :science-keywords (nested-facet :science-keywords)
   ;; Detailed variable is technically part of the science keyword hierarchy directly below
   ;; variable level 1 (at the same level as variable level 2.)
   ;; Opened ticket CMR-1722 to address.
   :detailed-variable (terms-facet :detailed-variable)})

(defmethod query-execution/pre-process-query-result-feature :facets
  [context query feature]
  (assoc query :aggregations flat-facet-aggregations))

(defmethod query-execution/pre-process-query-result-feature :hierarchical-facets
  [context query feature]
  (assoc query :aggregations hierarchical-facet-aggregations))

(defn buckets->value-count-pairs
  "Processes an elasticsearch aggregation response of buckets to a sequence of value and count
  tuples"
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

(defn- parse-hierarchical-bucket
  "Parses the elasticsearch aggregations response for hierarchical fields."
  [field-hierarchy bucket-map]
  (when-let [field (first field-hierarchy)]
    (let [empty-response {:subfields []}
          value-counts (for [bucket (get-in bucket-map [field :buckets])
                             :let [sub-facets (parse-hierarchical-bucket (rest field-hierarchy)
                                                                         bucket)]]
                         (merge (when-not (= sub-facets empty-response)
                                  sub-facets)
                                {:count (get-in bucket [:coll-count :doc_count] (:doc_count bucket))
                                 :value (:key bucket)}))]
      (if (seq value-counts)
        (let [field-snake-case (csk/->snake_case_string field)]
          {:subfields [field-snake-case]
           field-snake-case value-counts})
        empty-response))))

(defn- hierarchical-bucket-map->facets
  "Takes a map of elastic aggregation results for a nested field. Returns a hierarchical facet for
  that field."
  [field bucket-map]
  (parse-hierarchical-bucket (field kms-fetcher/nested-fields-mappings) bucket-map))

(defn- create-hierarchical-facets
  "Create the facets response with hierarchical facets. Takes an elastic aggregations result and
  returns the facets."
  [elastic-aggregations]
  (concat (bucket-map->facets (apply dissoc elastic-aggregations hierarchical-facet-order)
                              [:project :sensor :two-d-coordinate-system-name
                               :processing-level-id :detailed-variable])
          (map (fn [field]
                 (assoc (hierarchical-bucket-map->facets field (field elastic-aggregations))
                        :field (csk/->snake_case_string field))) hierarchical-facet-order)))

(defn- create-flat-facets
  "Create the facets response with flat facets. Takes an elastic aggregations result and returns
  the facets."
  [elastic-aggregations]
  (bucket-map->facets
    elastic-aggregations
    ;; Specified here so that order will be consistent with results
    [:data-center :archive-center :project :platform :instrument :sensor
     :two-d-coordinate-system-name :processing-level-id :category :topic :term
     :variable-level-1 :variable-level-2 :variable-level-3 :detailed-variable]))

(defmethod query-execution/post-process-query-result-feature :facets
  [_ _ {:keys [aggregations]} query-results _]
  (assoc query-results :facets (create-flat-facets aggregations)))

(defmethod query-execution/post-process-query-result-feature :hierarchical-facets
  [_ _ {:keys [aggregations]} query-results _]
  (assoc query-results :facets (create-hierarchical-facets aggregations)))

(defn- key-with-prefix
  [prefix k]
  (if prefix
    (symbol (str prefix ":" (name k)))
    k))

(defn- value-count-maps->xml-element
  "Converts a list of value-count-maps into an XML element."
  [ns-prefix value-count-maps]
  (let [with-prefix (partial key-with-prefix ns-prefix)]
    (x/element (with-prefix :value-count-maps) {}
               (for [value-count-map value-count-maps]
                 (if-let [subfield-name (first (:subfields value-count-map))]
                   (when-let [subfield-facets (get value-count-map subfield-name)]
                     (let [child-facets (x/element (with-prefix :facet)
                                                   {:field (name subfield-name)}
                                                   (value-count-maps->xml-element ns-prefix
                                                                                  subfield-facets))]
                       (x/element (with-prefix :value-count-map) {}
                                  [(x/element (with-prefix :value)
                                              {:count (:count value-count-map)}
                                              (:value value-count-map))
                                   child-facets])))
                   (x/element (with-prefix :value-count-map) {}
                              (x/element (with-prefix :value)
                                         {:count (:count value-count-map)}
                                         (:value value-count-map))))))))

(defn- facet->xml-element
  "Helper function for converting a facet result into an XML element"
  [ns-prefix {:keys [field value-counts subfields] :as facet}]
  (let [with-prefix (partial key-with-prefix ns-prefix)
        xml-element-content
        (if-let [subfield (first subfields)]
          (x/element (with-prefix :facet)
                     {:field (name subfield)}
                     (value-count-maps->xml-element ns-prefix (get facet subfield)))
          (for [[value value-count] value-counts]
            (x/element (with-prefix :value) {:count value-count} value)))]
    (x/element (with-prefix :facet) {:field (name field)} xml-element-content)))

(defn facets->xml-element
  "Converts a facets result into an XML element"
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

(defn- facet->echo-xml-element
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
               (map facet->echo-xml-element
                    ;; Only include facets which are present in ECHO
                    (filter #(cmr-facet-name->echo-facet-keyword (:field %))
                            facets)))))
(defn- value-count->echo-json
  "Returns value count in echo json format"
  [value-count]
  (let [[value value-count] value-count]
    {:count value-count
     :term value}))

(defn- facet->echo-json
  [facet]
  (let [{:keys [field value-counts]} facet
        field-key (csk/->snake_case_string (cmr-facet-name->echo-facet-keyword field))]
    [field-key (map value-count->echo-json value-counts)]))

(defn facets->echo-json
  "Helper function for converting facets into an XML element in echo format"
  [facets]
  (when facets
    (into {} (map facet->echo-json
                  ;; Only include facets which are present in ECHO
                  (filter #(cmr-facet-name->echo-facet-keyword (:field %))
                          facets)))))

(comment)

  ;; See below for snippets of the data structures used for building hierarchical facets
  ;; 1) The elastic aggregations query for hierarchical facets

  ;; GET 1_collections_v2/collection/_search
  ; {
  ; "query": {
  ;   "match_all": {}
  ; },
  ; "size": 0,
  ; "aggs": {
  ;   "science_keywords": {
  ;     "nested": {
  ;       "path": "science-keywords"
  ;     },
  ;     "aggs": {
  ;       "category": {
  ;         "terms": {
  ;           "field": "science-keywords.category"
  ;         },
  ;         "aggs": {
  ;           "category-coll-count": {
  ;             "reverse_nested": {},
  ;             "aggs": {
  ;               "concept-id": {
  ;                 "terms": {
  ;                   "field": "concept-id",
  ;                   "size": 1
  ;                 }
  ;               }
  ;             }
  ;           },
  ;           "topic": {
  ;             ;; ... same structure as category
  ;             ;; repeats for each term in the science keyword hierarchy

  ;; Snippet of the elastic response for hierarchical facets
  ;  "aggregations": {
  ;     "science_keywords": {
  ;        "doc_count": 14,
  ;        "category": {
  ;           "doc_count_error_upper_bound": 0,
  ;           "sum_other_doc_count": 0,
  ;           "buckets": [
  ;              {
  ;                 "key": "Hurricane",
  ;                 "doc_count": 6,
  ;                 "category-coll-count": {
  ;                    "doc_count": 2,
  ;                    "concept-id": {
  ;                       "doc_count_error_upper_bound": 0,
  ;                       "sum_other_doc_count": 1,
  ;                       "buckets": [
  ;                          {
  ;                             "key": "C1200000000-PROV1",
  ;                             "doc_count": 1
  ;                          }
  ;                       ]
  ;                    }
  ;                 },
  ;             "topic": {
  ;                ...

  ;; Hierarchical facets json response
  ;  }, {
  ;     "field" : "detailed_variable",
  ;     "value-counts" : [ [ "Detail1", 2 ], [ "UNIVERSAL", 2 ] ]
  ;   }, {
  ;     "field" : "science_keywords",
  ;     "subfields" : [ "category" ],
  ;     "category" : [ {
  ;       "value" : "Hurricane",
  ;       "count" : 2,
  ;       "subfields" : [ "topic" ],
  ;       "topic" : [ {
  ;         "value" : "Popular",
  ;           ...
  ;
