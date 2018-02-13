(ns cmr.search.services.query-execution.facets.granule-v2-facets
  "Functions for generating v2 granule facets. Similar structure as v2 collection facets, but
  granule fields. First major use case is supporting OPeNDAP virutal directories capability."
  (:require
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as v2-facets]
   [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]
   [cmr.search.services.query-execution.facets.temporal-facets :as temporal-facets]))

(def granule-facet-params->elastic-fields
  "Maps the parameter names for the concept-type to the fields in Elasticsearch."
  {:start-date :start-date-doc-values})

(defmethod v2-facets/facets-v2-params->elastic-fields :granule
  [_]
  granule-facet-params->elastic-fields)

(def granule-facet-params
  "Granule facet params."
  (keys granule-facet-params->elastic-fields))

(defmethod v2-facets/facets-v2-params :granule
  [_]
  granule-facet-params)

(def granule-fields->aggregation-fields
  "Defines the mapping of granule parameter names to the aggregation parameter names."
  (into {}
        (map (fn [field]
               [field (q2e/query-field->elastic-field field :granule)])
             granule-facet-params)))

(defmethod v2-facets/facet-fields->aggregation-fields :granule
  [_]
  granule-fields->aggregation-fields)

(defmethod v2-facets/v2-facets-root :granule
  [_]
  {:title "Browse Granules"
   :type :group})

(defmethod v2-facets/v2-facets-result-field-in-order :granule
  [_]
  ["Temporal"])

(defn single-collection-validation
  "Validates that the provided query is limited to a single collection. We do this to prevent
  expensive aggregation queries that would have to run against more than one granule index."
  [context]
  (let [collection-ids (:query-collection-ids context)
        collection-count (count collection-ids)]
    (when-not (= 1 collection-count)
      [(format "Granule V2 facets are limited to a single collection, but query matched %s collections."
               (if (= 0 collection-count) "an undetermined number of" collection-count))])))

(def validations
  "Validation functions to run for v2 granule facets."
  (util/compose-validations [single-collection-validation]))

(defmethod v2-facets/facets-validator :granule
  [_]
  (util/build-validator :bad-request validations))

(def group-nodes-in-order
  "The titles of temporal facet group nodes in order."
  ["Year" "Month" "Day"])
;
; (defn get-children-depth
;   "Returns how many levels of children the facets contain."
;   [facets]
;   (loop [facets facets
;          level 1]
;     (if facets
;       (recur (:children facets) (inc level))
;       level)))
;
;

(comment
   (some? (some identity [false true false true]))
   (some? (some true? [false true false]))
   (any? #(= true %) [false false])
   (not (apply = [false true false]))
   (not (apply = [true true]))

   (some? #{true} [false false]))

(defn add-group-nodes-to-facets
  "Adds group nodes (Year, Month, Day) as applicable to the provided facets."
  [facets remaining-levels]
  ; (let [num-levels (get-children-depth subfacets)]
    ; (loop [updated-facets subfacets
    ;        level 1
    ;        remaining-levels group-nodes-in-order]
  ; (println "Facets are:" facets)
  (println remaining-levels)
  (let [applied? (some? (some true? (map :applied (:children facets))))
        years (:children facets)
        ; _ (println "Applied" applied?)
        updated-facets (v2h/generate-group-node (first remaining-levels) applied?
                                                (:children facets))
        ; _ (println "Updated-facets" updated-,ffacets)
        children-facets (doall
                         (for [child (reverse (:children facets))]
                          (if (some? (:children child))
                            (add-group-nodes-to-facets child (rest remaining-levels))
                            child)))

        ; _ (println "Children facets" children-facets)
        parent-to-children (-> updated-facets :children first)
        ; _ (println "Parent to children" parent-to-children)
        updated-parent-to-children (assoc parent-to-children :children children-facets)]
        ; parent (-> updated-facets :children first)
        ; updated-parent]

    ; (v2h/generate-group-node (first remaining-levels) applied? (assoc facets :children children-facets))))
    (if (some? (-> facets :children first :children))
      (assoc updated-facets :children [updated-parent-to-children])
      (assoc updated-facets :children children-facets))))
    ; (cmr.common.util/update-in-each updated-facets [:children] children-facets)))

(defn add-group-nodes-to-facets2
  "Adds group nodes (Year, Month, Day) as applicable to the provided facets."
  [facets remaining-levels]
  ; (let [num-levels (get-children-depth subfacets)]
    ; (loop [updated-facets subfacets
    ;        level 1
    ;        remaining-levels group-nodes-in-order]
  ; (println "Facets are:" facets)
  (println remaining-levels)
  (println "Facets" facets)
  (let [applied? (some? (some true? (map :applied facets)))
        ; _ (println "Applied" applied?)
        ; updated-facets (v2h/generate-group-node (first remaining-levels) applied?
        ;                                         facets)
        ; _ (println "Updated-facets" updated-,ffacets)
        children-facets (doall
                         (for [facet (reverse facets)]
                          (if (:has_children facet)
                            (assoc facet :children [(add-group-nodes-to-facets2 (:children facet) (rest remaining-levels))])
                            facet)))]

        ; _ (println "Children facets" children-facets)
        ; parent-to-children (-> updated-facets :children first)
        ; ; _ (println "Parent to children" parent-to-children)
        ; updated-parent-to-children (assoc parent-to-children :children children-facets)]
        ; parent (-> updated-facets :children first)
        ; updated-parent]

    ; (v2h/generate-group-node (first remaining-levels) applied? (assoc facets :children children-facets))))
    ; (if (some? (-> facets :children first :children))
      ; (assoc updated-facets :children [updated-parent-to-children])
      ; (assoc updated-facets :children children-facets)))

    (v2h/generate-group-node (first remaining-levels) applied? children-facets)))
; (defn add-group-nodes-to-facets
;   "Adds group nodes (Year, Month, Day) as applicable to the provided facets."
;   [facets]
;   (let [years facets
;         months (:children facets)]))

(defmethod v2-facets/create-v2-facets-by-concept-type :granule
  [concept-type base-url query-params aggs _]
  (let [subfacets (hv2/hierarchical-bucket-map->facets-v2
                   :temporal-facet
                   (:start-date-doc-values aggs)
                   base-url
                   query-params)]
    (when (seq subfacets)
      (let [field-reg-ex (re-pattern "temporal_facet.*")
            applied? (->> query-params
                          (filter (fn [[k v]] (re-matches field-reg-ex k)))
                          seq
                          some?)
            _ (println "Starting subfacets" subfacets)
            updated-subfacets (add-group-nodes-to-facets2 (:children subfacets) group-nodes-in-order)]
            ; updated-subfacets (add-group-nodes-to-facets subfacets group-nodes-in-order)]
            ; subfacets-missing-title? (nil? (:title subfacets))
            ; updated-subfacets (if subfacets-missing-title?
            ;                     (v2h/generate-group-node "Year" applied?
            ;                                              (:children subfacets))
            ;                     (assoc subfacets :applied applied?))
            ; ;; Return facets in reverse chronological order
            ; updated-subfacets (update updated-subfacets :children reverse)]
        [(merge v2h/sorted-facet-map
                (v2h/generate-group-node "Temporal" applied?
                                        ;  [subfacets]))]))))
                                         [updated-subfacets]))]))))
