(ns cmr.umm-spec.migration.version.collection
  "Contains functions for migrating between versions of the UMM Collection schema."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.util :as util :refer [update-in-each remove-nil-keys]]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.collection-progress-migration :as coll-progress-migration]
   [cmr.umm-spec.migration.contact-information-migration :as ci]
   [cmr.umm-spec.migration.distance-units-migration :as distance-units-migration]
   [cmr.umm-spec.migration.doi-migration :as doi]
   [cmr.umm-spec.migration.distributions-migration :as distributions-migration]
   [cmr.umm-spec.migration.geographic-coordinate-units-migration :as geographic-coordinate-units-migration]
   [cmr.umm-spec.migration.organization-personnel-migration :as op]
   [cmr.umm-spec.migration.related-url-migration :as related-url]
   [cmr.umm-spec.migration.spatial-extent-migration :as spatial-extent]
   [cmr.umm-spec.migration.version.interface :as interface]
   [cmr.umm-spec.models.umm-collection-models :as umm-coll-models]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :refer [versions current-version]]
   [cmr.umm-spec.xml-to-umm-mappings.characteristics-data-type-normalization :as char-data-type-normalization]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions

(def kms-format-lowercase-to-umm-c-enum-mapping
  "Mapping from lower-case of KMS granule-data-format short_name values to enum list of values
  for RelatedUrls/GetData/Format in UMM-C 1.15.4 schema. For those values that don't exist in
  the enum list, they will be mapped to \"Not provided\""
  {"ascii" "ascii"
   "binary" "binary"
   "bufr" "BUFR"
   "hdf4" "HDF4"
   "hdf5" "HDF5"
   "hdf-eos4" "HDF-EOS4"
   "hdf-eos5" "HDF-EOS5"
   "jpeg" "jpeg"
   "png" "png"
   "tiff" "tiff"
   "geotiff" "geotiff"
   "kml" "kml"})

(def kms-mimetype-to-umm-c-enum-mapping
  "Mapping from KMS mime-type values to enum list of values
  for RelatedUrls/GetData/MimeType in UMM-C 1.17.0 schema. For those values that don't exist in
  this mapping, they will be mapped to nil and removed."
  {"application/json" "application/json"
   "application/xml" "application/xml"
   "application/x-netcdf" "application/x-netcdf"
   "application/gml+xml" "application/gml+xml"
   "application/opensearchdescription+xml" "application/opensearchdescription+xml"
   "application/vnd.google-earth.kml+xml" "application/vnd.google-earth.kml+xml"
   "image/gif" "image/gif"
   "image/tiff" "image/tiff"
   "image/bmp" "image/bmp"
   "text/csv" "text/csv"
   "text/xml" "text/xml"
   "application/pdf" "application/pdf"
   "application/x-hdf" "application/x-hdf"
   "application/x-hdf5" "application/xhdf5"
   "application/octet-stream" "application/octet-stream"
   "application/vnd.google-earth.kmz" "application/vnd.google-earth.kmz"
   "image/jpeg" "image/jpeg"
   "image/png" "image/png"
   "image/vnd.collada+xml" "image/vnd.collada+xml"
   "text/html" "text/html"
   "text/plain" "text/plain"})

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

