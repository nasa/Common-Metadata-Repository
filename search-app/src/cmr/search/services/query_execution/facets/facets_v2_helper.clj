(ns cmr.search.services.query-execution.facets.facets-v2-helper
  "Helper vars and functions for generating v2 facet responses."
  (:require [cmr.search.services.query-execution.facets.links-helper :as lh]
            [cmr.common.util :as util]))

(def sorted-facet-map
  "A map that sorts the keys of the facet map so it is presented in a pleasing way to Users
  of the API. The nested hierarchical maps API can be hard to understand if the maps are ordered
  randomly."
  (util/key-sorted-map [:title :type :applied :count :links :has_children :children]))

(def fields->human-readable-label
  "Map of facet fields to their human readable label."
  {:data-center-h "Organizations"
   :project-h "Projects"
   :platform-h "Platforms"
   :instrument-h "Instruments"
   :processing-level-id-h "Processing levels"
   :science-keywords-h "Keywords"})

(defn terms-facet
  "Construct a terms query to be applied for the given field. Size specifies the number of results
  to return."
  [field size]
  {:terms {:field field :size size}})

(defn- prioritized-facet-key
  "Produces a keyword for prioritized facets that is :<field>.<key>"
  [field key]
  (keyword (str (name field) "." (name key))))

(defn prioritized-facet
  "Construct a facet query for use with fields that have a priority value"
  [field size]
  ;; Facets on the :value field of the nested {:value :priority} map used by
  ;; humanized facets, yielding the top <size> results sorted first by
  ;; priority, then by count.
  ;; Two facets with the same value can have different priorities based
  ;; on the order of humanizer application, we have to average the priorities
  ;; within each value bucket and cannot just use min or max.
  {:nested {:path field}
   :aggs {:values {:terms {:field (prioritized-facet-key field :value)
                           :size size
                           :order [{:priority :desc} {:_count :desc}]}
                   :aggs {:priority {:avg {:field (prioritized-facet-key field :priority)}}}}}})

(defn generate-group-node
  "Returns a group node for the provided title, applied?, and children."
  [title applied? children]
  {:title title
   :type :group
   :applied applied?
   :has_children (some? children)
   :children children})

(defn generate-filter-node
  "Returns a function to generate a child node with the provided base-url, query-params, and
  field-name. Returned function takes two arguments (a term and a count of collections containing
  that term)."
  [base-url query-params field-name some-term-applied?]
  (fn [[term count]]
    (let [links (if some-term-applied?
                    (lh/create-link base-url query-params field-name term)
                    (lh/create-apply-link base-url query-params field-name term))]
     (merge sorted-facet-map
            {:title term
             :type :filter
             :applied (= :remove (first (keys links)))
             :links links
             :count count
             :has_children false}))))

(defn- any-facet-applied?
  "Returns true if any of the facets have an applied value of true, false otherwise."
  [facets]
  (some? (seq (filter :applied facets))))

(defn generate-hierarchical-filter-node
  "Generates a filter node for a hierarchical field. Takes a title, count, links and sub-facets."
  [title count links sub-facets]
  (merge sorted-facet-map
         {:has_children false
          :applied false}
         sub-facets
         {:title title
          :applied (or (= :remove (first (keys links)))
                       (any-facet-applied? (:children sub-facets)))
          :links links
          :count count
          :type :filter}))
