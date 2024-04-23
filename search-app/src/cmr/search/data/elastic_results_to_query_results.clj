(ns cmr.search.data.elastic-results-to-query-results
  "Contains functions to convert elasticsearch results to query results."
  (:require
   [cmr.elastic-utils.search.es-results-to-query-results :as elastic-results]))

(defmethod elastic-results/get-revision-id-from-elastic-result :collection
  [_concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))

(defmethod elastic-results/get-revision-id-from-elastic-result :variable
  [_concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))

(defmethod elastic-results/get-revision-id-from-elastic-result :service
  [_concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))

(defmethod elastic-results/get-revision-id-from-elastic-result :tool
  [_concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))

(defmethod elastic-results/get-revision-id-from-elastic-result :subscription
  [_concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))
