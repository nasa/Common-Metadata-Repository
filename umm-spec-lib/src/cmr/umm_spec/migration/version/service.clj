(ns cmr.umm-spec.migration.version.service
  "Contains functions for migrating between versions of the UMM Service schema."
  (:require
   [clojure.set :as set]
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

(defn- duplicate-supported-projections
  "Duplicate the SupportedProjections in v1.1 ServiceOptions to SupportedInputProjections
  and SupportedOutputProjections in v1.2 ServiceOptions when migrate from 1.1 to 1.2."
  [service-options]
  (-> service-options
      (assoc :SupportedInputProjections (seq (map #(hash-map :ProjectionName %)
                                                  (:SupportedProjections service-options))))
      (assoc :SupportedOutputProjections (seq (map #(hash-map :ProjectionName %)
                                                   (:SupportedProjections service-options))))
      (dissoc :SupportedProjections)))

(defn- duplicate-supported-formats
  "Duplicate the SupportedFormats in v1.1 ServiceOptions to SupportedInputFormats
  and SupportedOutputFormats in v1.2 ServiceOptions when migrate from 1.1 to 1.2."
  [service-options]
  (-> service-options
      (assoc :SupportedInputFormats (:SupportedFormats service-options))
      (assoc :SupportedOutputFormats (:SupportedFormats service-options))
      (dissoc :SupportedFormats)))

(def v1-1-supported-formats-enum->v1-2-supported-formats-enum
  "Defines SupportedFormats ENUM changes from v1.1 to v1.2"
  {"HDF4" "HDF4"
   "HDF5" "HDF5"
   "HDF-EOS4" "HDF-EOS2"
   "HDF-EOS5" "HDF-EOS5"
   "netCDF-3" "NETCDF-3"
   "netCDF-4" "NETCDF-4"
   "Binary" "BINARY"
   "ASCII" "ASCII"
   "PNG" "PNG"
   "JPEG" "JPEG"
   "GeoTIFF" "GEOTIFF"
   "image/png" "PNG"
   "image/tiff" "TIFF"
   "image/gif" "GIF"
   "image/png; mode=24bit" "PNG24"
   "image/jpeg" "JPEG"
   "image/vnd.wap.wbmp" "BMP"})

(def v1-2-supported-formats-enum->v1-1-supported-formats-enum
  "Defines SupportedFormats ENUM changes from v1.2 to v1.1"
  (-> v1-1-supported-formats-enum->v1-2-supported-formats-enum
      set/map-invert
      (assoc "HDF-EOS" "HDF-EOS5")))

(defn- supported-Formats-1-1->1-2
  "Migrate SupportedFormats from vector of 1.1 enums to vector of 1.2 enums."
  [supported-formats]
  (let [supported-formats (->> supported-formats
                               (map #(get v1-1-supported-formats-enum->v1-2-supported-formats-enum %))
                               (remove nil?))]
    (when (seq supported-formats)
      (vec supported-formats))))

(defn- v1-1-supported-formats->v1-2-supported-formats
  "Migrate v1.1 supported formats in ServiceOptions to v1.2"
  [service-options]
  (-> service-options
      (update :SupportedFormats supported-Formats-1-1->1-2)
      duplicate-supported-formats))

(defn- revert-supported-projections
  "Revert the SupportedInputProjections and SupportedOutputProjections in v1.2 ServiceOptions
  to SupportedProjections in v1.1 ServiceOptions when migrate from 1.2 to 1.1."
  [service-options]
  (-> service-options
      (assoc :SupportedProjections (seq (map :ProjectionName
                                             (:SupportedInputProjections service-options))))
      (dissoc :SupportedInputProjections :SupportedOutputProjections)))

(defn- supported-Formats-1-2->1-1
  "Migrate SupportedFormats from vector of 1.2 enums to vector of 1.1 enums."
  [supported-formats]
  (let [supported-formats (->> supported-formats
                               (map #(get v1-2-supported-formats-enum->v1-1-supported-formats-enum %))
                               (remove nil?))]
    (when (seq supported-formats)
      (vec supported-formats))))

(defn- revert-supported-formats
  "Revert the SupportedInputFormats and SupportedOutputFormats in v1.2 ServiceOptions
  to SupportedFormats in v1.1 ServiceOptions when migrate from 1.2 to 1.1."
  [service-options]
  (-> service-options
      (assoc :SupportedFormats (:SupportedInputFormats service-options))
      (dissoc :SupportedInputFormats :SupportedOutputFormats)))

(defn- v1-2-supported-formats->v1-1-supported-formats
  "Migrate v1.2 supported formats in ServiceOptions to v1.1"
  [service-options]
  (-> service-options
      revert-supported-formats
      (update :SupportedFormats supported-Formats-1-2->1-1)))

(defn- v1-1-service-options->v1-2-service-options
  "Migrate v1.1 ServiceOptions to v1.2"
  [service-options]
  (-> service-options
      duplicate-supported-projections
      v1-1-supported-formats->v1-2-supported-formats))

(defn- v1-2-service-options->v1-1-service-options
  "Migrate v1.2 ServiceOptions to v1.1"
  [service-options]
  (-> service-options
      revert-supported-projections
      v1-2-supported-formats->v1-1-supported-formats
      (dissoc :VariableAggregationSupportedMethods :MaxGranules)))

(defn- fix-contact-info
  "Drops Uuid and NonServiceOrganizationAffiliation fields from
  the given contact list when migrating from v1.1 to v1.2"
  [contacts]
  (mapv #(dissoc % :Uuid :NonServiceOrganizationAffiliation) contacts))

(defn- fix-contacts
  "Drops Uuid and NonServiceOrganizationAffiliation fields from the given field key
  (can be either :ContactGroups or :ContactPersons) when migrating from v1.1 to v1.2"
  [s field-key]
  (if-let [contacts (field-key s)]
    (assoc s field-key (fix-contact-info contacts))
    s))

(defn- v1-2-type->v1-1-type
  "Migrate v1.2 Type to v1.1 Type, i.e. change ESI and ECHO ORDERS to WEB SERVICES"
  [service-type]
  (if (some #{"ESI" "ECHO ORDERS"} [service-type])
    "WEB SERVICES"
    service-type))

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

(defmethod interface/migrate-umm-version [:service "1.1" "1.2"]
  [context s & _]
  (-> s
      (update :ServiceOptions v1-1-service-options->v1-2-service-options)
      (fix-contacts :ContactGroups)
      (fix-contacts :ContactPersons)
      (util/update-in-each [:ServiceOrganizations] #(fix-contacts % :ContactGroups))
      (util/update-in-each [:ServiceOrganizations] #(fix-contacts % :ContactPersons))
      (dissoc :OnlineAccessURLPatternMatch :OnlineAccessURLPatternSubstitution :Coverage)
      util/remove-empty-maps))

(defmethod interface/migrate-umm-version [:service "1.2" "1.1"]
  [context s & _]
  (-> s
      (update :ServiceOptions v1-2-service-options->v1-1-service-options)
      (update :Type v1-2-type->v1-1-type)
      (update :LongName #(util/trunc % 120))
      (dissoc :OperationMetadata)
      util/remove-empty-maps))
