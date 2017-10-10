(ns cmr.umm-spec.migration.version.collection
  "Contains functions for migrating between versions of the UMM Collection schema."
  (:require
   [clojure.set :as set]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.util :as util :refer [update-in-each]]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.migration.collection-progress-migration :as coll-progress-migration]
   [cmr.umm-spec.migration.contact-information-migration :as ci]
   [cmr.umm-spec.migration.distance-units-migration :as distance-units-migration]
   [cmr.umm-spec.migration.geographic-coordinate-units-migration :as geographic-coordinate-units-migration]
   [cmr.umm-spec.migration.organization-personnel-migration :as op]
   [cmr.umm-spec.migration.related-url-migration :as related-url]
   [cmr.umm-spec.migration.spatial-extent-migration :as spatial-extent]
   [cmr.umm-spec.migration.version.interface :as interface]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :refer [versions current-version]]
   [cmr.umm-spec.xml-to-umm-mappings.characteristics-data-type-normalization :as char-data-type-normalization]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions

(def not-provided-organization
  "Place holder to use when an organization is not provided."
  {:Role "RESOURCEPROVIDER"
   :Party {:OrganizationName {:ShortName u/not-provided}}})

(def valid-iso-topic-categories
  "Valid values for ISOTopicCategory as defined in UMM JSON schema"
  #{"farming" "biota" "boundaries" "climatologyMeteorologyAtmosphere" "economy" "elevation"
    "environment" "geoscientificInformation" "health" "imageryBaseMapsEarthCover"
    "intelligenceMilitary" "inlandWaters" "location" "oceans" "planningCadastre" "society"
    "structure" "transportation" "utilitiesCommunication"})

(defn- get-iso-topic-category
  "Returns the UMM iso topic category or the default value of location if it is not a known
  iso topic category"
  [iso-topic-category]
  (get valid-iso-topic-categories iso-topic-category "location"))

(defn- update-attribute-description
  "If description is nil, set to default of 'Not provided'"
  [attribute]
  (if (nil? (:Description attribute))
     (assoc attribute :Description u/not-provided)
     attribute))

(defn- migrate-doi-up
  "Migrate :DOI from CollectionCitation level up to collection level."
  [c]
  (if-let [doi-obj (some :DOI (:CollectionCitations c))]
    (-> c
      (update-in-each [:CollectionCitations] dissoc :DOI)
      (assoc :DOI doi-obj))
    c))

(defn- migrate-doi-down
  "Migrate :DOI from collection level down to CollectionCitation level."
  [c]
  (if-let [doi-obj (:DOI c)]
    (-> c
      (update-in-each [:CollectionCitations] assoc :DOI doi-obj)
      (dissoc :DOI))
    c))

(defn- add-related-urls
  "Add required RelatedUrls in version 1.8 if missing in version 1.9"
  [c]
  (if (seq (:RelatedUrls c))
    c
    (assoc c :RelatedUrls [u/not-provided-related-url])))

(defn- migrate-sensor-to-instrument
 "Migrate from 1.8 to 1.9 sensors to ComposedOf list of instrument child types on
 the instrument"
 [instrument]
 (-> instrument
     (assoc :ComposedOf (:Sensors instrument))
     (assoc :NumberOfInstruments (:NumberOfSensors instrument))
     (dissoc :Sensors)
     (dissoc :NumberOfSensors)))

