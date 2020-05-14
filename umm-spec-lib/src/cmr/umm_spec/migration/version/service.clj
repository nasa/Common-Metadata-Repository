(ns cmr.umm-spec.migration.version.service
  "Contains functions for migrating between versions of the UMM Service schema."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
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

(defn create-main-url-for-1_3
  "When migrating from UMM-S v1.2 to UMM-S v1.3 verison 1.2 RelatedURLs needs to be
   converted to the new URL element.  Take the first DistributionURL element from
   RelatedURLs and convert it to the new URL type."
  [s]
  (let [url (->> (:RelatedURLs s)
                 (filter #(= (:URLContentType %) "DistributionURL"))
                 first)]
    (when url
      {:Description (:Description url)
       :URLContentType (:URLContentType url)
       :Type (:Type url)
       :Subtype (:Subtype url)
       :URLValue (:URL url)})))

(defn create-main-related-urls-for-1_2
  "Migrating from UMM-S v1.3 to UMM-S v1.2 RelatedURLs."
  [s]
  (let [url (:URL s)]
    (when url
      [{:Description (:Description url)
        :URLContentType (:URLContentType url)
        :Type (:Type url)
        :Subtype (:Subtype url)
        :URL (:URLValue url)}])))

(defn create-online-resource
  "Create an online resource structure for service organization staring in version 1.3.
   Since Description is required in 1.3, if it doesn't exist then add the words Not provided
   as the description."
  [service-org]
  (let [url (->> (:ContactInformation service-org)
                 (:RelatedUrls)
                 (filter #(= (:URLContentType %) "DataCenterURL"))
                 (first))]
    (when url
      (util/remove-nil-keys
        {:Linkage (:URL url)
         :Description (if (:Description url)
                        (:Description url)
                        "Not provided")
         :Name "HOME PAGE"}))))

(defn add-online-resource
  "This function takes in a service organization and calls create-online-resource to generate
   an online resource from the old RelatedURLs element. If RelatedURLs doesn't exist or a
   DataCenterURL doesn't exist then "
  [service-org]
  (let [online-resource (create-online-resource service-org)]
    (if online-resource
      (assoc service-org :OnlineResource online-resource)
      service-org)))

(defn update-service-organization-1_2->1_3
  "Take the passed in edn service record and Update the service organization by moving the
   ServiceOrganizations ContactPersons and ContactGroups to the main level ContactPersons and
   ContactGroups. Return the altered map record."
  [s]
  (let [service-orgs (:ServiceOrganizations s)
        service-org-contact-groups (map :ContactGroups service-orgs)
        service-org-contact-persons (map :ContactPersons service-orgs)
        service-orgs (map #(add-online-resource %) service-orgs)
        service-orgs (map #(dissoc % :ContactGroups :ContactPersons :ContactInformation) service-orgs)]
    (-> s
        (update :ContactGroups #(seq (apply concat % service-org-contact-groups)))
        (update :ContactPersons #(seq (apply concat % service-org-contact-persons)))
        (assoc :ServiceOrganizations (seq service-orgs)))))

(defn add-related-url
  "If OnlineResource exists in the passed in service organization then convert it to service 1.2
   RelatedUrls and then remove the OnlineResource element. Otherwise just pass back the passed
   in service organization."
  [service-org]
  (let [online-resource (:OnlineResource service-org)]
    (if online-resource
      (-> service-org
          (assoc :ContactInformation {:RelatedUrls [{:URLContentType "DataCenterURL"
                                                     :Type "HOME PAGE"
                                                     :Description (:Description online-resource)
                                                     :URL (:Linkage online-resource)}]})
          (dissoc :OnlineResource))
      service-org)))

(defn update-service-organization-1_3->1_2
  "Loop through the service orgainizations and call add-related-url to convert the OnlineResource
   to RelatedUrls and remove OnlineResource if it exists."
  [s]
  (map #(add-related-url %) (:ServiceOrganizations s)))

(defn update-service-type-1_3->1_2
  "Update the UMM-S 1.2 service type to WEB SERVICES if the UMM-S version 1.3
   service type = EGI - No Processing."
  [s]
  (case (:Type s)
    "EGI - No Processing" (assoc s :Type "WEB SERVICES")
    "WMTS" (assoc s :Type "WMS")
    s))

(def CRSIdentifierTypeEnum-service-1_2
  "These are the valid enum values in UMM-S 1.2. These are used to migrate ServiceOptions
   SupportedInputProjections/SupportedOutputProjections ProjectionAuthority string in version 1.3 to 1.2."
  ["4326", "3395", "3785", "9807", "2000.63", "2163", "3408", "3410", "6931",
   "6933", "3411", "9822", "54003", "54004", "54008", "54009", "26917", "900913"])

(defn check-projection-valid-values
  "For a projection check to see if the ProjectionAuthority is a valid value for UMM-S 1.2.
   If it is then use as is, if not them remove the invalid value. Return a Valid UMM-S version
   1.2 SupportedProjectionType object."
  [projection]
  (if (some #(= (:ProjectionAuthority projection) %) CRSIdentifierTypeEnum-service-1_2)
    projection
    (dissoc projection :ProjectionAuthority)))

(defn remove-non-valid-formats
  "Remove the formats that are not valid for UMM-S version 1.2."
  [formats]
  (remove #(= "ZARR" %) formats))

(defn update-projections
  "Iterate through each projection calling the check-projection-valid-values function
   checking to see if the ProjectionAuthority is valid. Return a list of Valid UMM-S version 1.2
   SupportedProjectionType objects."
  [projections]
  (map #(check-projection-valid-values %) projections))

(defn update-service-options-1_3->1_2
  "Update the service options from the passed in 1.3 UMM-S record to a valid UMM-S version 1.2 record."
  [s]
  (-> s
      (update-in [:ServiceOptions :SupportedInputProjections] update-projections)
      (update-in [:ServiceOptions :SupportedOutputProjections] update-projections)
      (update :ServiceOptions dissoc :SupportedReformattings)
      (update-in [:ServiceOptions :SupportedInputFormats] remove-non-valid-formats)
      (update-in [:ServiceOptions :SupportedOutputFormats] remove-non-valid-formats)))

(defn remove-non-valid-operation-name
  "Remove the operation metadata if the OperationName is GetTile. This is not a valid option
   in version 1.2."
  [operation-metadata]
  (if (= "GetTile" (:OperationName operation-metadata))
    nil
    operation-metadata))

(defn update-crs-identifier-1_3->1_2
  "Update the CRSIdentifier by removing EPSC: from the 1.3 enumeration to match the 1.2 enumeration.
   return nil for anything else since it isn't valid."
  [identifier]
  (when-let [id (:CRSIdentifier identifier)]
    (if (string/includes? id "EPSG:")
      (update identifier :CRSIdentifier #(string/replace % "EPSG:" ""))
      nil)))

(defn update-operation-metadata-1_3->1_2
  "Migrate the operation metadata from version 1.3 to version 1.2."
  [operation-metadata]
  (-> operation-metadata
      (remove-non-valid-operation-name)
      (update-in [:CoupledResource :DataResource :DataResourceSpatialExtent
                  :SpatialBoundingBox] update-crs-identifier-1_3->1_2)
      (update-in [:CoupledResource :DataResource :DataResourceSpatialExtent
                  :GeneralGridType] update-crs-identifier-1_3->1_2)
      (util/remove-nil-keys)
      (util/remove-empty-maps)))

(defn update-crs-identifier-1_2->1_3
  "Updates the CRSIdentifier from version 1.2 enumerations to the 1.3 version."
  [identifier]
  (when (:CRSIdentifier identifier)
    (update identifier :CRSIdentifier #(str "EPSG:" %))))

(defn update-operation-metadata-1_2->1_3
  "Migrate the operation metadata from version 1.2 to version 1.3."
  [operation-metadata]
  (-> operation-metadata
      (update-in [:CoupledResource :DataResource :DataResourceSpatialExtent
                  :SpatialBoundingBox] update-crs-identifier-1_2->1_3)
      (update-in [:CoupledResource :DataResource :DataResourceSpatialExtent
                  :GeneralGridType] update-crs-identifier-1_2->1_3)
      (util/remove-nil-keys)
      (util/remove-empty-maps)))

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

(defmethod interface/migrate-umm-version [:service "1.2" "1.3"]
  [context s & _]
  (let [url (create-main-url-for-1_3 s)
        op-metadata (map #(update-operation-metadata-1_2->1_3 %) (:OperationMetadata s))]
    (-> s
        (update :UseConstraints #(assoc {} :LicenseText %))
        (update-service-organization-1_2->1_3)
        (assoc :OperationMetadata op-metadata)
        (assoc :URL url)
        (dissoc :RelatedURLs
                :ScienceKeywords
                :Platforms))))

(defmethod interface/migrate-umm-version [:service "1.3" "1.2"]
  [context s & _]
  (let [url (create-main-related-urls-for-1_2 s)
        service-orgs (update-service-organization-1_3->1_2 s)
        op-metadata (remove nil?
                      (map #(update-operation-metadata-1_3->1_2 %) (:OperationMetadata s)))]
    (-> s
        (update-service-type-1_3->1_2)
        (update :UseConstraints #(get % :LicenseText))
        (assoc :RelatedURLs url)
        (assoc :ServiceOrganizations service-orgs)
        (update-service-options-1_3->1_2)
        (assoc :OperationMetadata op-metadata)
        (dissoc :URL
                :LastUpdatedDate
                :VersionDescription))))
