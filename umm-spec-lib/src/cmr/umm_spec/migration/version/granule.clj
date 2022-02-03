(ns cmr.umm-spec.migration.version.granule
  "Contains functions for migrating between versions of the UMM-G schema."
  (:require
   [cmr.common.util :as util]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.version.interface :as interface]))

(def ^:private v1-5-identifier-name-max-length
  "Max length allowed for `Identifiers` fields in v1.5"
  80)

(def ^:private v1-4-url-subtype-enum->v1-5-url-subtype-enum
  "Defines RelatedUrlSubTypeEnum that needs to be changed from v1.4 to v1.5"
  {"ALGORITHM THEORETICAL BASIS DOCUMENT" "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
   "USER FEEDBACK" "USER FEEDBACK PAGE"})

(def ^:private v1-5-url-subtype-enum->v1-4-url-subtype-enum
  "Defines RelatedUrlSubTypeEnum that needs to be changed from v1.5 to v1.4"
  {"GoLIVE Portal" "PORTAL"
   "IceBridge Portal" "PORTAL"
   "Order" nil
   "Subscribe" nil
   "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)" "ALGORITHM THEORETICAL BASIS DOCUMENT"
   "USER FEEDBACK PAGE" "USER FEEDBACK"})

(def ^:private v1-5-to-1-4-changed-url-subtype-enums
  "Defines a set of v1.5 RelatedUrlSubTypeEnums that need to be changed when migrate to v1.4"
  (set (keys v1-5-url-subtype-enum->v1-4-url-subtype-enum)))

(def ^:private v1-6-3-data-format-enums
  "These are the values that were in the DataFormatEnum definition which was
   changed to a string of length 1-80 and validated against KMS."
  ["ASCII" "BINARY" "BMP" "BUFR" "CSV" "GEOTIFF" "GIF" "GEOTIFFINT16"
   "GEOTIFFFLOAT32" "GRIB" "GZIP" "HDF4" "HDF5" "HDF-EOS2" "HDF-EOS5" "HTML"
   "ICARTT" "JPEG" "JSON" "KML" "NETCDF-3" "NETCDF-4" "NETCDF-CF" "PNG" "PNG24"
   "TAR" "TIFF" "XLSX" "XML" "ZIP" "DMRPP" "Not provided"]
)

(defn- v1-4-related-url-subtype->v1-5-related-url-subtype
  "Migrate v1.4 related url Subtype to v1.5 related url Subtype"
  [related-url]
  (if-let [changed-sub-type (v1-4-url-subtype-enum->v1-5-url-subtype-enum (:Subtype related-url))]
    (assoc related-url :Subtype changed-sub-type)
    related-url))

(defn- v1-5-related-url-subtype->v1-4-related-url-subtype
  "Migrate v1.5 related url Subtype to v1.4 related url Subtype"
  [related-url]
  (if (v1-5-to-1-4-changed-url-subtype-enums (:Subtype related-url))
    (->> related-url
         :Subtype
         v1-5-url-subtype-enum->v1-4-url-subtype-enum
         (assoc related-url :Subtype)
         util/remove-nil-keys)
    related-url))

(defn- v1-4-mime-type->v1-5-mime-type
  "Migrate v1.4 MimeType to v1.5 MimeType, i.e. application/xhdf5 is changed to application/x-hdf5"
  [mime-type]
  (if (= "application/xhdf5" mime-type)
    "application/x-hdf5"
    mime-type))

(defn- v1-5-mime-type->v1-4-mime-type
  "Migrate v1.5 MimeType to v1.4 MimeType, i.e. application/x-hdf5 is changed to application/xhdf5"
  [mime-type]
  (if (= "application/x-hdf5" mime-type)
    "application/xhdf5"
    mime-type))

(defn- dissoc-track
  "Migrate v1.5 Track to v1.4 by dissociating it from HorizontalSpatialDomain if applicable"
  [g]
  (if (get-in g [:SpatialExtent :HorizontalSpatialDomain :Track])
    (update-in g [:SpatialExtent :HorizontalSpatialDomain] dissoc :Track)
    g))

