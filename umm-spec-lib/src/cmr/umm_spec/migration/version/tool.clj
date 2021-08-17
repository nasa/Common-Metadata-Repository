(ns cmr.umm-spec.migration.version.tool
  "Contains functions for migrating between versions of the UMM tool schema."
  (:require
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.version.interface :as interface]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;;; tool Migration Implementations

(defmethod interface/migrate-umm-version [:tool "1.0" "1.1"]
  [context t & _]
  (-> t
      (dissoc :SearchAction)
      (m-spec/update-version :tool "1.1")))

(defmethod interface/migrate-umm-version [:tool "1.1" "1.0"]
  [context t & _]
  (-> t
      (dissoc :PotentialAction)
      (m-spec/update-version :tool "1.0")))