(defn- remove-format-descriptions
  "Remove FormatDescription from the passed in file information maps.
   This is for 1.15.3 -> 1.15.2 in :ArchiveAndDistributionInformation :FileArchiveInformation and
   :FileDistributionInformation."
  [file-informations]
  (map #(dissoc % :FormatDescription) file-informations))

(defn- drop-military-grid-reference-system
  "Drop TilingIdentificationSystems with name Military Grid Reference System."
  [tiling-identification-systems]
  (->> tiling-identification-systems
       (remove #(= "Military Grid Reference System" (:TilingIdentificationSystemName %)))
       seq))

(defn- replace-invalid-format
  "Replace GetData Formats in RelatedUrls that are not present in the 1.15.4 schema."
  [getdata]
  (if (:Format getdata)
    (update getdata :Format #(get kms-format-lowercase-to-umm-c-enum-mapping
                                  (string/lower-case %)
                                  "Not provided"))
    getdata))

(defn- migrate-mimetype-up
  "Migrate 'application/xhdf5' to 'application/x-hdf5' and
  remove MimeType from element whose value is 'Not provided'"
  [element]
  (if (:MimeType element)
    (-> element
        (update :MimeType #(get (set/map-invert kms-mimetype-to-umm-c-enum-mapping) %))
        (remove-nil-keys))
    element))

(defn- migrate-mimetype-down
  "Migrate 'application/x-hdf5' to 'application/xhdf5' and
  remove MimeType from element whose values don't exist in URLMimeTypeEnum."
  [element]
  (if (:MimeType element)
    (-> element
        (update :MimeType #(get kms-mimetype-to-umm-c-enum-mapping %))
        (remove-nil-keys))
    element))

(defn- migrate-OrbitParameters-up
  "Add in the assumed units. The Period element needs to be changed to OrbitPeriod.
  There are four units added in 1.17.0. Three of them are associated with the required
  fields in 1.16.7. StartCircularLatitudeUnit is added only if StartCircularLatitude
  exists."
  [collection]
  (if-let [orbit-period (get-in collection [:SpatialExtent :OrbitParameters :Period])]
    (let [StartCircularLatitude (get-in collection [:SpatialExtent :OrbitParameters :StartCircularLatitude])
          collection (-> collection
                         (update-in [:SpatialExtent :OrbitParameters] dissoc :Period)
                         (update-in [:SpatialExtent :OrbitParameters] assoc :SwathWidthUnit "Kilometer"
                                    :OrbitPeriodUnit "Decimal Minute"
                                    :InclinationAngleUnit "Degree"
                                    :OrbitPeriod orbit-period))]
      (if StartCircularLatitude
        (assoc-in collection [:SpatialExtent :OrbitParameters :StartCircularLatitudeUnit] "Degree")
        collection))
    collection))

(defn- get-largest-footprint-in-kilometer
  "Convert all foot-prints to Kilometer, return the largest value."
  [foot-prints]
  (let [foot-prints-in-kilometer (map #(if (= "Meter" (:FootprintUnit %))
                                         (double (/ (:Footprint %) 1000))
                                         (:Footprint %))
                                      foot-prints)]
    (when (seq foot-prints)
      (apply max foot-prints-in-kilometer))))

(defn get-swath-width
  "Get the correct value for collection's SwathWidth in v1.16.7 from v1.17.0.
  If SwathWidth exists in v1.17.0, convert it to the value in Kilometer.
  Otherwise, convert all Footprints to Kilometer, get the largest value and use it
  for SwathWidth."
  [collection]
  (let [swath-width-unit (get-in collection [:SpatialExtent :OrbitParameters :SwathWidthUnit])
        swath-width (get-in collection [:SpatialExtent :OrbitParameters :SwathWidth])
        swath-width (if (and swath-width (= "Meter" swath-width-unit))
                      (double (/ swath-width 1000))
                      swath-width)]
    (if swath-width
      swath-width
      ;; if SwathWidth doesn't exist, Footprints is required.
      (get-largest-footprint-in-kilometer (get-in collection [:SpatialExtent :OrbitParameters :Footprints])))))

(defn- migrate-OrbitParameters-down
  "Remove Footprints element; rename OrbitPeriod to Period; remove all the units;
  convert SwathWidth to the value in assumed unit; If SwathWidth doesn't exist,
  convert largest Footprint to SwathWidth."
  [collection]
  (if-let [period (get-in collection [:SpatialExtent :OrbitParameters :OrbitPeriod])]
    (let [swath-width (get-swath-width collection)]
      (-> collection
          (update-in [:SpatialExtent :OrbitParameters] dissoc :Footprints :OrbitPeriod :SwathWidthUnit :OrbitPeriodUnit
                                                              :InclinationAngleUnit :StartCircularLatitudeUnit)
          (update-in [:SpatialExtent :OrbitParameters] assoc :SwathWidth swath-width :Period period)))
    collection))

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
      (update-in [:DataCenters] #(seq (mapv ci/update-data-center-contact-info %)))
      (update-in [:ContactPersons] #(seq (mapv ci/first-contact-info %)))
      (update-in [:ContactGroups] #(seq (mapv ci/first-contact-info %)))))

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
      doi/migrate-doi-up
      (update :RelatedUrls related-url/updating-main-related-urls-for-1-9)
      (update-in-each [:PublicationReferences] related-url/migrate-related-url-to-online-resource)
      (update-in-each [:CollectionCitations] related-url/migrate-related-url-to-online-resource)
      (update :DataCenters related-url/migrate-data-centers-up)
      (update :ContactGroups related-url/migrate-contacts-up)
      (update :ContactPersons related-url/migrate-contacts-up)
      (update-in-each [:Platforms] update-in-each [:Instruments] migrate-sensor-to-instrument)
      (update-in [:SpatialExtent] spatial-extent/remove-center-point)))

(defmethod interface/migrate-umm-version [:collection "1.9" "1.8"]
  [context c & _]
  (-> c
      doi/migrate-doi-down
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
      util/remove-nils-empty-maps-seqs
      (update-in [:SpatialExtent :VerticalSpatialDomains] spatial-conversion/drop-invalid-vertical-spatial-domains)
      char-data-type-normalization/migrate-up
      doi/migrate-missing-reason-up
      (update-in-each [:PublicationReferences] related-url/migrate-online-resource-up)
      (update-in-each [:CollectionCitations] related-url/migrate-online-resource-up)
      ;; Can't do (assoc :UseConstraints (when-let [description (:UseConstraints c)]... because when :UseConstraints
      ;; is nil, it will be turned into (:Description nil :LIcenseUrl nil :LicenseText nil) which will fail validation.
      (as-> coll (if-let [description (:UseConstraints c)]
                   (assoc coll :UseConstraints
                               {:Description (umm-coll-models/map->UseConstraintsDescriptionType
                                               {:Description description})})
                   coll))))

(defmethod interface/migrate-umm-version [:collection "1.10" "1.9"]
  [context c & _]
  (-> c
      coll-progress-migration/migrate-down
      (util/update-in-all [:RelatedUrls :GetData] dissoc :MimeType)
      (util/update-in-all [:RelatedUrls :GetService] dissoc :Format)
      doi/migrate-missing-reason-down
      (update-in-each [:PublicationReferences] related-url/migrate-online-resource-down)
      (update-in-each [:CollectionCitations] related-url/migrate-online-resource-down)
      (assoc :UseConstraints (when-let [description (get-in c [:UseConstraints :Description])]
                               ;; Description in 1.10 is object/record.
                               ;; It needs to be converted to string when becoming UseConstraints in 1.9.i
                               (:Description description)))))

(defmethod interface/migrate-umm-version [:collection "1.10" "1.11"]
  [context c & _]
  (related-url/migrate-up-to-1_11 c))

(defmethod interface/migrate-umm-version [:collection "1.11" "1.10"]
  [context c & _]
  (-> c
      related-url/migrate-down-from-1_11
      doi/migrate-doi-down-to-1_10))

(defmethod interface/migrate-umm-version [:collection "1.11" "1.12"]
  [context c & _]
  ;; since only related url keywords were added to 1.12 the mapping from 1.11 doesn't change to 1.12
  c)

(defmethod interface/migrate-umm-version [:collection "1.12" "1.11"]
  [context c & _]
  (related-url/migrate-down-from-1_12 c))

(defmethod interface/migrate-umm-version [:collection "1.12" "1.13"]
  [context c & _]
  (distributions-migration/migrate-up-to-1_13 c))

(defmethod interface/migrate-umm-version [:collection "1.13" "1.12"]
  [context c & _]
  (distributions-migration/migrate-down-to-1_12 c))

(defmethod interface/migrate-umm-version [:collection "1.13" "1.14"]
  [context c & _]
  (spatial-extent/migrate-up-to-1_14 c))

(defmethod interface/migrate-umm-version [:collection "1.14" "1.13"]
  [context c & _]
  (spatial-extent/migrate-down-to-1_13 c))

(defmethod interface/migrate-umm-version [:collection "1.14" "1.15"]
  [context c & _]
  (spatial-extent/migrate-up-to-1_15 c))

(defmethod interface/migrate-umm-version [:collection "1.15" "1.14"]
  [context c & _]
  (spatial-extent/migrate-down-to-1_14 c))

(defmethod interface/migrate-umm-version [:collection "1.15" "1.15.1"]
  [context c & _]
  ;; Don't need to migrate Collection Progress - we just added a new enumeration value.
  c)

(defmethod interface/migrate-umm-version [:collection "1.15.1" "1.15"]
  [context c & _]
  (coll-progress-migration/migrate-down-to-1_15 c))

(defmethod interface/migrate-umm-version [:collection "1.15.1" "1.15.2"]
  [context c & _]
  (spatial-extent/migrate-up-to-1_15_2 c))

(defmethod interface/migrate-umm-version [:collection "1.15.2" "1.15.1"]
  [context c & _]
  (spatial-extent/migrate-down-to-1_15_1 c))

(defmethod interface/migrate-umm-version [:collection "1.15.2" "1.15.3"]
  [context c & _]
  ;; Don't need to migrate anyting - FormatDescription was added to FileArchiveInformation
  ;; and FileDistributionInformation.]
  c)

(defmethod interface/migrate-umm-version [:collection "1.15.3" "1.15.2"]
  [context c & _]
  (-> c
      (update-in [:ArchiveAndDistributionInformation :FileArchiveInformation] remove-format-descriptions)
      (update-in [:ArchiveAndDistributionInformation :FileDistributionInformation] remove-format-descriptions)))

(defmethod interface/migrate-umm-version [:collection "1.15.3" "1.15.4"]
  [context c & _]
  ;; No need to migrate - "Military Grid Reference System" was added to TilingIdentificationSystemNameEnum
  c)

(defmethod interface/migrate-umm-version [:collection "1.15.4" "1.15.3"]
  [context c & _]
  (update c :TilingIdentificationSystems drop-military-grid-reference-system))

(defmethod interface/migrate-umm-version [:collection "1.15.4" "1.15.5"]
  [context c & _]
  ;; No need to migrate
  c)

(defmethod interface/migrate-umm-version [:collection "1.15.5" "1.15.4"]
  [context c & _]
  ;; Need to replace the RelatedURLs/GetData/Format with "Not provided"
  ;; if it's not part of the enum list in 1.15.4, case insensitive.
  (util/update-in-all c [:RelatedUrls :GetData] replace-invalid-format))

(defmethod interface/migrate-umm-version [:collection "1.15.5" "1.16"]
  [context c & _]
  ;; No need to migrate
  c)

(defmethod interface/migrate-umm-version [:collection "1.16" "1.15.5"]
  [context c & _]
  (dissoc c :DirectDistributionInformation))

(defmethod interface/migrate-umm-version [:collection "1.16" "1.16.1"]
  [context c & _]
  (-> c
      (update :DOI doi/migrate-doi-up-to-1-16-1)
      (update :PublicationReferences doi/migrate-pub-ref-up-to-1-16-1)
      util/remove-nils-empty-maps-seqs))


(defmethod interface/migrate-umm-version [:collection "1.16.1" "1.16"]
  [context c & _]
  (-> c
      (update :DOI doi/migrate-doi-down-to-1-16)
      (dissoc :AssociatedDOIs)))

(defmethod interface/migrate-umm-version [:collection "1.16.1" "1.16.2"]
  [context c & _]
  ;; Can't use the same solution as used in 1.16.2 -> 1.16.1 using update because when :UseConstraints
  ;; is nil, it will be turned into (:Description nil :LIcenseUrl nil :LicenseText nil) which will
  ;; fail validation. This is the same issue going from 1.9 -> 1.10 above.
  (let [use-constraints (-> c
                            :UseConstraints
                            (assoc :Description (get-in c [:UseConstraints :Description :Description]))
                            (set/rename-keys {:LicenseUrl :LicenseURL})
                            util/remove-nils-empty-maps-seqs)]
     (if use-constraints
       (assoc c :UseConstraints use-constraints)
       (dissoc c :UseConstraints))))

(defmethod interface/migrate-umm-version [:collection "1.16.2" "1.16.1"]
  [context c & _]
  (update c :UseConstraints (fn [use-constraints]
                              (-> use-constraints
                                  (assoc :Description {:Description (get use-constraints :Description)})
                                  (set/rename-keys {:LicenseURL :LicenseUrl})
                                  util/remove-nils-empty-maps-seqs))))

;; Migrations related to 1.16.3 --------------

(defmethod interface/migrate-umm-version [:collection "1.16.2" "1.16.3"]
  [context c & _]
  ;; No need to migrate
  c)

(defmethod interface/migrate-umm-version [:collection "1.16.3" "1.16.2"]
  [context c & _]
  ;; Remove the related urls that contain GET CAPABILITIES as the type as it is not valid in the
  ;; lower versions.
  (-> c
      (update :RelatedUrls (fn [related-urls]
                             (into []
                               (remove #(= "GET CAPABILITIES" (:Type %)) related-urls))))
      util/remove-nils-empty-maps-seqs))

;; Migrations related to 1.16.4 --------------

(defmethod interface/migrate-umm-version [:collection "1.16.3" "1.16.4"]
  [context collection & _]
  ;; No need to migrate
  collection)

(defmethod interface/migrate-umm-version [:collection "1.16.4" "1.16.5"]
  [context collection & _]
  ;; No need to migrate
  collection)

(defn- remove-1-16-4-urls
  [related-urls]
  (let [sans ["HITIDE", "SOTO", "Sub-Orbital Order Tool", "CERES Ordering Tool"]]
    (into [] (remove #(some? (some #{(:Subtype %)} sans)) related-urls))))

(defmethod interface/migrate-umm-version [:collection "1.16.4" "1.16.3"]
  [context collection & _]
  ;; Remove the related urls that sub-types that were not valid in the lower versions.
  (-> collection
      (update :RelatedUrls remove-1-16-4-urls)
      util/remove-nils-empty-maps-seqs))

(defmethod interface/migrate-umm-version [:collection "1.16.5" "1.16.4"]
  [context collection & _]
  ;; No need to migrate
  collection)

(defmethod interface/migrate-umm-version [:collection "1.16.5" "1.16.6"]
  [context collection & _]
  ;; No need to migrate
  collection)

(defmethod interface/migrate-umm-version [:collection "1.16.6" "1.16.5"]
  [context collection & _]
  ;; Remove the FreeAndOpenData field in UseConstraints.
  (update-in collection [:UseConstraints] dissoc :FreeAndOpenData))

(defmethod interface/migrate-umm-version [:collection "1.16.6" "1.16.7"]
  [context collection & _]
  ;; No need to migrate
  collection)

(defmethod interface/migrate-umm-version [:collection "1.16.7" "1.16.6"]
  [context collection & _]
  ;; Change CollectionDataType to "NEAR_REAL_TIME" if its value is "LOW_LATENCY" or "EXPEDITED"
  (let [CollectionDataType (:CollectionDataType collection)]
    (if (or (= "LOW_LATENCY" CollectionDataType) (= "EXPEDITED" CollectionDataType))
      (assoc collection :CollectionDataType "NEAR_REAL_TIME")
      collection)))

(defmethod interface/migrate-umm-version [:collection "1.16.7" "1.17.0"]
  [context collection & _]
  ;; MetadataSpecification - add in the field and the proper enumeration values.
  ;; OrbitParameters - add in the assumed units. The Period element needs to be changed to OrbitPeriod.
  (-> collection
      (m-spec/update-version :collection "1.17.0")
      (migrate-OrbitParameters-up)))

(defmethod interface/migrate-umm-version [:collection "1.17.0" "1.16.7"]
  [context collection & _]
  ;; Remove MetadataSpecification and StandardProduct.
  ;; OrbitParameters - remove Footprints element; rename OrbitPeriod to Period;
  ;;                   remove all the units; convert SwathWidth to the value in assumed unit;
  ;;                   if SwathWidth doesn't exist, convert largest Footprint to SwathWidth.
  (-> collection
      (dissoc :MetadataSpecification :StandardProduct)
      (migrate-OrbitParameters-down)))

(defmethod interface/migrate-umm-version [:collection "1.17.0" "1.17.1"]
  [context collection & _]
  ;; Migrate "application/xhdf5" to "application/x-hdf5" and
  ;; remove the MimeType with value of "Not provided".
  (-> collection
      (m-spec/update-version :collection "1.17.1")
      (util/update-in-all [:RelatedUrls :GetData] migrate-mimetype-up)
      (util/update-in-all [:DataCenters :ContactInformation :RelatedUrls :GetData] migrate-mimetype-up)
      (util/update-in-all [:DataCenters :ContactPersons :ContactInformation :RelatedUrls :GetData] migrate-mimetype-up)
      (util/update-in-all [:DataCenters :ContactGroups :ContactInformation :RelatedUrls :GetData] migrate-mimetype-up)
      (util/update-in-all [:ContactPersons :ContactInformation :RelatedUrls :GetData] migrate-mimetype-up)
      (util/update-in-all [:ContactGroups :ContactInformation :RelatedUrls :GetData] migrate-mimetype-up)
      (util/update-in-all [:UseConstraints :LicenseURL] migrate-mimetype-up)
      (util/update-in-all [:CollectionCitations :OnlineResource] migrate-mimetype-up)
      (util/update-in-all [:PublicationReferences :OnlineResource] migrate-mimetype-up)))

(defmethod interface/migrate-umm-version [:collection "1.17.1" "1.17.0"]
  [context collection & _]
  ;; Migrate "application/x-hdf5" to "application/xhdf5" and
  ;; remove MimeType with other values that don't exist in URLMimeTypeEnum  
  (-> collection
      (m-spec/update-version :collection "1.17.0")
      (util/update-in-all [:RelatedUrls :GetData] migrate-mimetype-down)
      (util/update-in-all [:DataCenters :ContactInformation :RelatedUrls :GetData] migrate-mimetype-down)
      (util/update-in-all [:DataCenters :ContactPersons :ContactInformation :RelatedUrls :GetData] migrate-mimetype-down)
      (util/update-in-all [:DataCenters :ContactGroups :ContactInformation :RelatedUrls :GetData] migrate-mimetype-down)
      (util/update-in-all [:ContactPersons :ContactInformation :RelatedUrls :GetData] migrate-mimetype-down)
      (util/update-in-all [:ContactGroups :ContactInformation :RelatedUrls :GetData] migrate-mimetype-down)
      (util/update-in-all [:UseConstraints :LicenseURL] migrate-mimetype-down)
      (util/update-in-all [:CollectionCitations :OnlineResource] migrate-mimetype-down)
      (util/update-in-all [:PublicationReferences :OnlineResource] migrate-mimetype-down)))