(defn- dissoc-size-in-bytes
  "Migrate v1.6 ArchiveAndDistributionInformation to v1.5 by removing
  SizeInBytes if applicable"
  [g]
  (-> g
      (util/update-in-all [:ArchiveAndDistributionInformation]
                          dissoc
                          :SizeInBytes)

      (util/update-in-all [:ArchiveAndDistributionInformation :FilePackageType]
                          dissoc
                          :SizeInBytes)

      (util/update-in-all [:ArchiveAndDistributionInformation :FileType]
                          dissoc
                          :SizeInBytes)
      (util/update-in-all [:ArchiveAndDistributionInformation :Files]
                          dissoc
                          :SizeInBytes)))

(defn- truncate-filename-type
  "Migrate v1.6 FileNameType to v1.5 by truncating FilePackageType/Name and
  FileType/Name fields to 80 characters or less"
  [g]
  (map (fn [id]
         (-> id
             (assoc :IdentifierName (util/trunc (:IdentifierName id) v1-5-identifier-name-max-length))
             (assoc :Identifier (util/trunc (:Identifier id) v1-5-identifier-name-max-length))
             util/remove-nil-keys))
       g))

(defn downgrade-format-and-mimetype-to-1-6
  "This function takes a map and downgrades the DMRPP format and
   mime-type to version 1.6. If the metadata contains :Files then it is a nested map and so call
   this function again to iterate over its maps.
   An updated map is returned."
  [archive-and-dist-info-file]
  (-> archive-and-dist-info-file
      (update :Format #(if (= "DMRPP" %)
                          "Not provided"
                          %))
      (update :MimeType #(if (= "application/vnd.opendap.dap4.dmrpp+xml" %)
                           "Not provided"
                           %))
      (update :Files (fn [files]
                       (seq (map #(downgrade-format-and-mimetype-to-1-6 %) files))))
      (util/remove-nil-keys)))

(defn downgrade-formats-and-mimetypes-to-1-6
  "This function takes a list of maps that contain the fields called Formats and MimeTypes and it
   iterates through them to downgrade DMRPP formats and mime-types to version 1.6. A list of updated
   maps is returned."
  [list-of-maps]
  (seq (map #(downgrade-format-and-mimetype-to-1-6 %) list-of-maps)))

(defn v1-6-2-related-url-type->1-6-1
  "Migrate v1.6.2 related url Type to v1.6.1 related url Type"
  [related-url]
  (if (= "GET DATA VIA DIRECT ACCESS" (:Type related-url))
    (assoc related-url :Type "GET DATA")
    related-url))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;;; Granule Migration Implementations

(defmethod interface/migrate-umm-version [:granule "1.4" "1.5"]
  [context g & _]
  (-> g
      (assoc :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.5"
                                     :Name "UMM-G"
                                     :Version "1.5"})
      (util/update-in-each [:RelatedUrls] v1-4-related-url-subtype->v1-5-related-url-subtype)
      (util/update-in-all [:RelatedUrls :MimeType] v1-4-mime-type->v1-5-mime-type)
      (util/update-in-all [:DataGranule :ArchiveAndDistributionInformation :MimeType]
                          v1-4-mime-type->v1-5-mime-type)
      (util/update-in-all [:DataGranule :ArchiveAndDistributionInformation :Files :MimeType]
                          v1-4-mime-type->v1-5-mime-type)
      util/remove-empty-maps))

(defmethod interface/migrate-umm-version [:granule "1.5" "1.4"]
  [context g & _]
  (-> g
      (dissoc :MetadataSpecification)
      dissoc-track
      (util/update-in-each [:RelatedUrls] v1-5-related-url-subtype->v1-4-related-url-subtype)
      (util/update-in-all [:RelatedUrls :MimeType] v1-5-mime-type->v1-4-mime-type)
      (util/update-in-all [:DataGranule :ArchiveAndDistributionInformation :MimeType]
                          v1-5-mime-type->v1-4-mime-type)
      (util/update-in-all [:DataGranule :ArchiveAndDistributionInformation :Files :MimeType]
                          v1-5-mime-type->v1-4-mime-type)
      util/remove-empty-maps))

(defmethod interface/migrate-umm-version [:granule "1.6" "1.5"]
  [context g & _]
  (-> g
      (assoc :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.5"
                                     :Name "UMM-G"
                                     :Version "1.5"})
      (update :DataGranule dissoc-size-in-bytes)
      (update-in [:DataGranule :Identifiers] truncate-filename-type)))

(defmethod interface/migrate-umm-version [:granule "1.5" "1.6"]
  [context g & _]
  (-> g
      (assoc :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6"
                                     :Name "UMM-G"
                                     :Version "1.6"})))

(defmethod interface/migrate-umm-version [:granule "1.6.1" "1.6"]
  [context g & _]
  (-> g
      (assoc :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6"
                                     :Name "UMM-G"
                                     :Version "1.6"})
      (update-in [:DataGranule :ArchiveAndDistributionInformation] downgrade-formats-and-mimetypes-to-1-6)
      (update :RelatedUrls downgrade-formats-and-mimetypes-to-1-6)))

(defmethod interface/migrate-umm-version [:granule "1.6" "1.6.1"]
  [context g & _]
  (-> g
      (assoc :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.1"
                                     :Name "UMM-G"
                                     :Version "1.6.1"})))

