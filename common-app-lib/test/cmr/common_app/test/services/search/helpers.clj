(ns cmr.common-app.test.services.search.helpers
  "Contains helper functions for testing"
  (:require [cmr.elastic-utils.es-query-model :as cqm]
            [cmr.elastic-utils.es-group-query-conditions :as gc]))

(defn and-conds
  [& conds]
  (gc/and-conds conds))

(defn or-conds
  [& conds]
  (gc/or-conds conds))

(defn negated
  [c]
  (cqm/->NegatedCondition c))

(defn other
  "Creates a unique condition"
  ([]
   (cqm/string-conditions :foo ["foo"]))
  ([n]
   (cqm/string-conditions :foo [(str "other" n)])))

(defn generic
  "Creates a generic condition with a specific string name"
  [named]
  (cqm/string-condition :foo named))
