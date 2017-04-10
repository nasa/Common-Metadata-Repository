(ns cmr.umm-spec.validation.umm-spec-collection-validation
  "Defines validations for UMM collections."
  (:require
   [clj-time.core :as t]
   [cmr.common.validations.core :as v]
   [cmr.umm-spec.validation.additional-attribute :as aa]
   [cmr.umm-spec.validation.platform :as p]
   [cmr.umm-spec.validation.related-url :as url]
   [cmr.umm-spec.validation.spatial :as s]
   [cmr.umm.validation.validation-utils :as vu]))

(defn- range-date-time-validation
  "Defines range-date-time validation"
  [field-path value]
  (let [{:keys [BeginningDateTime EndingDateTime]} value]
    (when (and BeginningDateTime EndingDateTime (t/after? BeginningDateTime EndingDateTime))
      {field-path [(format "BeginningDateTime [%s] must be no later than EndingDateTime [%s]"
                           (str BeginningDateTime) (str EndingDateTime))]})))

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

(def tiling-identification-system-coordinate-validations
  "Defines the tiling identification system coordinate validations for collections"
  {:Coordinate1 coordinate-validator
   :Coordinate2 coordinate-validator})

(def tiling-identification-system-validations
  "Defines the tiling identification system validations for collections"
  [(vu/unique-by-name-validator :TilingIdentificationSystemName)
   (v/every tiling-identification-system-coordinate-validations)])


(def temporal-extent-validation
  {:RangeDateTimes (v/every range-date-time-validation)})

(def science-keyword-validations
  "Defines the science keyword validations for collections"
  {:Category v/required
   :Topic v/required
   :Term v/required})

(def collection-validations
  "Defines validations for collections"
  {:TemporalExtents (v/every temporal-extent-validation)
   :Platforms p/platforms-validation
   :AdditionalAttributes aa/additional-attribute-validation
   :Projects (vu/unique-by-name-validator :ShortName)
   :ScienceKeywords (v/every science-keyword-validations)
   :SpatialExtent s/spatial-extent-validation
   :MetadataAssociations (vu/unique-by-name-validator metadata-association-name)
   :TilingIdentificationSystems tiling-identification-system-validations})

(def collection-validation-warnings
 "Defines validations for collections that we want to return as warnings and not
 as failures"
 {:RelatedUrls (v/every url/related-url-validations)
  :CollectionCitations (v/every {:OnlineResource {:Linkage url/url-validation}})
  :PublicationReferences (v/every {:OnlineResource {:Linkage url/url-validation}})
  :DataCenters (v/every url/data-center-url-validation)
  :ContactPersons (v/every url/contact-persons-groups-contact-information-validations)
  :ContactGroups (v/every url/contact-persons-groups-contact-information-validations)})
