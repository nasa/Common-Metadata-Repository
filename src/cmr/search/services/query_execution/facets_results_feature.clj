(ns cmr.search.services.query-execution.facets-results-feature
  "This is enables returning facets with collection search results"
  (:require [cmr.search.services.query-execution :as query-execution]
            [cmr.search.models.results :as r]
            [camel-snake-kebab :as csk]
            [clojure.data.xml :as x]))

(defn terms-facet
  [field]
  {:terms {:field field :size 10000}})

(def facet-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request facetted results
  from a collection search."
  {:project (terms-facet :project-sn)
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

(defmethod query-execution/pre-process-query-result-feature :facets
  [context query feature]
  (assoc query :aggregations facet-aggregations))

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

(defmethod query-execution/post-process-query-result-feature :facets
  [context query elastic-results query-results feature]
  (let [aggs (:aggregations elastic-results)
        science-keyword-buckets (:science-keywords aggs)
        facets (bucket-map->facets
                 aggs
                 ;; Specified here so that order will be consistent with results
                 [:project :platform :instrument :sensor :two-d-coordinate-system-name
                  :processing-level-id :category :topic :term :variable-level-1
                  :variable-level-2 :variable-level-3 :detailed-variable])]
    (assoc query-results :facets facets)))

(defn key-with-prefix
  [prefix k]
  (if prefix
    (symbol (str prefix ":" (name k)))
    k))

(defn facet->xml-element
  [ns-prefix facet]
  (let [{:keys [field value-counts]} facet
        with-prefix (partial key-with-prefix ns-prefix)]
    (x/element (with-prefix :facet) {:field field}
               (for [[value value-count] value-counts]
                 (x/element (with-prefix :value) {:count value-count} value)))))

(defn facets->xml-element
  "Helper function for converting a facet result into an XML element"
  ([facets]
   (facets->xml-element nil facets))
  ([ns-prefix facets]
   (when facets
     (x/element (key-with-prefix ns-prefix :facets) {}
                (map (partial facet->xml-element ns-prefix) facets)))))