(defn- migrate-instrument-to-sensor
 "Migrate from 1.9 to 1.8 child instruments to sensors "
 [instrument]
 (-> instrument
     (assoc :Sensors (:ComposedOf instrument))
     (assoc :NumberOfSensors (:NumberOfInstruments instrument))
     (dissoc :ComposedOf)
     (dissoc :NumberOfInstruments)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Collection Migration Implementations

(defmethod interface/migrate-umm-version [:collection "1.0" "1.1"]
  [context c & _]
  (-> c
      (update-in [:TilingIdentificationSystem] #(when % [%]))
      (set/rename-keys {:TilingIdentificationSystem :TilingIdentificationSystems})))

(defmethod interface/migrate-umm-version [:collection "1.1" "1.0"]
  [context c & _]
  (-> c
      (update-in [:TilingIdentificationSystems] first)
      (set/rename-keys {:TilingIdentificationSystems :TilingIdentificationSystem})))

(defmethod interface/migrate-umm-version [:collection "1.1" "1.2"]
  [context c & _]
  ;; Change SpatialKeywords to LocationKeywords
  (-> c
      (assoc :LocationKeywords (lk/translate-spatial-keywords (kf/get-kms-index context)
                                                              (:SpatialKeywords c)))))

(defmethod interface/migrate-umm-version [:collection "1.2" "1.1"]
  [context c & _]
  ;;Assume that IsoTopicCategories will not deviate from the 1.1 list of allowed values.
  (-> c
      (assoc :SpatialKeywords
             (or (seq (lk/location-keywords->spatial-keywords (:LocationKeywords c)))
                 (:SpatialKeywords c)
                 ;; Spatial keywords are required
                 [u/not-provided]))
      (assoc :LocationKeywords nil)))

(defmethod interface/migrate-umm-version [:collection "1.2" "1.3"]
  [context c & _]
  (-> c
      (update-in [:PaleoTemporalCoverage] #(when % [%]))
      (set/rename-keys {:PaleoTemporalCoverage :PaleoTemporalCoverages})))

(defmethod interface/migrate-umm-version [:collection "1.3" "1.2"]
  [context c & _]
  (-> c
      (update-in [:PaleoTemporalCoverages] first)
      (set/rename-keys {:PaleoTemporalCoverages :PaleoTemporalCoverage})))

(defmethod interface/migrate-umm-version [:collection "1.3" "1.4"]
  [context c & _]
  (-> c
      (assoc :DataCenters (op/organizations->data-centers (:Organizations c)))
      (assoc :ContactPersons (op/personnel->contact-persons (:Personnel c)))
      (dissoc :Organizations :Personnel)))

(defmethod interface/migrate-umm-version [:collection "1.4" "1.3"]
  [context c & _]
  (-> c
      (assoc :Organizations (op/data-centers->organizations (:DataCenters c)))
      (assoc :Personnel (op/contact-persons->personnel (:ContactPersons c)))
      (dissoc :DataCenters :ContactGroups :ContactPersons)))

(defmethod interface/migrate-umm-version [:collection "1.4" "1.5"]
  [context c & _]
  (-> c
    ;; If an Additional Attribute has no description, set the description
    ;; to the default "Not provided"
    (update-in [:AdditionalAttributes] #(mapv update-attribute-description %))))

(defmethod interface/migrate-umm-version [:collection "1.5" "1.4"]
  [context c & _]
  ;; Don't need to migrate Additional Attribute description back since 'Not provided' is valid
  c)

(defmethod interface/migrate-umm-version [:collection "1.5" "1.6"]
  [context c & _]
  (-> c
    (update-in [:DataCenters] #(mapv ci/update-data-center-contact-info %))
    (update-in [:ContactPersons] #(mapv ci/first-contact-info %))
    (update-in [:ContactGroups] #(mapv ci/first-contact-info %))))

(defmethod interface/migrate-umm-version [:collection "1.6" "1.5"]
  [context c & _]
  (-> c
      (update-in [:DataCenters] #(mapv ci/update-data-center-contact-info-to-array %))
      (update-in [:ContactPersons] #(mapv ci/contact-info-to-array %))
      (update-in [:ContactGroups] #(mapv ci/contact-info-to-array %))))

(defmethod interface/migrate-umm-version [:collection "1.6" "1.7"]
  [context c & _]
  ;; migration removed CMR-4718
  c)

(defmethod interface/migrate-umm-version [:collection "1.7" "1.6"]
  [context c & _]
  ;; Don't need to migrate ISOTopicCategories
  c)

(defmethod interface/migrate-umm-version [:collection "1.7" "1.8"]
  [context c & _]
  (-> c
     (update :CollectionProgress u/with-default)))

(defmethod interface/migrate-umm-version [:collection "1.8" "1.7"]
  [context c & _]
  (-> c
    (dissoc :VersionDescription)))

(defmethod interface/migrate-umm-version [:collection "1.8" "1.9"]
  [context c & _]
  (-> c
      migrate-doi-up
      related-url/dissoc-titles-from-contact-information
      (update :RelatedUrls related-url/array-of-urls->url)
      (update-in-each [:RelatedUrls] related-url/relation->url-content-type)
      (update-in-each [:PublicationReferences] related-url/migrate-related-url-to-online-resource)
      (update-in-each [:CollectionCitations] related-url/migrate-related-url-to-online-resource)
      (update-in-each [:RelatedUrls] related-url/migrate-url-content-types-up)
      (update :DataCenters related-url/migrate-data-centers-up)
      (update :ContactGroups related-url/migrate-contacts-up)
      (update :ContactPersons related-url/migrate-contacts-up)
      (update-in-each [:Platforms] update-in-each [:Instruments] migrate-sensor-to-instrument)
      (update-in [:SpatialExtent] spatial-extent/remove-center-point)))

(defmethod interface/migrate-umm-version [:collection "1.9" "1.8"]
  [context c & _]
  (-> c
      migrate-doi-down
      related-url/migrate-down-from-1_9
      (update-in-each [:PublicationReferences] related-url/migrate-online-resource-to-related-url)
      (update-in-each [:CollectionCitations] related-url/migrate-online-resource-to-related-url)
      (update-in-each [:Platforms] update-in-each [:Instruments] migrate-instrument-to-sensor)
      add-related-urls))

(defmethod interface/migrate-umm-version [:collection "1.9" "1.10"]
  [context c & _]
  (-> c
      (update :TilingIdentificationSystems spatial-conversion/filter-and-translate-tiling-id-systems)
      coll-progress-migration/migrate-up
      (update-in-each [:TemporalExtents] dissoc :TemporalRangeType)
      (update-in [:SpatialInformation :VerticalCoordinateSystem :AltitudeSystemDefinition] dissoc :EncodingMethod)
      (update-in [:SpatialInformation :VerticalCoordinateSystem :DepthSystemDefinition] dissoc :EncodingMethod)
      geographic-coordinate-units-migration/migrate-geographic-coordinate-units-to-enum
      distance-units-migration/migrate-distance-units-to-enum
      ;; Remove the possible empty maps after setting geographic coordinate units and/or distance-units to nil.
      util/remove-empty-maps
      (update-in [:SpatialExtent :VerticalSpatialDomains] spatial-conversion/drop-invalid-vertical-spatial-domains)
      char-data-type-normalization/migrate-up))

(defmethod interface/migrate-umm-version [:collection "1.10" "1.9"]
  [context c & _]
  (-> c
      coll-progress-migration/migrate-down))
