(ns cmr.search.data.elastic-results-to-query-results
  "Contains functions to convert elasticsearch results to query results."
  (:require
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]))

(defmethod elastic-results/get-revision-id-from-elastic-result :collection
  [concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))

(defmethod elastic-results/get-revision-id-from-elastic-result :variable
  [concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))

(defmethod elastic-results/get-revision-id-from-elastic-result :service
  [concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))

(defmethod elastic-results/get-revision-id-from-elastic-result :tool
  [concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))

(defmethod elastic-results/get-revision-id-from-elastic-result :subscription
  [concept-type elastic-result]
  (get-in elastic-result [:_source :revision-id]))
