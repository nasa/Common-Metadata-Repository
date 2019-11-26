(ns cmr.umm-spec.migration.version.granule
  "Contains functions for migrating between versions of the UMM-G schema."
  (:require
   [cmr.common.util :as util]
   [cmr.umm-spec.migration.version.interface :as interface]))

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

(defn- safe-subs
  "If `end` is beyond the end of a string, use the actual length of the string instead"
  [string begin end]
  (when string
    (if (> (count string) end)
      (subs string begin end)
      (subs string begin (count string)))))

(defn- truncate-filename-type
  "Migrate v1.6 FileNameType to v1.5 by truncating FilePackageType/Name and
  FileType/Name fields to 80 characters or less"
  [g]
  (map (fn [id]
         (-> id
             (assoc :Identifier (safe-subs (:Identifier id) 0 79))
             (assoc :IdentifierName (safe-subs (:IdentifierName id) 0 79))))
       g))

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
