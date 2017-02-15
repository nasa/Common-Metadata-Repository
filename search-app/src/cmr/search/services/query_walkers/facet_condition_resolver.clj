(ns cmr.search.services.query-walkers.facet-condition-resolver
  "Defines protocols and functions to resolve facet conditions by removing the conditions that
   match the given facet field."
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.util :as util]))


(defprotocol AdjustFacetQuery
  "Defines function to adjust facet query for a given facet field."
   (adjust-facet-query
    [c field-key]
    "Returns the query condition by dropping the conditions that are related to the field key.")

  (has-field?
    [c field-key]
    "Returns true if the condition has the field key"))

(extend-protocol AdjustFacetQuery
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query
  (has-field?
   [query field-key]
   (has-field? (:condition query) field-key))

  (adjust-facet-query
   [query field-key]
   (if-let [adjusted-condition (adjust-facet-query (:condition query) field-key)]
     (assoc query :condition adjusted-condition)
     (assoc query :condition cqm/match-all)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup
  (has-field?
   [cg field-key]
   (let [conditions (:conditions cg)]
     (util/any? #(has-field? % field-key) conditions)))

  (adjust-facet-query
   [cg field-key]
   (let [{:keys [operation conditions]} cg
         conditions (keep #(adjust-facet-query % field-key) conditions)]
     (when (seq conditions)
       (gc/group-conds operation conditions))))

  cmr.common_app.services.search.query_model.NestedCondition
  (has-field?
   [c field-key]
   (has-field? (:condition c) field-key))

  (adjust-facet-query
   [c field-key]
   (if (has-field? (:condition c) field-key)
     nil
     (update c :condition #(adjust-facet-query % field-key))))

  cmr.common_app.services.search.query_model.StringCondition
  (has-field?
   [c field-key]
   (if (re-matches (re-pattern (str field-key ".*")) (str (:field c)))
     true
     false))

  (adjust-facet-query
   [c field-key]
   (when-not (re-matches (re-pattern (str field-key ".*")) (str (:field c)))
     c))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; catch all resolver
  java.lang.Object
  (has-field? [this field-key] false)
  (adjust-facet-query [this field-key] this))
