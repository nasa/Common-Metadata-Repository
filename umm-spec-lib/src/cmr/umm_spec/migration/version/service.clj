(ns cmr.umm-spec.migration.version.service
  "Contains functions for migrating between versions of the UMM Service schema."
  (:require
   [cmr.common.util :as util]
   [cmr.common.log :as log]
   [cmr.umm-spec.migration.version.interface :as interface]))

(defn- migrate-related-url-subtype-down
  "Migrate value TOOL to nil"
  [related-url]
  (if (= "TOOL" (get related-url :SubType))
    (assoc related-url :SubType nil)
    related-url))

(defn- migrate-coverage-type-down
  "Migrate CoverageType changes down from 1.1 to 1.0"
  [coverage-type]
  (-> coverage-type
      (assoc :Type (get-in coverage-type [:CoverageSpatialExtent :CoverageSpatialExtentTypeType]))
      (update :CoverageTemporalExtent dissoc :CoverageTemporalExtentTypeType)
      (update :CoverageSpatialExtent dissoc :CoverageSpatialExtentTypeType)))

(defn- migrate-coverage-type-up
  "Migrate CoverageType changes up from 1.0 to 1.1"
  [coverage-type]
  (-> coverage-type
      (update :CoverageSpatialExtent
              assoc :CoverageSpatialExtentTypeType (get coverage-type :Type))
      (dissoc :Type)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;;; Service Migration Implementations

(defmethod interface/migrate-umm-version [:service "1.0" "1.1"]
  [context s & _]
  (log/error "DBG: MIGRATING SERVICE UP 1.0 to 1.1" s)
  (-> s
      (assoc :AccessConstraints (first (:AccessConstraints s)))
      (update :AccessConstraints #(util/trunc % 1024))
      (update :UseConstraints #(util/trunc % 1024))
      (update-in [:ServiceQuality :Lineage] #(util/trunc % 100))
      (assoc :RelatedURLs [(:RelatedURL s)])
      (update :Coverage migrate-coverage-type-up)
      (dissoc :RelatedURL)
      util/remove-empty-maps))

(defmethod interface/migrate-umm-version [:service "1.1" "1.0"]
  [context s & _]
  (log/error "DBG: MIGRATING SERVICE DOWN 1.1 to 1.0" s)
  (-> s
      (assoc :AccessConstraints [(:AccessConstraints s)])
      (assoc :RelatedURL (first (:RelatedURLs s)))
      (update :RelatedURL migrate-related-url-subtype-down)
      (update :Coverage migrate-coverage-type-down)
      (dissoc :RelatedURLs)
      util/remove-empty-maps))
