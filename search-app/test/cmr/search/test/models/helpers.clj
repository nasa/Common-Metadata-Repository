(ns cmr.search.test.models.helpers
  "Contains helper functions for testing the query walkers namespaces."
  (:require [cmr.search.models.query :as q]
            [cmr.common-app.services.search.query-model :as cqm]
            [cmr.common-app.services.search.group-query-conditions :as gc]))

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

(defn coll-query-cond
  [condition]
  (q/->CollectionQueryCondition condition))

(defn generic
  "Creates a generic condition with a specific string name"
  [named]
  (cqm/string-condition :foo named))
