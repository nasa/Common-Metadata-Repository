(ns cmr.umm-spec.migration.version.variable
  "Contains functions for migrating between versions of the UMM Variable schema."
  (:require
   [cmr.umm-spec.migration.version.interface :as interface]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions

(defn- migrate-services-down
  "Migrate :DOI from CollectionCitation level up to collection level."
  [v]
  (if-let [services-obj (some :Services v)]
    (assoc v :Services services-obj)
    v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Variable Migration Implementations

(defmethod interface/migrate-umm-version [:variable "1.0" "1.1"]
  [context v & _]
  (dissoc v :Services))

(defmethod interface/migrate-umm-version [:variable "1.1" "1.0"]
  [context v & _]
  (migrate-services-down v))
