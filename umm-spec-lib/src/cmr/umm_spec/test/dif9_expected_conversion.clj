(ns cmr.umm-spec.test.dif9-expected-conversion
 "DIF 9 specific expected conversion functionality"
 (:require
  [clj-time.core :as t]
  [clj-time.format :as f]
  [clojure.string :as string]
  [cmr.common.util :as util :refer [update-in-each]]
  [cmr.umm-spec.date-util :as date]
  [cmr.umm-spec.json-schema :as js]
  [cmr.umm-spec.location-keywords :as lk]
  [cmr.umm-spec.models.umm-collection-models :as umm-c]
  [cmr.umm-spec.models.umm-common-models :as cmn]
  [cmr.umm-spec.related-url :as ru-gen]
  [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
  [cmr.umm-spec.test.location-keywords-helper :as lkt]
  [cmr.umm-spec.umm-to-xml-mappings.dif9.data-center :as center]
  [cmr.umm-spec.umm-to-xml-mappings.dif9.data-contact :as contact]
  [cmr.umm-spec.umm-to-xml-mappings.dif9.spatial-extent :as spatial]
  [cmr.umm-spec.util :as su]))

(defn- single-date->range
  "Returns a RangeDateTimeType for a single date."
  [date]
  (cmn/map->RangeDateTimeType {:BeginningDateTime date
                               :EndingDateTime    date}))

(defn- dif9-temporal
  "Returns the expected value of a parsed DIF 9 UMM record's :TemporalExtents. All dates under
  SingleDateTimes are converted into ranges and concatenated with all ranges into a single
  TemporalExtentType."
  [temporal-extents]
  (let [singles (mapcat :SingleDateTimes temporal-extents)
        ranges (mapcat :RangeDateTimes temporal-extents)
        all-ranges (concat ranges
                           (map single-date->range singles))]
    (if (seq all-ranges)
      [(cmn/map->TemporalExtentType
         {:RangeDateTimes all-ranges})]
      su/not-provided-temporal-extents)))

(defn- expected-dif-instruments
  "Returns the expected DIF instruments for the given instruments"
  [instruments]
  (seq (map #(assoc % :Characteristics nil :Technique nil :NumberOfInstruments nil :ComposedOf nil
                    :OperationalModes nil) instruments)))

(defn- expected-dif-platform
  "Returns the expected DIF platform for the given platform"
  [platform]
  (-> platform
      (assoc :Type nil :Characteristics nil)
      (update-in [:Instruments] expected-dif-instruments)))

