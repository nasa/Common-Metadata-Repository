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

(extend-protocol UpdateQueryForField
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query
  (has-field?
   [query field-key]
   (has-field? (:condition query) field-key))

  (remove-field
   [query field-key]
   (if-let [adjusted-condition (remove-field (:condition query) field-key)]
     (assoc query :condition adjusted-condition)
     (assoc query :condition cqm/match-all)))

  (rename-field
   [query field-key new-field-key]
   (if-let [adjusted-condition (rename-field (:condition query) field-key new-field-key)]
     (assoc query :condition adjusted-condition)
     (assoc query :condition cqm/match-all)))
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

  cmr.common_app.services.search.query_model.NestedCondition
  (has-field?
   [c field-key]
   (has-field? (:condition c) field-key))

  (remove-field
   [c field-key]
   ;; drop nested condition that has the facet field
   (when-not (has-field? (:condition c) field-key)
     c))

  (rename-field
   [c field-key new-field-key]
   ;; drop nested condition that has the facet field
   (rename-field (:condition c) field-key new-field-key))

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
