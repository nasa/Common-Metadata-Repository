(ns cmr.umm-spec.umm-to-xml-mappings.echo10
  "Defines mappings from a UMM record into ECHO10 XML"
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.related-url :as ru]
            [cmr.umm-spec.util :refer [with-default]]
            [cmr.umm-spec.util :as spec-util]
            [cmr.umm-spec.date-util :as dates]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.spatial :as spatial]))

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
                         :Technique
                         :NumberOfSensors)
          [:Characteristics
           (for [cc (:Characteristics inst)]
             (characteristic-mapping cc))]
          [:Sensors
           (for [ss (:Sensors inst)]
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
  [c]
  ;; We're assuming there is only one TemporalExtent for now. Issue CMR-1933 has been opened to
  ;; address questions about temporal mappings.
  (when-let [temporal (first (:TemporalExtents c))]
    [:Temporal
     (elements-from temporal
                    :TemporalRangeType
                    :PrecisionOfSeconds
                    :EndsAtPresentFlag)

     (for [r (:RangeDateTimes temporal)]
       [:RangeDateTime (elements-from r :BeginningDateTime :EndingDateTime)])

     (for [date (:SingleDateTimes temporal)]
       [:SingleDateTime (str date)])

     (for [pdt (:PeriodicDateTimes temporal)]
       [:PeriodicDateTime
        (elements-from pdt
                       :Name
                       :StartDate
                       :EndDate
                       :DurationUnit
                       :DurationValue
                       :PeriodCycleDurationUnit
                       :PeriodCycleDurationValue)])]))

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

(defn umm-c-to-echo10-xml
  "Returns ECHO10 XML structure from UMM collection record c."
  [c]
  (xml
    [:Collection
     [:ShortName (:ShortName c)]
     [:VersionId (with-default (:Version c))]
     [:InsertTime (dates/or-default (dates/data-create-date c))]
     [:LastUpdate (dates/or-default (dates/data-update-date c))]
     [:DeleteTime (dates/data-delete-date c)]
     [:LongName "dummy-long-name"]
     [:DataSetId (:EntryTitle c)]
     [:Description (:Abstract c)]
     [:CollectionDataType (:CollectionDataType c)]
     [:Orderable "true"]
     [:Visible "true"]
     [:SuggestedUsage (:Purpose c)]
     [:ProcessingLevelId (-> c :ProcessingLevel :Id)]
     [:ProcessingLevelDescription (-> c :ProcessingLevel :ProcessingLevelDescription)]
     [:CollectionState (:CollectionProgress c)]
     [:RestrictionFlag (-> c :AccessConstraints :Value)]
     [:RestrictionComment (-> c :AccessConstraints :Description)]
     [:Price (when-let [price-str (-> c :Distributions first :Fees)]
               (try (format "%9.2f" (Double. price-str))
                 ;; If price is not a number string just ignore it. ECHO10
                 ;; expectes a string in %9.2f format, so we have to
                 ;;ignore 'Free', 'Gratis', etc.
                 (catch NumberFormatException e)))]
     [:DataFormat (-> c :Distributions first :DistributionFormat)]
     [:SpatialKeywords
      (for [kw (:SpatialKeywords c)]
        [:Keyword kw])]
     [:TemporalKeywords
      (for [kw (:TemporalKeywords c)]
        [:Keyword kw])]
     (echo10-temporal c)
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
     (when-let [sys (:TilingIdentificationSystem c)]
       [:TwoDCoordinateSystems
        [:TwoDCoordinateSystem
         [:TwoDCoordinateSystemName (with-default (:TilingIdentificationSystemName sys))]
         [:Coordinate1
          (elements-from (:Coordinate1 sys) :MinimumValue :MaximumValue)]
         [:Coordinate2
          (elements-from (:Coordinate2 sys) :MinimumValue :MaximumValue)]]])
     (ru/generate-access-urls (:RelatedUrls c))
     (ru/generate-resource-urls (:RelatedUrls c))
     (spatial/spatial-element c)
     (ru/generate-browse-urls (:RelatedUrls c))]))
