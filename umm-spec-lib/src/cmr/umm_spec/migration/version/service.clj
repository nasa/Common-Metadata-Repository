(ns cmr.umm-spec.migration.version.service
  "Contains functions for migrating between versions of the UMM Service schema."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.service-service-options :as service-options]
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
      (-> url
          (assoc :URLValue (:URL url))
          (dissoc :URL :GetData :GetService)))))

(defn create-main-related-urls-for-1_2
  "Migrating from UMM-S v1.3 to UMM-S v1.2 RelatedURLs."
  [s]
  (let [url (:URL s)]
    (when url
      [(-> url
           (assoc :URL (:URLValue url))
           (dissoc :URLValue))])))

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

(defn remove-get-data-service
  "Remove GetData and GetService from all of the passed in RelatedUrls."
  [related-urls]
  (when related-urls
    (map #(dissoc % :GetData :GetService) related-urls)))

(defn remove-get-data-service-1-2->1-3
  "For the passed in single ContactGroup or ContactPerson, remove GetData and GetService from the
   ContactPersons/ContactGroups ContactInformation RelatedUrls."
  [contact]
  (if (:RelatedUrls (:ContactInformation contact))
    (update-in contact [:ContactInformation :RelatedUrls] #(remove-get-data-service %))
    contact))

(defn remove-related-url-get-data-service-1-2->1-3
  "Iterate over the passed in vector of ContactGroups or ContactPersons to ultimately remove the
   ContactInformations RelatedUrls GetData and GetService sub elements."
  [vector-of-contacts]
  (map #(remove-get-data-service-1-2->1-3 %) vector-of-contacts))

(defn update-service-organization-1_2->1_3
  "Take the passed in edn service record and Update the service organization by moving the
   ServiceOrganizations ContactPersons and ContactGroups to the main level ContactPersons and
   ContactGroups. Then once the contact groups and contact persons are moved, then remove the
   ContactInformation RelatedUrls GetData and GetService sub elements. Return the altered map record."
  [s]
  (let [service-orgs (:ServiceOrganizations s)
        service-org-contact-groups (map :ContactGroups service-orgs)
        service-org-contact-persons (map :ContactPersons service-orgs)
        service-orgs (->> service-orgs
                          (map #(add-online-resource %))
                          (map #(dissoc % :ContactGroups
                                          :ContactPersons
                                          :ContactInformation)))]
    (-> s
        (update :ContactGroups #(seq (apply concat % service-org-contact-groups)))
        (update :ContactGroups #(seq (remove-related-url-get-data-service-1-2->1-3 %)))
        (update :ContactPersons #(seq (apply concat % service-org-contact-persons)))
        (update :ContactPersons #(seq (remove-related-url-get-data-service-1-2->1-3 %)))
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
      (update-in [:CoupledResource
                  :DataResource
                  :DataResourceSpatialExtent
                  :SpatialBoundingBox]
                 update-crs-identifier-1_3->1_2)
      (update-in [:CoupledResource
                  :DataResource
                  :DataResourceSpatialExtent
                  :GeneralGridType]
                 update-crs-identifier-1_3->1_2)
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
      (update-in [:CoupledResource
                  :DataResource
                  :DataResourceSpatialExtent
                  :SpatialBoundingBox]
                 update-crs-identifier-1_2->1_3)
      (update-in [:CoupledResource
                  :DataResource
                  :DataResourceSpatialExtent
                  :GeneralGridType]
                 update-crs-identifier-1_2->1_3)
      (util/remove-nil-keys)
      (util/remove-empty-maps)))

(defn update-url-with-default-values
  "If a value exists then pass it back.  If not, given the passed in key (k) pass back the default
   value."
  [k value]
  (if value
    value
    (case k
      :URLContentType "DistributionURL"
      :Type "GET SERVICE"
      :URLValue "Not provided")))

(defn update-url-1_3->1_3_1
  "UMM-S version 1.3 these values are not required, but they are in UMM-S version 1.3.1.
   Make sure the URL has the required keys. If not then provide the default values."
  [url]
  (-> url
      (update :URLContentType #(update-url-with-default-values :URLContentType %))
      (update :Type #(update-url-with-default-values :Type %))
      (update :URLValue #(update-url-with-default-values :URLValue %))))

(defn update-related-url-to-online-resource
  "Takes a related-url and returns an Online Resource map with a couple of fields copied over."
  [related-url]
  {:Description (:Description related-url)
   :Name (:Type related-url)
   :Linkage (:URL related-url)})

(defn update-contact-info-1_3_1->1_3_2
  "For each contact info change the RelatedUrl map to an OnlineResource map."
  [contact-info]
  (let [online-resources (seq (map #(update-related-url-to-online-resource %)
                                   (:RelatedUrls contact-info)))]
    (-> contact-info
        (assoc :OnlineResources online-resources)
        (dissoc :RelatedUrls)
        (util/remove-nil-keys))))

(defn update-contacts-1_3_1->1_3_2
  "In ContactGroups and in ContactPersons change the ContactInformation/RelatedURL to OnlineResource."
  [contacts]
  (map #(update % :ContactInformation update-contact-info-1_3_1->1_3_2) contacts))

(defn update-online-resource-to-related-url
  "Takes an OnlineResource and returns a RelatedUrl map with a couple of fields copied over."
  [online-resource]
  {:Description (:Description online-resource)
   :Type "PROJECT HOME PAGE"
   :URLContentType "CollectionURL"
   :URL (:Linkage online-resource)})

(defn update-contact-info-1_3_2->1_3_1
  "For each contact info change the OnlineResources map to a RelatedUrl map."
  [contact-info]
  (let [related-urls (seq (map #(update-online-resource-to-related-url %)
                               (:OnlineResources contact-info)))]
    (-> contact-info
        (assoc :RelatedUrls related-urls)
        (dissoc :OnlineResources)
        (util/remove-nil-keys))))

(defn update-contacts-1_3_2->1_3_1
  "In ContactGroups and in ContactPersons change the ContactInformation/OnlineResource to RelatedUrl."
  [contacts]
  (map #(update % :ContactInformation update-contact-info-1_3_2->1_3_1) contacts))

(def kms-related-url-entries
  "RelatedURL entries in kms relevant for migration."
  {"CollectionURL" {"DATA SET LANDING PAGE" []
                    "EXTENDED METADATA" ["DMR++ MISSING DATA" "DMR++"]}
   "PublicationURL" {"VIEW RELATED INFORMATION"
                     ["ANOMALIES" "CASE STUDY" "DATA CITATION POLICY" "DATA PRODUCT SPECIFICATION"
                      "DATA QUALITY" "DATA RECIPE" "DELIVERABLES CHECKLIST" "GENERAL DOCUMENTATION"
                      "HOW-TO" "IMPORTANT NOTICE" "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"
                      "MICRO ARTICLE" "PI DOCUMENTATION" "PROCESSING HISTORY" "PRODUCT HISTORY"
                      "PRODUCT QUALITY ASSESSMENT" "PRODUCT USAGE" "PRODUCTION HISTORY" "PUBLICATIONS"
                      "READ-ME" "REQUIREMENTS AND DESIGN" "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"
                      "SCIENCE DATA PRODUCT VALIDATION" "USER FEEDBACK PAGE" "USER'S GUIDE"]}
   "VisualizationURL" {"Color Map" ["GITC" "Giovanni" "Harmony GDAL"]
                       "GET RELATED VISUALIZATION" ["GIOVANNI" "MAP" "SOTO" "WORLDVIEW"]}})

(defn- convert-related-url-1_4->1_4_1
  "Convert RelatedURL from 1.4 to 1.4.1"
  [related-url]
  ;; v1.4 RelatedURL: URLContentType is enum ["CollectionURL", "PublicationURL", "VisualizationURL"],
  ;; Type is string, Subtype is string.

  ;; The following are migrated to "PublicationURL" "VIEW RELATED INFORMATION"
  ;; [URLContentType in 1.4] [Type not in kms for the URLContentType]

  ;; The following values are migrated without Subtype
  ;; [URLContentType in 1.4] [Type in kms for the URLContentType]
  ;; [Subtype not in kms for the URLContentType and Type]
  (let [uct (:URLContentType related-url)
        t (:Type related-url)
        st (:Subtype related-url)]
    (if (not (some #(= t %) (keys (get kms-related-url-entries uct))))
      (-> related-url
          (assoc :URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION")
          (dissoc :Subtype))
      (if (and (some #(= t %) (keys (get kms-related-url-entries uct)))
               (not (some #(= st %) (get-in kms-related-url-entries [uct t]))))
        (dissoc related-url :Subtype)
        related-url))))

(defn- convert-related-url-1_4_1->1_4
  "Convert RelatedURL from 1.4.1 to 1.4"
  [related-url]
  ;; The following are migrated to "PublicationURL" "VIEW RELATED INFORMATION"
  ;; [URLContentType not in 1.4]
  (if (not (some #(= (:URLContentType related-url) %)
                 ["CollectionURL" "PublicationURL" "VisualizationURL"]))
    (-> related-url
        (assoc :URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION")
        (dissoc :Subtype))
    related-url))

(defn- migrate-related-urls-1_4->1_4_1
  "Migrate RelatedURLs from 1.4 to 1.4.1"
  [service]
  (if (:RelatedURLs service)
    (assoc service :RelatedURLs (map convert-related-url-1_4->1_4_1 (:RelatedURLs service)))
    service))

(defn- migrate-related-urls-1_4_1->1_4
  "Migrate RelatedURLs from 1.4.1 to 1.4"
  [service]
  (if (:RelatedURLs service)
    (assoc service :RelatedURLs (map convert-related-url-1_4_1->1_4 (:RelatedURLs service)))
    service))

(defn- migrate-related-urls-1_5_0->1_4_1
  "Migrate RelatedURLs from 1.5.0 to 1.4.1"
  [service]
  ;; Remove Format and MimeType from each entry in RelatedURLs.
  (if-let [related-urls (:RelatedURLs service)]
    (assoc service :RelatedURLs (map #(dissoc % :Format :MimeType) related-urls))
    service))   
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
      (update :ServiceOptions service-options/v1-1-service-options->v1-2-service-options)
      (fix-contacts :ContactGroups)
      (fix-contacts :ContactPersons)
      (util/update-in-each [:ServiceOrganizations] #(fix-contacts % :ContactGroups))
      (util/update-in-each [:ServiceOrganizations] #(fix-contacts % :ContactPersons))
      (dissoc :OnlineAccessURLPatternMatch :OnlineAccessURLPatternSubstitution :Coverage)
      util/remove-empty-maps))

(defmethod interface/migrate-umm-version [:service "1.2" "1.1"]
  [context s & _]
  (-> s
      (update :ServiceOptions service-options/v1-2-service-options->v1-1-service-options)
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
        (service-options/update-service-options-1_3->1_2)
        (assoc :OperationMetadata op-metadata)
        (dissoc :URL
                :LastUpdatedDate
                :VersionDescription))))

(defmethod interface/migrate-umm-version [:service "1.3" "1.3.1"]
  [context s & _]
  (-> s
      (update :URL update-url-1_3->1_3_1)
      (service-options/update-service-options-1_3->1_3_1)))

(defmethod interface/migrate-umm-version [:service "1.3.1" "1.3"]
  [context s & _]
  (service-options/update-service-options-1_3_1->1_3 s))

(defmethod interface/migrate-umm-version [:service "1.3.1" "1.3.2"]
  [context s & _]
  (-> s
      (update :URL #(dissoc % :URLContentType :Type :Subtype))
      (update :UseConstraints #(set/rename-keys % {:LicenseUrl :LicenseURL}))
      (update :ContactGroups update-contacts-1_3_1->1_3_2)
      (update :ContactPersons update-contacts-1_3_1->1_3_2)))

(defmethod interface/migrate-umm-version [:service "1.3.2" "1.3.1"]
  [context s & _]
  (-> s
      (update :URL #(assoc % :URLContentType "DistributionURL"
                             :Type "GET SERVICE"))
      (update :UseConstraints #(set/rename-keys % {:LicenseURL :LicenseUrl}))
      (update :ContactGroups update-contacts-1_3_2->1_3_1)
      (update :ContactPersons update-contacts-1_3_2->1_3_1)))

(defmethod interface/migrate-umm-version [:service "1.3.2" "1.3.3"]
  [context s & _]
  ;; There is nothing to migrate up. New supported format enumerations were added in 1.3.3.
  s)

(defmethod interface/migrate-umm-version [:service "1.3.3" "1.3.2"]
  [context s & _]
  (-> s
      (update-in [:ServiceOptions :SupportedInputFormats] service-options/remove-non-valid-formats-1_3_3-to-1_3_2)
      (update-in [:ServiceOptions :SupportedOutputFormats] service-options/remove-non-valid-formats-1_3_3-to-1_3_2)
      (update-in [:ServiceOptions :SupportedReformattings] service-options/remove-reformattings-non-valid-formats)))

(defmethod interface/migrate-umm-version [:service "1.3.3" "1.3.4"]
  [context s & _]
  (def s s)
  (let [type (:Type s)]
    (-> s
        (assoc-in [:ServiceOptions :Subset] (service-options/create-subset-type-1_3_3-to-1_3_4 s))
        (update-in [:ServiceOptions :SupportedReformattings]
                   #(service-options/move-supported-formats-to-reformattings-for-1_3_4
                     %
                     (get-in s [:ServiceOptions :SupportedInputFormats])
                     (get-in s [:ServiceOptions :SupportedOutputFormats])))
        (update-in [:ServiceOptions] dissoc :SubsetTypes :SupportedInputFormats :SupportedOutputFormats))))

(defmethod interface/migrate-umm-version [:service "1.3.4" "1.3.3"]
  [context s & _]
  (-> s
      (update :Type #(if (= % "Harmony")
                       "NOT PROVIDED"
                       %))
      (assoc-in [:ServiceOptions :SubsetTypes]
                (service-options/create-subset-type-1_3_4-to-1_3_3
                  (get-in s [:ServiceOptions :Subset])))
      (update-in [:ServiceOptions] dissoc :Subset)
      (update-in [:ServiceOptions :SupportedReformattings]
                 service-options/remove-reformattings-non-valid-formats-1_3_4-to-1_3_3)))

(defmethod interface/migrate-umm-version [:service "1.3.4" "1.4"]
  [context umm-s & _]
  (-> umm-s
      (m-spec/update-version :service "1.4")))

(defmethod interface/migrate-umm-version [:service "1.4" "1.3.4"]
  [context umm-s & _]
  (-> umm-s
      (dissoc :MetadataSpecification :RelatedURLs)))

(defmethod interface/migrate-umm-version [:service "1.4" "1.4.1"]
  [context umm-s & _]
  (-> umm-s
      (migrate-related-urls-1_4->1_4_1)
      (m-spec/update-version :service "1.4.1")))

(defmethod interface/migrate-umm-version [:service "1.4.1" "1.4"]
  [context umm-s & _]
  (-> umm-s
      (migrate-related-urls-1_4_1->1_4)
      (m-spec/update-version :service "1.4")))

(defmethod interface/migrate-umm-version [:service "1.4.1" "1.5.0"]
  [context umm-s & _]
  (m-spec/update-version umm-s :service "1.5.0"))

(defmethod interface/migrate-umm-version [:service "1.5.0" "1.4.1"]
  [context umm-s & _]
  (-> umm-s
      (migrate-related-urls-1_5_0->1_4_1)
      (m-spec/update-version :service "1.4.1")))
