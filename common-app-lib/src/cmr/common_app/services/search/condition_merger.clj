(ns cmr.common-app.services.search.condition-merger
  "Contains functions for merging and simplifying conditions in a condition group"
  (:require [cmr.common-app.services.search.query-model :as q]
            [clojure.set :as set])
  (:import [cmr.common_app.services.search.query_model
            StringCondition
            StringsCondition
            RelatedItemQueryCondition]))

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

(def merge-type->merge-strategy
  "The set of types of conditions that can be merged together mapped to the merge strategy to use."
  {StringCondition :string-condition
   StringsCondition :string-condition
   RelatedItemQueryCondition :related-item})

(def single-value-string-fields
  "This is a set of fields that can contain only a single value for a concept. We can only merge
  together string conditions if the values are for fields that can contain only a single value.
  For example a concept can only have a single :concept-id. AND of two different concept-ids will
  find nothing. But a concept id could potentially have multiple platform names. ANDing two
  platform names would find concepts that have both platforms."
  #{:concept-id :collection-concept-id :entry-title :provider :granule-ur :short-name :version})

(defn condition->merge-strategy
  "Returns the merge strategy to use if the condition is mergeable."
  [c]
  (when-let [merge-strategy (merge-type->merge-strategy (type c))]
    (case merge-strategy
      :string-condition
      (when (and (not (:pattern? c)) (single-value-string-fields (:field c)))
        :string-condition)

      :related-item
      :related-item)))

(defmulti merge-string-condition-group
  "Multimethod merges together a group of mergable conditions.
  Arguments:
   * group-operation - the group operation i.e. :and or :or
   * group-info - the map of common group fields.
   * conditions - the list of conditions to merge."
  (fn [group-operation group-info conditions]
    group-operation))

(defmethod merge-string-condition-group :or
  [group-operation {:keys [field case-sensitive?]} conditions]
  (let [values (distinct (mapcat extract-values conditions))]
    (q/string-conditions field values case-sensitive?)))

(defmethod merge-string-condition-group :and
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

(defmulti merge-conditions-with-strategy
  "Merges together the conditions using the given strategy"
  (fn [strategy group-operation conditions]
    strategy))

;; Conditions with no merge strategy are not merged together
(defmethod merge-conditions-with-strategy nil
  [_ _ conditions]
  conditions)

(defmethod merge-conditions-with-strategy :string-condition
  [_ group-operation conditions]
  ;; Split the mergable conditions into separate groups.
  (let [mergable-groups (group-by #(select-keys % [:case-sensitive? :field]) conditions)]
    ;; The conditions within each group can be merged together.
    (map (fn [[group-info grouped-conds]]
           (merge-string-condition-group group-operation group-info grouped-conds))
         mergable-groups)))

(defmethod merge-conditions-with-strategy :related-item
  [_ group-operation conditions]
  ;; Split the mergable conditions into separate groups.
  (let [mergable-groups (group-by #(select-keys % [:concept-type :result-fields :results-to-condition-fn])
                                  conditions)]
    ;; The conditions within each group can be merged together.
    (map (fn [[common-fields grouped-conds]]
           (if (> (count grouped-conds) 1)
             (q/map->RelatedItemQueryCondition
               (assoc common-fields
                      :condition (q/->ConditionGroup group-operation
                                                     (mapv :condition grouped-conds))))
             (first grouped-conds)))
         mergable-groups)))

(defn merge-conditions
  "Merges together the conditions if possible from a grouping query condition."
  [group-operation conditions]
  (mapcat (fn [[strategy strategy-conds]]
            (merge-conditions-with-strategy strategy group-operation strategy-conds))
          (group-by condition->merge-strategy conditions)))



