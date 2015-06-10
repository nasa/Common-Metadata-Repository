(ns cmr.search.services.query-execution.facets-results-feature
  "This is enables returning facets with collection search results"
  (:require [cmr.search.services.query-execution :as query-execution]
            [cmr.search.models.results :as r]
            [camel-snake-kebab.core :as csk]
            [clojure.data.xml :as x]))

(defn- terms-facet
  [field]
  ;; We shouldn't try to handle this many different values.
  ;; We should have a limit and if that's exceeded in the elastic response we should note that in
  ;; the values returned. This can be handled as a part of CMR-1101.
  {:terms {:field field :size 10000}})

(def ^:private collection-count-aggregation
  "Used to build an aggregation to get a count of unique concepts included in the current nested
  aggregation."
  {:reverse_nested {}
   :aggs {:concept-id {:terms {:field :concept-id :size 1}}}})

(def ^:private science-keyword-hierarchy
  "List containing the elements within the science keyword hierarchy from top to bottom."
  [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3])

(defn- science-keyword-aggregation-builder
  "Build the science keyword aggregations query.

  The elastic aggregations look like the following - the same pattern follows for the entire
  hierarchy:

  \"aggs\": {
  \"science_keywords\": {
  \"nested\": {
  \"path\": \"science-keywords\"},
  \"aggs\": {
  \"category\": {
  \"terms\": {
  \"field\": \"science-keywords.category\"},
  \"aggs\": {
  \"category-coll-count\": {
  \"reverse_nested\": {},
  \"aggs\": {
  \"concept-id\": {
  \"terms\": {
  \"field\": \"concept-id\",
  \"size\": 1}}}},
  \"topic\": {
  \"terms\": {
  \"field\": \"science-keywords.topic\"},
  \"aggs\": {
  \"topic-coll-count\": {
  \"reverse_nested\": {},
  \"aggs\": {
  \"concept-id\": {
  \"terms\": {
  \"field\": \"concept-id\",
  \"size\": 1}}}},..."
  [field-hierarchy]
  (when-let [field (first field-hierarchy)]
    {field {:terms {:field (str "science-keywords." (name field))}
            :aggs (merge {:coll-count collection-count-aggregation}
                         (science-keyword-aggregation-builder (rest field-hierarchy)))}}))

(def ^:private science-keyword-aggregations
  "Returns the nested aggregation query for science-keyword-aggregations in a hierarchical fashion"
  {:nested {:path :science-keywords}
   :aggs (science-keyword-aggregation-builder science-keyword-hierarchy)})

(def ^:private flat-facet-aggregations
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

(def ^:private hierarchical-facet-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request facetted results
  from a collection search."
  {:archive-center (terms-facet :archive-center)
   :project (terms-facet :project-sn)
   :platform (terms-facet :platform-sn)
   :instrument (terms-facet :instrument-sn)
   :sensor (terms-facet :sensor-sn)
   :two-d-coordinate-system-name (terms-facet :two-d-coord-name)
   :processing-level-id (terms-facet :processing-level-id)
   :science-keywords science-keyword-aggregations
   ;; Detailed variable is technically part of the science keyword hierarchy directly below
   ;; variable level 1 (at the same level as variable level 2.) Opened ticket TBD to address.
   :detailed-variable (terms-facet :detailed-variable)})

(defn- facet-aggregations
  "Return facet aggregations to use depending on whether a flat or hierarchical facet response is
  requested."
  [hierarchical-facets?]
  (if hierarchical-facets? hierarchical-facet-aggregations flat-facet-aggregations))

(defmethod query-execution/pre-process-query-result-feature :facets
  [context query feature]
  (assoc query :aggregations flat-facet-aggregations))

(defmethod query-execution/pre-process-query-result-feature :hierarchical-facets
  [context query feature]
  (assoc query :aggregations hierarchical-facet-aggregations))

(defn- buckets->value-count-pairs
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
  "Parses the elasticsearch aggregations response for hierarchical fields.

  Here is a sample response:

  {:subfields [\"category\"],
  :category
  [{:value \"Hurricane\",
  :count 2,
  :subfields [\"topic\"],
  :topic
  [{:value \"Popular\",
  :count 2,
  :subfields [\"term\"],
  :term
  [{:value \"Extreme\",
  :count 2,
  :subfields [\"variable-level-1\"],
  :variable-level-1
  [{:value \"Level2-1\",
  :count 2,
  :subfields [\"variable-level-2\"],
  :variable-level-2
  [{:value \"Level2-2\",
  :count 2,
  :subfields [\"variable-level-3\"],
  :variable-level-3
  [{:value \"Level2-3\", :count 2}]}]}]}
  {:value \"UNIVERSAL\", :count 2}]}
  {:value \"Cool\",
  :count 2,
  :subfields [\"term\"],
  :term
  [{:value \"Term4\",
  :count 2,
  :subfields [\"variable-level-1\"],
  :variable-level-1
  [{:value \"UNIVERSAL\", :count 2}]}]}]}]}"
  [field-hierarchy aggregations-for-field]
  (when-let [field (first field-hierarchy)]
    (let [next-field (first (rest field-hierarchy))
          value-count-maps (for [bucket (:buckets aggregations-for-field)
                                 :let [sub-aggregations-for-field (when next-field
                                                                    (select-keys
                                                                      (get bucket next-field)
                                                                      [:coll-count :buckets]))]]
                             (merge (when (seq (:buckets sub-aggregations-for-field))
                                      (parse-hierarchical-bucket (rest field-hierarchy)
                                                                 sub-aggregations-for-field))
                                    {:count (get-in bucket [:coll-count :doc_count])
                                     :value (:key bucket)}))]
      (if (seq value-count-maps)
        {:subfields [field]
         field value-count-maps}
        {:subfields []}))))

(defn- science-keywords-bucket->facets
  "Takes a map of elastic aggregation results for science keywords. Returns a hierarchical facet
  map of science keywords. See parse-hierarchical-bucket for an example response."
  [bucket-map]
  (parse-hierarchical-bucket science-keyword-hierarchy
                             (get bucket-map (first science-keyword-hierarchy))))

(defn- create-hierarchical-facets
  "Create the facets response with hierarchical facets. Takes an elastic aggregations result and
  returns the facets."
  [elastic-aggregations]
  (concat (bucket-map->facets (dissoc elastic-aggregations :science-keywords)
                              [:archive-center :project :platform :instrument :sensor
                               :two-d-coordinate-system-name :processing-level-id
                               :detailed-variable])
          [(merge (science-keywords-bucket->facets (:science-keywords elastic-aggregations))
                  {:field "science_keywords"})]))

(defn- create-flat-facets
  "Create the facets response with flat facets. Takes an elastic aggregations result and returns
  the facets."
  [elastic-aggregations]
  (bucket-map->facets
    elastic-aggregations
    ;; Specified here so that order will be consistent with results
    [:archive-center :project :platform :instrument :sensor
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
               (map facet->echo-xml-element facets))))

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
    (into {} (map facet->echo-json facets))))