(defmethod interface/migrate-umm-version [:granule "1.6.2" "1.6.1"]
  [context g & _]
  (-> g
      (assoc :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.1"
                                     :Name "UMM-G"
                                     :Version "1.6.1"})
      (util/update-in-each [:RelatedUrls] v1-6-2-related-url-type->1-6-1)))

(defmethod interface/migrate-umm-version [:granule "1.6.1" "1.6.2"]
  [context g & _]
  (-> g
      (assoc :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.2"
                                     :Name "UMM-G"
                                     :Version "1.6.2"})))

(defn- drop-1-6-3-related-urls
  "Drop the urls specific to v1.6.3"
  [related-urls]
  (remove #(and (= "EXTENDED METADATA" (:Type %))
                (some #{(:Subtype %)} ["DMR++" "DMR++ MISSING DATA"]))
          related-urls))

;; v1.6.3 migrations

(defmethod interface/migrate-umm-version [:granule "1.6.3" "1.6.2"]
  [context g & _]
  (-> g
      (m-spec/update-version :granule "1.6.2")
      (update :RelatedUrls drop-1-6-3-related-urls)))

(defmethod interface/migrate-umm-version [:granule "1.6.2" "1.6.3"]
  [context g & _]
  (-> g
      (m-spec/update-version :granule "1.6.3")))

;; v1.6.4 migrations

(defn- map-1-6-4-format-keywords
  "Collections 1.15.5->1.15.4 did not drop URLs with old formats but instead
  moved the value to 'Not provided'. Doing the same here. This function will
  take either an ArchiveAndDistributionInformation or a RelatedUrls"
  [data]
  (if (some #{(:Format data)} v1-6-3-data-format-enums)
    data
    (assoc data :Format "Not provided")))

(defmethod interface/migrate-umm-version [:granule "1.6.4" "1.6.3"]
  ;; When downgrading metadata, preserve formats which are on the old SCHEMA list
  ;; and set the others to 'Not provided'"
  [context granule & _]
  (-> granule
      (util/update-in-each-vector [:RelatedUrls] map-1-6-4-format-keywords)
      (util/update-in-each-vector [:DataGranule :ArchiveAndDistributionInformation]
                                  map-1-6-4-format-keywords)
      (m-spec/update-version :granule "1.6.3")))

(defmethod interface/migrate-umm-version [:granule "1.6.3" "1.6.4"]
  [context granule & _]
  (-> granule
      (m-spec/update-version :granule "1.6.4")))
