(ns cmr.umm-spec.migration.version.interface
  "Contains the interface definition for migrating between versions of UMM
  schemas."
  (:require
   [cmr.common.log :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Interface for Migrating Between Versions

(defn- dispatch-migrate
  [_context _concept concept-type source-version dest-version]
  [concept-type source-version dest-version])

(defmulti migrate-umm-version
  "Returns the given data structure of the indicated concept type and UMM
  version updated to conform to the target UMM schema version."
  (fn [_context _concept concept-type source-version dest-version]
   [concept-type source-version dest-version]))

(defmethod migrate-umm-version :default
  [context concept & _]
  ;; Do nothing by default. This lets us skip over "holes" in the version
  ;; sequence, where the UMM version may be updated but a particular concept
  ;; type's schema may not be affected.
  concept)
