(ns cmr.umm-spec.validation.umm-spec-collection-validation
  "Defines validations for UMM collections."
  (:require
   [cmr.common.validations.core :as v]
   [cmr.umm-spec.validation.additional-attribute :as aa]
   [cmr.umm-spec.validation.data-date :as data-date]
   [cmr.umm-spec.validation.platform :as p]
   [cmr.umm-spec.validation.coll-project :as project]
   [cmr.umm-spec.validation.related-url :as url]
   [cmr.umm-spec.validation.spatial :as s]
   [cmr.umm-spec.validation.temporal-extent :as temporal-extent]
   [cmr.umm-spec.validation.umm-spec-validation-utils :as vu]))

(defn- metadata-association-name
  "Returns the unique name of metadata association for reporting purpose"
  [ma]
  (format "(EntryId [%s] & Version [%s])" (:EntryId ma) (:Version ma)))

(defn- coordinate-validator
  "Validates coordinate, minimum must be less than the maximum"
  [field-path value]
  (let [{:keys [MinimumValue MaximumValue]} value]
    (when (and MinimumValue MaximumValue (> MinimumValue MaximumValue))
      {field-path [(format "%%s minimum [%s] must be less than or equal to the maximum [%s]."
                           (str MinimumValue) (str MaximumValue))]})))

(defn doi-format-warning-validation
  "Validates that DOI is properly formatted."
  [field-path value]
  (when (seq value)
    (when-not (re-matches #"\d\d\.\d\d\d\d(\.\d+)*\/.+" value)
      {field-path [(format "DOI [%s] is improperly formatted." value)]})))

(def tiling-identification-system-coordinate-validations
  "Defines the tiling identification system coordinate validations for collections"
  {:Coordinate1 coordinate-validator
   :Coordinate2 coordinate-validator})

(def tiling-identification-system-validations
  "Defines the tiling identification system validations for collections"
  [(vu/unique-by-name-validator :TilingIdentificationSystemName)
   (v/every tiling-identification-system-coordinate-validations)])

(def science-keyword-validations
  "Defines the science keyword validations for collections"
  {:Category v/field-cannot-be-blank
   :Topic v/field-cannot-be-blank
   :Term v/field-cannot-be-blank})

(def collection-validations
  "Defines validations for collections"
  {:TemporalExtents (v/every temporal-extent/temporal-extent-validation)
   :Platforms p/platforms-validation
   :AdditionalAttributes aa/additional-attribute-validation
   :Projects project/projects-validation
   :ScienceKeywords (v/every science-keyword-validations)
   :SpatialExtent s/spatial-extent-validation
   :MetadataAssociations (vu/unique-by-name-validator metadata-association-name)
   :TilingIdentificationSystems tiling-identification-system-validations
   :DirectDistributionInformation {:S3BucketAndObjectPrefixNames (v/every url/s3-bucket-validation)}})

(def collection-validation-warnings
 "Defines validations for collections that we want to return as warnings and not
 as failures"
 {:RelatedUrls (v/every url/related-url-validations)
  :Projects project/projects-warning-validation
  :TemporalExtents (v/every temporal-extent/temporal-extent-warning-validation)
  :CollectionCitations (v/every {:OnlineResource {:Linkage url/url-validation}})
  :PublicationReferences (v/every {:OnlineResource {:Linkage url/url-validation}})
  :DataCenters (v/every url/data-center-url-validation)
  :ContactPersons (v/every url/contact-persons-groups-contact-information-validations)
  :ContactGroups (v/every url/contact-persons-groups-contact-information-validations)
  :DataDates data-date/data-dates-warning-validation
  :MetadataDates data-date/data-dates-warning-validation
  :DOI {:DOI doi-format-warning-validation}
  :DirectDistributionInformation {:S3CredentialsAPIEndpoint url/url-validation
                                  :S3CredentialsAPIDocumentationURL url/url-validation}})
