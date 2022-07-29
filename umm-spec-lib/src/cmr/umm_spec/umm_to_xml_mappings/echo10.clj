(ns cmr.umm-spec.umm-to-xml-mappings.echo10
  "Defines mappings from a UMM record into ECHO10 XML"
  (:require
   [clojure.string :as string]
   [cmr.common.date-time-parser :as p]
   [cmr.common.util :as util]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.date-util :as dates]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.related-url :as ru]
   [cmr.umm-spec.umm-to-xml-mappings.echo10.data-contact :as dc]
   [cmr.umm-spec.umm-to-xml-mappings.echo10.spatial :as spatial]
   [cmr.umm-spec.util :refer [with-default]]
   [cmr.umm-spec.util :as spec-util]))

(defn characteristic-mapping
  [data]
  [:Characteristic
   (elements-from data
                  :Name
                  :Description
                  :DataType
                  :Unit
                  :Value)])

(defn echo10-platforms
  [c]
  [:Platforms
   (for [plat (:Platforms c)]
     [:Platform
      [:ShortName (:ShortName plat)]
      [:LongName (with-default (:LongName plat))]
      [:Type (with-default (:Type plat))]
      [:Characteristics
       (for [cc (:Characteristics plat)]
         (characteristic-mapping cc))]
      [:Instruments
       (for [inst (:Instruments plat)]
         [:Instrument
          (elements-from inst
                         :ShortName
                         :LongName
                         :Technique)
          [:NumberOfSensors (:NumberOfInstruments inst)]
          [:Characteristics
           (for [cc (:Characteristics inst)]
             (characteristic-mapping cc))]
          [:Sensors
           (for [ss (:ComposedOf inst)]
             [:Sensor
              (elements-from ss
                             :ShortName
                             :LongName
                             :Technique)
              [:Characteristics
               (map characteristic-mapping (:Characteristics ss))]])]
          [:OperationModes
           (for [mode (:OperationalModes inst)]
             [:OperationMode mode])]])]])])

(defn echo10-temporal
  "Create the ECHO10 TemporalExtent. ECHO10 supports only one temoral extent, but for now
  we put multiple dates within the temporal extent. Set the ends at present flag if any
  UMM temporal ends at present. Only one date type can go into echo, so put either all
  of the range date times, single date times, or periodic date times in."
  [c]
  (when-let [temporals (seq (:TemporalExtents c))]
    [:Temporal
     (elements-from (first temporals)
                    :PrecisionOfSeconds)
     [:EndsAtPresentFlag (boolean (some :EndsAtPresentFlag temporals))]
     (let [range-date-times (mapcat :RangeDateTimes temporals)
           single-date-times (mapcat :SingleDateTimes temporals)
           periodic-date-times (mapcat :PeriodicDateTimes temporals)]
       (cond
        (seq range-date-times)
        (for [r range-date-times]
         [:RangeDateTime (elements-from r :BeginningDateTime :EndingDateTime)])

        (seq single-date-times)
        (for [date single-date-times]
         [:SingleDateTime (str date)])

        :else
        (for [pdt periodic-date-times]
         [:PeriodicDateTime
          (elements-from pdt
                           :Name
                           :StartDate
                           :EndDate
                           :DurationUnit
                           :DurationValue
                           :PeriodCycleDurationUnit
                           :PeriodCycleDurationValue)])))]))

(defn echo10-sciencekeywords
  "Generates ECHO 10 XML structure for science-keywords"
  [c]
  (when-let [science-keywords (:ScienceKeywords c)]
    [:ScienceKeywords
     (for [sk science-keywords]
       [:ScienceKeyword
        [:CategoryKeyword (:Category sk)]
        [:TopicKeyword (:Topic sk)]
        [:TermKeyword (:Term sk)]
        [:VariableLevel1Keyword
         [:Value (:VariableLevel1 sk)]
         [:VariableLevel2Keyword
          [:Value (:VariableLevel2 sk)]
          [:VariableLevel3Keyword (:VariableLevel3 sk)]]]
        [:DetailedVariableKeyword (:DetailedVariable sk)]])]))

(defn metadata-associations
  "Generates ECHO 10 XML structure for metadata associations"
  [c]
  (when-let [mas (:MetadataAssociations c)]
    [:CollectionAssociations
     (for [ma mas]
       [:CollectionAssociation
        [:ShortName (:EntryId ma)]
        [:VersionId (or (:Version ma) spec-util/not-provided)]
        [:CollectionType (or (:Type ma) spec-util/not-provided)]
        [:CollectionUse (:Description ma)]])]))

(defn generate-collection-citations
  "Finds first OtherCitationDetails value in CollectionCitations and uses it to
   generate a CitationForExternalPublication xml entry"
  [c]
  (when-let [collection-citations (:CollectionCitations c)]
    (when-let [citation (first (map :OtherCitationDetails collection-citations))]
      [:CitationForExternalPublication citation])))

(defn find-first-available-distribution-price
  "Find the first FileDistributionInformation object that contains the sub element of Fees."
  [c]
  (some :Fees (get-in c [:ArchiveAndDistributionInformation :FileDistributionInformation])))

