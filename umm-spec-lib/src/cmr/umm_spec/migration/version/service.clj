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

(defn- migrate-types-down
  "Migrates CoverageSpatialExtent and CoverageTemporalExtent types from 1.1 to 1.0"
  [coverage-type]
  (if-let [type (get-in coverage-type [:CoverageSpatialExtent :CoverageSpatialExtentTypeType])]
    (-> coverage-type
        (assoc :Type type)
        (assoc-in [:CoverageSpatialExtent :Type] type))))

(defn- migrate-coverage-type-down
  "Migrate CoverageType changes down from 1.1 to 1.0"
  [coverage-type]
  (-> coverage-type
      (assoc :Type (get-in coverage-type [:CoverageSpatialExtent :CoverageSpatialExtentTypeType]))
      migrate-types-down
      (update :CoverageTemporalExtent dissoc :CoverageTemporalExtentType)
      (update :CoverageSpatialExtent dissoc :CoverageSpatialExtentTypeType)))

(defn- migrate-coverage-type-up
  "Migrate CoverageType changes up from 1.0 to 1.1"
  [coverage-type]
  (if-let [type (or (get-in coverage-type [:CoverageSpatialExtent :Type])
                    (get coverage-type :Type))]
    (-> coverage-type
        (update :CoverageSpatialExtent
                assoc :CoverageSpatialExtentTypeType type)
        (update :CoverageSpatialExtent dissoc :Type)
        (dissoc :Type))
    (dissoc coverage-type :Type)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;;; Service Migration Implementations

(defmethod interface/migrate-umm-version [:service "1.0" "1.1"]
  [context s & _]
  (-> s
      (assoc :AccessConstraints (first (:AccessConstraints s)))
      (assoc :RelatedURLs [(:RelatedURL s)])
      (assoc :UseConstraints (first (:UseConstraints s)))
      (update :Coverage migrate-coverage-type-up)
      (dissoc :RelatedURL)
      (util/update-in-each [:ServiceOrganizations] dissoc :Uuid)
      util/remove-empty-maps))

(defmethod interface/migrate-umm-version [:service "1.1" "1.0"]
  [context s & _]
  (-> s
      (assoc :AccessConstraints [(util/trunc (:AccessConstraints s) 1024)])
      (assoc :UseConstraints [(util/trunc (:UseConstraints s) 1024)])
      (update-in [:ServiceQuality :Lineage] #(util/trunc % 100))
      (assoc :RelatedURL (first (:RelatedURLs s)))
      (update :RelatedURL migrate-related-url-subtype-down)
      (update :Coverage migrate-coverage-type-down)
      (dissoc :RelatedURLs)
      util/remove-empty-maps))
