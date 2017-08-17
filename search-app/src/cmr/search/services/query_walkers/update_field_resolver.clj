(ns cmr.search.services.query-walkers.update-field-resolver
  "Defines protocols and functions to resolve conditions for a provided field."
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as fvrf]))

(defprotocol UpdateQueryForField
  "Defines functions to adjust query to either remove a field from the query or update the name of
  the field."
  (has-field?
    [condition field-key]
    "Returns true if the condition has the field key")

  (remove-field
    [condition field-key]
    "Returns the query condition by dropping the conditions that are related to the field key.")

  (rename-field
    [condition field-key new-field-key]
    "Returns the query condition by changing the original field key to the new field key."))

(defn- has-field-within-condition?
  "Returns true if the provided field is referenced within the :condition field of the passed
  in query-condition."
  [query field-key]
  (has-field? (:condition query) field-key))

(defn- remove-field-within-condition
  "Removes any query which references the provided field from within the condition."
  [query field-key]
  (when-not (has-field? (:condition query) field-key)
    query))

(defn remove-field-within-root-query
  "Removes any part of the condition which references the provided field. Returns a match-all
  query if no query condition remains."
  [query field-key]
  (if-let [adjusted-condition (remove-field (:condition query) field-key)]
    (assoc query :condition adjusted-condition)
    (assoc query :condition cqm/match-all)))

(defn- rename-field-within-condition
  "Replaces any references to field-key with new-field-key within the condition."
  [query field-key new-field-key]
  (assoc query :condition (rename-field (:condition query) field-key new-field-key)))

(def condition-matching-fns
  "Functions which are used for the UpdateQueryForField protocol for any query condition records
  that contain a :condition field."
  {:has-field? has-field-within-condition?
   :remove-field remove-field-within-condition
   :rename-field rename-field-within-condition})

(extend cmr.common_app.services.search.query_model.Query
        UpdateQueryForField
        (merge condition-matching-fns
               {:remove-field remove-field-within-root-query}))

(extend cmr.common_app.services.search.query_model.NestedCondition
        UpdateQueryForField
        condition-matching-fns)

(extend cmr.common_app.services.search.query_model.NegatedCondition
        UpdateQueryForField
        condition-matching-fns)

(extend cmr.common_app.services.search.query_model.RelatedItemQueryCondition
        UpdateQueryForField
        condition-matching-fns)

(extend-protocol UpdateQueryForField
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup
  (has-field?
   [cg field-key]
   (let [conditions (:conditions cg)]
     (util/any? #(has-field? % field-key) conditions)))

  (remove-field
   [cg field-key]
   (let [{:keys [operation conditions]} cg
         conditions (keep #(remove-field % field-key) conditions)]
     (when (seq conditions)
       (gc/group-conds operation conditions))))

  (rename-field
   [cg field-key new-field-key]
   (let [{:keys [operation conditions]} cg
         conditions (keep #(rename-field % field-key new-field-key) conditions)]
     (when (seq conditions)
       (gc/group-conds operation conditions))))

  cmr.common_app.services.search.query_model.NumericRangeIntersectionCondition
  (has-field?
   [c field-key]
   (or (= (:min-field c) field-key)
       (= (:max-field c) field-key)))

  (remove-field
   [c field-key]
   ;; If the field matches either the min-field or max-field remove it
   (when-not (has-field? c field-key)
     c))

  (rename-field
   [c field-key new-field-key]
   (if (has-field? c field-key)
     ;; Same field key for both min and max we rename both of them
     (if (= field-key (:max-field c) (:min-field c))
       (assoc c :max-field new-field-key :min-field new-field-key)
       (if (= field-key (:max-field c))
         (assoc c :max-field new-field-key)
         (assoc c :min-field new-field-key)))
     c))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; catch all resolver
  java.lang.Object
  (has-field?
   [this field-key]
   (= (:field this) field-key))

  (remove-field
   [this field-key]
   (when-not (has-field? this field-key)
     this))

  (rename-field
   [this field-key new-field-key]
   (if (has-field? this field-key)
     (assoc this :field new-field-key)
     this)))
