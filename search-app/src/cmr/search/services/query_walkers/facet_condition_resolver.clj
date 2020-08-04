(ns cmr.search.services.query-walkers.facet-condition-resolver
  "Defines protocols and functions to resolve facet conditions by removing the conditions that
   match the given facet field."
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as fvrf]))

(defn- string-condition-for-v2-facet-field?
  "Returns true if the given string condition is on the given v2 facet field"
  [c field-key]
  (boolean (or
            ;; The first check handles the data-center-h field since its query condition field
            ;; :organization-humanized.value does not match the regex on the second check
            ;; The first check alone will not work for science-keywords
            ;; Note that this check only applies for collection facets and not granule facets
            (= (str (get (fvrf/facets-v2-params->elastic-fields :collection) field-key) ".value")
               (str (:field c)))
            (re-matches (re-pattern (str field-key ".*")) (str (:field c))))))

(defprotocol AdjustFacetQuery
  "Defines function to adjust facet query for a given facet field to remove the facet field from
   the query condition. The field-key is in the form of query parameter and the condition is in
   the form of simplified query condition."
  (has-field?
    [c field-key]
    "Returns true if the condition has the field key")

  (adjust-facet-query
   [c field-key]
   "Returns the query condition by dropping the conditions that are related to the field key."))

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
     (util/any-true? #(has-field? % field-key) conditions)))

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
   ;; drop nested condition that has the facet field
   (when-not (has-field? (:condition c) field-key)
     c))

  cmr.common_app.services.search.query_model.StringCondition
  (has-field?
   [c field-key]
   (string-condition-for-v2-facet-field? c field-key))

  (adjust-facet-query
   [c field-key]
   (when-not (string-condition-for-v2-facet-field? c field-key)
     c))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; catch all resolver
  java.lang.Object
  (has-field? [this field-key] false)
  (adjust-facet-query [this field-key] this))