(defn umm-c-to-echo10-xml
  "Returns ECHO10 XML structure from UMM collection record c."
  [c]
  (xml
    [:Collection
     [:ShortName (:ShortName c)]
     [:VersionId (:Version c)]
     [:InsertTime (dates/with-current (dates/data-create-date c))]
     [:LastUpdate (dates/with-current (dates/data-update-date c))]
     [:DeleteTime (dates/data-delete-date c)]
     [:LongName spec-util/not-provided]
     [:DataSetId (:EntryTitle c)]
     [:Description (if-let [abstract (:Abstract c)]
                     (util/trunc abstract 12000)
                     spec-util/not-provided)]
     (when-let [doi (get c :DOI)]
       (if (:DOI doi)
         [:DOI
          [:DOI (:DOI doi)]
          (when (:Authority doi)
            [:Authority (:Authority doi)])]
         (when (:MissingReason doi)
           [:DOI
            [:MissingReason (:MissingReason doi)]
            (when (:Explanation doi)
              [:Explanation (:Explanation doi)])])))
     (when-let [assoc-dois (get c :AssociatedDOIs)]
       [:AssociatedDOIs
         (for [assoc-doi assoc-dois]
           [:AssociatedDOI
             [:DOI (:DOI assoc-doi)]
             [:Title (:Title assoc-doi)]
             [:Authority (:Authority assoc-doi)]])])
     [:CollectionDataType (:CollectionDataType c)]
     [:StandardProduct (:StandardProduct c)]
     (when-let [revision-date (dates/metadata-update-date c)]
       [:RevisionDate (p/clj-time->date-time-str revision-date)])
     [:SuggestedUsage (util/trunc (:Purpose c) 4000)]
     (dc/generate-processing-centers c)
     [:ProcessingLevelId (-> c :ProcessingLevel :Id)]
     [:ProcessingLevelDescription (-> c :ProcessingLevel :ProcessingLevelDescription)]
     (dc/generate-archive-centers c)
     [:VersionDescription (:VersionDescription c)]
     (generate-collection-citations c)
     [:CollectionState (:CollectionProgress c)]
     [:RestrictionFlag (-> c :AccessConstraints :Value)]
     [:RestrictionComment (util/trunc (-> c :AccessConstraints :Description) 1024)]
     (when-let [use-constraints (get c :UseConstraints)]
       [:UseConstraints
         [:Description (:Description use-constraints)]
         (when (some? (:FreeAndOpenData use-constraints))
           [:FreeAndOpenData (Boolean/valueOf (:FreeAndOpenData use-constraints))])
         (when-let [url (get-in use-constraints [:LicenseURL :Linkage])]
           [:LicenseURL
             [:URL url]
             [:Description (get-in use-constraints [:LicenseURL :Description])]
             [:Type (or (get-in use-constraints [:LicenseURL :Name])
                        "License URL")]
             [:MimeType (get-in use-constraints [:LicenseURL :MimeType])]])
         (when-let [license-text (:LicenseText use-constraints)]
           [:LicenseText license-text])])
     [:Price (when-let [price-str (find-first-available-distribution-price c)]
               (try (format "%9.2f" (Double. price-str))
                 ;; If price is not a number string just ignore it. ECHO10
                 ;; expectes a string in %9.2f format, so we have to
                 ;;ignore 'Free', 'Gratis', etc.
                 (catch NumberFormatException e)))]
     ;; Go through all of the FileDistributionInformation elements and set the
     ;; ECHO 10 DataFormats.
     (for [data-format (get-in c [:ArchiveAndDistributionInformation :FileDistributionInformation])]
       [:DataFormat (:Format data-format)])
     [:SpatialKeywords
      (for [kw (lk/location-keywords->spatial-keywords (:LocationKeywords c))]
        [:Keyword kw])]
     [:TemporalKeywords
      (for [kw (:TemporalKeywords c)]
        [:Keyword kw])]
     (echo10-temporal c)
     (dc/generate-contacts c) ;; Contacts are both Data Centers and Contact Persons
     (echo10-sciencekeywords c)
     (echo10-platforms c)
     [:AdditionalAttributes
      (for [aa (:AdditionalAttributes c)]
        [:AdditionalAttribute
         [:Name (:Name aa)]
         [:DataType (:DataType aa)]
         [:Description (with-default (:Description aa))]
         [:ParameterRangeBegin (:ParameterRangeBegin aa)]
         [:ParameterRangeEnd (:ParameterRangeEnd aa)]
         [:Value (:Value aa)]])]
     (metadata-associations c)
     [:Campaigns
      (for [{:keys [ShortName LongName StartDate EndDate]} (:Projects c)]
        [:Campaign
         [:ShortName ShortName]
         [:LongName LongName]
         [:StartDate StartDate]
         [:EndDate EndDate]])]
     [:TwoDCoordinateSystems
      (for [sys (:TilingIdentificationSystems c)]
        [:TwoDCoordinateSystem
         [:TwoDCoordinateSystemName (with-default (:TilingIdentificationSystemName sys))]
         [:Coordinate1
          (elements-from (:Coordinate1 sys) :MinimumValue :MaximumValue)]
         [:Coordinate2
          (elements-from (:Coordinate2 sys) :MinimumValue :MaximumValue)]])]
     (spatial/spatial-info-element c)
     (ru/generate-access-urls (:RelatedUrls c))
     (ru/generate-resource-urls (:RelatedUrls c))
     (spatial/spatial-element c)
     (ru/generate-browse-urls (:RelatedUrls c))
     (when-let [direct-dist-info (:DirectDistributionInformation c)]
       [:DirectDistributionInformation
         [:Region (:Region direct-dist-info)]
         (for [prefix-name (:S3BucketAndObjectPrefixNames direct-dist-info)]
           [:S3BucketAndObjectPrefixName prefix-name])
         [:S3CredentialsAPIEndpoint (:S3CredentialsAPIEndpoint direct-dist-info)]
         [:S3CredentialsAPIDocumentationURL (:S3CredentialsAPIDocumentationURL direct-dist-info)]])]))