(defn- expected-dif-platforms
  "Returns the expected DIF parsed platforms for the given platforms."
  [platforms]
  (let [platforms (seq (map expected-dif-platform platforms))]
    (if (= 1 (count platforms))
      platforms
      (if-let [instruments (seq (mapcat :Instruments platforms))]
        (conj (map #(assoc % :Instruments nil) platforms)
              (cmn/map->PlatformType {:ShortName su/not-provided
                                      :Instruments instruments}))
        platforms))))

(defn- expected-dif-vertical-domains
  "This function first gets the vertical domains from the passed in UMM spatial extent.
   The vertical extent consists of a vector of maps that contains two keys :Type and :Value and their values.
   Once the funtion has the vertical domains it checks each map to see if the key of :Type has a correct
   value. If it does then it keeps the map otherwise it removes the map from the vector. The function passes back
   the fixed list of maps."
  [spatial]
  (let [vertical-domains (:VerticalSpatialDomains spatial)]
    (remove #(nil? (spatial/create-elevation-key (:Type %))) vertical-domains)))

(defn- expected-dif-spatial-extent
  "Returns the expected DIF parsed spatial extent for the given spatial extent."
  [spatial]
  (let [vertical-domains (expected-dif-vertical-domains spatial)
        horizontal-domains (seq (get-in spatial [:HorizontalSpatialDomain :Geometry :BoundingRectangles]))
        spatial-coverage (when (or horizontal-domains (not-empty vertical-domains))
                           (if (and horizontal-domains (not-empty vertical-domains))
                             "HORIZONTAL_VERTICAL"
                             (if horizontal-domains
                               "HORIZONTAL"
                               "VERTICAL")))
        spatial (-> spatial
                    (assoc :SpatialCoverageType spatial-coverage
                           :OrbitParameters nil
                           :VerticalSpatialDomains vertical-domains)
                    (update-in [:HorizontalSpatialDomain] assoc
                               :ZoneIdentifier nil)
                    (update-in [:HorizontalSpatialDomain :Geometry] assoc
                               :CoordinateSystem "CARTESIAN"
                               :Points nil
                               :Lines nil
                               :GPolygons nil))]
    (if horizontal-domains
      spatial
      (assoc spatial :HorizontalSpatialDomain nil))))

(defn- expected-dif-contact-mechanisms
  "Returns the expected DIF contact mechanisms"
  [contact-mechanisms]
  (->> (concat (filter #(= "Email" (:Type %)) contact-mechanisms)
               (filter #(= "Fax" (:Type %)) contact-mechanisms)
               (filter #(contact/umm-contact-phone-types (:Type %)) contact-mechanisms))
       (map #(update % :Type (fn [t] (get #{"Email" "Fax"} t "Telephone"))))
       seq))

(defn- expected-dif-contact-information
  "Retruns the expected contact information for the given contact information."
  [contact-info]
  (let [contact-info (some-> contact-info
                             (dissoc :RelatedUrls nil :ServiceHours nil :ContactInstruction nil)
                             (update :ContactMechanisms expected-dif-contact-mechanisms)
                             (update :Addresses conversion-util/expected-dif-addresses))]
    (when (seq (util/remove-nil-keys contact-info))
      (cmn/map->ContactInformationType contact-info))))

(def ^:private role->expected
  "Defines mapping of original UMM data contact Role to the expected. DIF9 data contact Role
  mapping to the UMM contact Role is different depending on where the data contact is.
  This is for general data contact on the collection level."
  {"Data Center Contact" "Data Center Contact"
   "Technical Contact" "Technical Contact"
   "Science Contact" "Data Center Contact"
   "Investigator" "Investigator"
   "Metadata Author" "Metadata Author"
   "User Services" "Data Center Contact"
   "Science Software Development" "Data Center Contact"})

(def ^:private data-center-role->expected
  "Defines mapping of original UMM data center data contact Role to the expected.
  DIF9 data contact Role mapping to the UMM contact Role is different depending on where the data
  contact is. This is for data contact on the data center level."
  {"Data Center Contact" "Data Center Contact"
   "Technical Contact" "Data Center Contact"
   "Science Contact" "Data Center Contact"
   "Investigator" "Investigator"
   "Metadata Author" "Data Center Contact"
   "User Services" "Data Center Contact"
   "Science Software Development" "Data Center Contact"})

(defn- expected-dif-roles
  "Returns the expected UMM roles for the given roles when roundtripped back from a DIF record"
  [roles role-expected-mapping]
  (vec (distinct (map role-expected-mapping roles))))

(defn- contact->expected
  "Retruns the expected contact person for the given contact which could be either a contact group
  or contact person"
  [contact role-expected-mapping]
  (let [contact (if (:GroupName contact)
                  (let [{:keys [Roles ContactInformation Addresses GroupName]} contact]
                    (cmn/map->ContactPersonType {:Roles Roles
                                                 :ContactInformation ContactInformation
                                                 :FirstName nil
                                                 :MiddleName nil
                                                 :LastName GroupName}))
                  contact)]
    (-> contact
        (assoc :Uuid nil)
        (assoc :NonDataCenterAffiliation nil)
        (update :Roles #(expected-dif-roles % role-expected-mapping))
        (update :ContactInformation expected-dif-contact-information))))

(defn- expected-dif-contact-persons
  "Returns the expected DIF parsed contact persons for the given UMM collection.
  Both ContactGroups and ContactPersons are converted into ContactPersons with the un-supported
  DIF fields dropped."
  [c]
  (let [contacts (conversion-util/expected-contact-information-urls
                   (mapv #(contact->expected % role->expected)
                       (concat (:ContactGroups c) (:ContactPersons c)))
                   "DataContactURL")]
    (when (seq contacts)
      contacts)))

(defn- expected-dif-data-center-contact-persons
  "Returns the expected DIF data center contact persons for the given UMM data center.
  Both ContactGroups and ContactPersons are converted into ContactPersons with the DIF not supported
  fields dropped."
  [c]
  (let [contacts (mapv #(contact->expected % data-center-role->expected)
                      (concat (:ContactGroups c) (:ContactPersons c)))]
    (if (seq contacts)
      contacts
      [(cmn/map->ContactPersonType {:Roles ["Data Center Contact"]
                                    :LastName su/not-provided})])))

(defn- expected-dif-data-center-contact-info
  "Returns the expected DIF9 data center contact information."
  [contact-info]
  (when-let [related-url (:URL (first (:RelatedUrls contact-info)))]
    (cmn/map->ContactInformationType
       {:RelatedUrls [(cmn/map->RelatedUrlType
                       {:URLContentType "DataCenterURL"
                        :Type "HOME PAGE"
                        :URL related-url})]})))

(defn- expected-dif-data-centers
  "Returns the expected DIF parsed data centers for the given UMM collection."
  [centers]
  (let [processing-centers (for [center centers
                                 :let [long-name (:LongName center)]
                                 :when (and (.contains (:Roles center) "PROCESSOR")
                                            (or (nil? long-name) (.endsWith ".processor" long-name)))]
                             (cmn/map->DataCenterType
                               {:Roles ["PROCESSOR"]
                                :ShortName (:ShortName center)
                                :LongName (or (:LongName center) center/dif9-processor-group)}))
        data-centers (for [center centers
                           :when (or (.contains (:Roles center) "ARCHIVER")
                                     (.contains (:Roles center) "DISTRIBUTOR"))
                           :let [expected-persons (expected-dif-data-center-contact-persons center)
                                 expected-contact-info (expected-dif-data-center-contact-info
                                                         (:ContactInformation center))]]
                       (-> center
                           (assoc :Roles ["ARCHIVER" "DISTRIBUTOR"])
                           (assoc :ContactPersons expected-persons)
                           (assoc :ContactGroups nil)
                           (assoc :ContactInformation expected-contact-info)))
        data-centers (if (seq data-centers)
                       data-centers
                       ;; create a dummy data center as it is required in DIF9
                       [(cmn/map->DataCenterType
                          {:Roles ["ARCHIVER" "DISTRIBUTOR"]
                           :ShortName su/not-provided
                           :ContactPersons [(cmn/map->ContactPersonType
                                              {:Roles ["Data Center Contact"]
                                               :LastName su/not-provided})]})])]
    (seq (concat data-centers processing-centers))))

(defn- expected-dif-additional-attribute
  [attribute]
  (-> attribute
      (assoc :Group "AdditionalAttribute")
      (assoc :ParameterRangeBegin nil)
      (assoc :ParameterRangeEnd nil)
      (assoc :MeasurementResolution nil)
      (assoc :ParameterUnitsOfMeasure nil)
      (assoc :ParameterValueAccuracy nil)
      (assoc :ValueAccuracyExplanation nil)
      (assoc :Description (su/with-default (:Description attribute)))))

(defn- expected-metadata-dates
  "When converting, the creation date and last revision date will be persisted"
  [umm-coll]
  (seq
   (remove nil? [(conversion-util/create-date-type (date/metadata-create-date umm-coll) "CREATE")
                 (conversion-util/create-date-type (date/metadata-update-date umm-coll) "UPDATE")])))

(defn- expected-collection-citations
  "Adds OnlineResource Name and Description to CollectionCitations"
  [collection-citations version]
  (if (empty? collection-citations)
    [{:Version version}]
    (for [collection-citation collection-citations]
      (if (:OnlineResource collection-citation)
        (assoc (update collection-citation :OnlineResource #(select-keys % [:Linkage]))
          :Version version)
        (assoc collection-citation
          :Version version)))))

(defn umm-expected-conversion-dif9
  [umm-coll]
  (let [expected-contact-persons (expected-dif-contact-persons umm-coll)]
    (-> umm-coll
        (update :DOI conversion-util/expected-dif-doi)
        (dissoc :AssociatedDOIs)
        ;; DIF 9 only supports entry-id in metadata associations
        (update-in-each [:MetadataAssociations] assoc :Type nil :Description nil :Version nil)
        ;; DIF 9 does not support tiling identification system
        (assoc :TilingIdentificationSystems nil)
        (update-in [:DataCenters] expected-dif-data-centers)
        (assoc :ContactGroups nil)
        (assoc :ContactPersons expected-contact-persons)
        ;; DIF 9 does not support DataDates
        (assoc :DataDates nil)
        ;; DIF 9 sets the UMM Version to 'Not provided' if it is not present in the DIF 9 XML
        (assoc :Version (or (:Version umm-coll) su/not-provided))
        (update-in [:TemporalExtents] dif9-temporal)
        (update-in [:SpatialExtent] expected-dif-spatial-extent)
        ;; DIF 9 does not support Platform Type or Characteristics. The mapping for Instruments is
        ;; unable to be implemented as specified.
        (update-in [:Platforms] expected-dif-platforms)
        (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
        (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)
        (update-in-each [:AdditionalAttributes] expected-dif-additional-attribute)
        (update-in-each [:PublicationReferences] conversion-util/dif-publication-reference)
        (update :RelatedUrls conversion-util/expected-related-urls-for-dif)
        ;;CMR-2716 SpatialKeywords are being replaced by LocationKeywords.
        (assoc :SpatialKeywords nil)
        (assoc :MetadataDates (expected-metadata-dates umm-coll))
        (update :AccessConstraints conversion-util/expected-access-constraints)
        ;; DIF 9 does not support VersionDescription
        (assoc :VersionDescription nil)
        (update :DataLanguage conversion-util/dif-expected-data-language)
        (assoc :CollectionProgress (conversion-util/expected-coll-progress umm-coll))
        (update-in [:CollectionCitations] expected-collection-citations (:Version umm-coll))
        (assoc :UseConstraints (when-let [description (get-in umm-coll [:UseConstraints :Description])]
                                 (umm-c/map->UseConstraintsType
                                   {:Description description})))
        (assoc :ArchiveAndDistributionInformation nil)
        (assoc :DirectDistributionInformation nil)
        (assoc-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem] nil)
        (dissoc :StandardProduct)
        js/parse-umm-c)))
