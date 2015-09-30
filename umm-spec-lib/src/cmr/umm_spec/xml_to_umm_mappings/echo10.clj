(ns cmr.umm-spec.xml-to-umm-mappings.echo10
  "Defines mappings from ECHO10 XML into UMM records"
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.util :refer [without-default-value-of]]
            [cmr.umm-spec.xml-to-umm-mappings.echo10.spatial :as spatial]
            [cmr.umm-spec.xml-to-umm-mappings.echo10.related-url :as ru]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.util :as u]))

(defn parse-temporal
  "Returns seq of UMM temporal extents from an ECHO10 XML document."
  [doc]
  (for [temporal (select doc "/Collection/Temporal")]
    {:TemporalRangeType (value-of temporal "TemporalRangeType")
     :PrecisionOfSeconds (value-of temporal "PrecisionOfSeconds")
     :EndsAtPresentFlag (value-of temporal "EndsAtPresentFlag")
     :RangeDateTimes (for [rdt (select temporal "RangeDateTime")]
                       (fields-from rdt :BeginningDateTime :EndingDateTime))
     :SingleDateTimes (values-at temporal "SingleDateTime")
     :PeriodicDateTimes (for [pdt (select temporal "PeriodicDateTime")]
                          (fields-from pdt :Name :StartDate :EndDate :DurationUnit :DurationValue
                                       :PeriodCycleDurationUnit :PeriodCycleDurationValue))}))

(defn parse-characteristic
  "Returns a UMM characteristic record from an ECHO10 Characteristic element."
  [element]
  (fields-from element :Name :Description :DataType :Unit :Value))

(defn parse-characteristics
  "Returns a seq of UMM characteristic records from the element's child Characteristics."
  [el]
  (map parse-characteristic (select el "Characteristics/Characteristic")))

(defn parse-sensor
  "Returns a UMM Sensor record from an ECHO10 Sensor element."
  [sensor-element]
  (assoc (fields-from sensor-element :ShortName :LongName :Technique)
         :Characteristics (parse-characteristics sensor-element)))

(defn parse-instrument
  "Returns a UMM Instrument record from an ECHO10 Instrument element."
  [inst]
  (assoc (fields-from inst :ShortName :LongName :Technique :NumberOfSensors)
         :OperationalModes (values-at inst "OperationModes/OperationMode")
         :Characteristics (parse-characteristics inst)
         :Sensors (map parse-sensor (select inst "Sensors/Sensor"))))

(defn- parse-collection-type
  "Returns one of the enumerated types for CollectionType if present and matching one of the
  enumerated types, nil if present and not matching, or Not Specified if not present."
  [ca]
  (let [ctype (value-of ca "CollectionType")]
    (if ctype
      (if (contains? #{"SCIENCE ASSOCIATED" "DEPENDENT" "INPUT"} ctype)
        ctype
        nil)
      u/not-provided)))


(defn- parse-echo10-xml
  "Returns UMM-C collection structure from ECHO10 collection XML document."
  [doc]
  {:EntryTitle (value-of doc "/Collection/DataSetId")
   :EntryId    (value-of doc "/Collection/ShortName")
   :Version    (without-default-value-of doc "/Collection/VersionId")
   :Abstract   (value-of doc "/Collection/Description")
   :CollectionDataType (value-of doc "/Collection/CollectionDataType")
   :Purpose    (value-of doc "/Collection/SuggestedUsage")
   :CollectionProgress (value-of doc "/Collection/CollectionState")
   :AccessConstraints {:Description (value-of doc "/Collection/RestrictionComment")
                       :Value (value-of doc "/Collection/RestrictionFlag")}
   :Distributions [{:DistributionFormat (value-of doc "/Collection/DataFormat")
                    :Fees (value-of doc "/Collection/Price")}]
   :TemporalKeywords (values-at doc "/Collection/TemporalKeywords/Keyword")
   :SpatialKeywords  (values-at doc "/Collection/SpatialKeywords/Keyword")
   :SpatialExtent    (spatial/parse-spatial doc)
   :TemporalExtents  (parse-temporal doc)
   :Platforms (for [plat (select doc "/Collection/Platforms/Platform")]
                {:ShortName (without-default-value-of plat "ShortName")
                 :LongName (without-default-value-of plat "LongName")
                 :Type (without-default-value-of plat "Type")
                 :Characteristics (parse-characteristics plat)
                 :Instruments (map parse-instrument (select plat "Instruments/Instrument"))})
   :ProcessingLevel {:Id (value-of doc "/Collection/ProcessingLevelId")
                     :ProcessingLevelDescription (value-of doc "/Collection/ProcessingLevelDescription")}
   :AdditionalAttributes (for [aa (select doc "/Collection/AdditionalAttributes/AdditionalAttribute")]
                           {:Name (value-of aa "Name")
                            :DataType (value-of aa "DataType")
                            :Description (without-default-value-of aa "Description")
                            :ParameterRangeBegin (value-of aa "ParameterRangeBegin")
                            :ParameterRangeEnd (value-of aa "ParameterRangeEnd")
                            :Value (value-of aa "Value")})
   :Projects (for [proj (select doc "/Collection/Campaigns/Campaign")]
               {:ShortName (value-of proj "ShortName")
                :LongName (value-of proj "LongName")
                :StartDate (value-of proj "StartDate")
                :EndDate (value-of proj "EndDate")})
   :RelatedUrls (ru/parse-related-urls doc)
   :ScienceKeywords (for [sk (select doc "/Collection/ScienceKeywords/ScienceKeyword")]
                         {:Category (value-of sk "CategoryKeyword")
                          :Topic (value-of sk "TopicKeyword")
                          :Term (value-of sk "TermKeyword")
                          :VariableLevel1 (value-of sk "VariableLevel1Keyword/Value")
                          :VariableLevel2 (value-of sk "VariableLevel1Keyword/VariableLevel2Keyword/Value")
                          :VariableLevel3 (value-of sk "VariableLevel1Keyword/VariableLevel2Keyword/VariableLevel3Keyword")
                          :DetailedVariable (value-of sk "DetailedVariableKeyword")})
   :MetadataAssociations (for [ca (select doc "/Collection/CollectionAssociations/CollectionAssociation")]
                           {
                            ;; TODO This should be set to CollectionType value as long as it matches
                            ;; one of the allowed enumerated type values for this field. Otherwise we
                            ;; should set it to nil.
                            ;; When parsing it out we should use "Not Specified" if it isn't presents
                            :Type (parse-collection-type ca)
                            :Description (value-of ca "CollectionUse")
                            :EntryId (str (value-of ca "ShortName") "_" (value-of ca "VersionId"))
                            :Version (value-of ca "VersionId")
                            })})
(defn echo10-xml-to-umm-c
  "Returns UMM-C collection record from ECHO10 collection XML document."
  [metadata]
  (js/coerce (parse-echo10-xml metadata)))
