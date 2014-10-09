(ns cmr.search.models.condition-merger
  "Contains functions for merging and simplifying conditions in a condition group"
  (:require [cmr.search.models.query :as q]
            [clojure.set :as set])
  (:import [cmr.search.models.query
            StringCondition
            StringsCondition]))

(defprotocol ConditionValueExtractor
  (extract-values
    [condition]
    "Returns a sequence of values from the condition"))

(extend-protocol ConditionValueExtractor
  StringCondition
  (extract-values
    [{:keys [value]}]
    [value])

  StringsCondition
  (extract-values
    [{:keys [values]}]
    values))


(def mergeable-types
  "The set of types of conditions that can be merged together. Limited to string like conditions
  because these are the most likely ones to be created from ACLs."
  #{StringCondition StringsCondition})

(def single-value-string-fields
  "This is a set of fields that can contain only a single value for a concept. We can only merge
  together string conditions if the values are for fields that can contain only a single value.
  For example a concept can only have a single :concept-id. AND of two different concept-ids will
  find nothing. But a concept id could potentially have multiple platform names. ANDing two
  platform names would find concepts that have both platforms."
  #{:concept-id :collection-concept-id :entry-title :provider :granule-ur :short-name :version})

(defn mergeable?
  "Returns true if a condition can be merged with other conditions"
  [c]
  (and (mergeable-types (type c))
       (not (:pattern? c))
       (single-value-string-fields (:field c))))

(defmulti merge-condition-group
  "Multimethod merges together a group of mergable conditions.
  Arguments:
   * group-operation - the group operation i.e. :and or :or
   * group-info - the map of common group fields.
   * conditions - the list of conditions to merge."
  (fn [group-operation group-info conditions]
    group-operation))

(defmethod merge-condition-group :or
  [group-operation {:keys [field case-sensitive?]} conditions]
  (let [values (distinct (mapcat extract-values conditions))]
    (q/string-conditions field values case-sensitive?)))

(defmethod merge-condition-group :and
  [group-operation {:keys [field case-sensitive?]} conditions]
  ;; When we have a series of AND'd conditions we will extract each set of values which are OR'd
  ;; and create a set of each one.
  (let [value-sets (map (comp set extract-values) conditions)
        ;; Find the intersections of all the sets which is a set of values that should be OR'd together.
        values (seq (apply set/intersection value-sets))]
    (if values
      (q/string-conditions field values case-sensitive?)
      ;; If the intersection of values was empty this meant that nothing matched.
      q/match-none)))

(defn merge-conditions
  "Merges together the conditions if possible from a grouping query condition."
  [group-operation conditions]
  (let [;; Split conditions into those that can be merged and those that can't
        {:keys [mergeable non-mergeable]} (group-by #(if (mergeable? %)
                                                       :mergeable
                                                       :non-mergeable)
                                                    conditions)
        ;; Split the mergable conditions into separate groups.
        mergable-groups (group-by #(select-keys % [:case-sensitive? :field]) mergeable)
        ;; The conditions within each group can be merged together.
        merged-conditions (map (partial apply merge-condition-group group-operation) mergable-groups)]
    (concat merged-conditions non-mergeable)) )



