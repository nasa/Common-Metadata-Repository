(ns cmr.umm-spec.xml-to-umm-mappings.dif10
  "Defines mappings from DIF10 XML into UMM records"
  (:require [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.simple-xpath :refer [select]]
            [camel-snake-kebab.core :as csk]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.dif10.spatial :as spatial]))

(defn- not-not-provided
  "Returns x if x is not the DIF 10 placeholder \"Not provided\"."
  [x]
  (when-not (= x "Not provided")
    x))

(defn- parse-characteristics
  [el]
  (for [characteristic (select el "Characteristics")]
    (fields-from characteristic :Name :Description :DataType :Unit :Value)))

(defn parse-dif10-xml
  "Returns collection map from DIF10 collection XML document."
  [doc]
  {:EntryTitle (value-of doc "/DIF/Entry_Title")
   :EntryId (value-of doc "/DIF/Entry_ID")
   :Version (not-not-provided (value-of doc "/DIF/Version"))
   :Abstract (value-of doc "/DIF/Summary/Abstract")
   :CollectionDataType (value-of doc "/DIF/Collection_Data_Type")
   :Purpose (value-of doc "/DIF/Summary/Purpose")
   :DataLanguage (value-of doc "/DIF/Dataset_Language")
   :TemporalKeywords (values-at doc "/DIF/Temporal_Coverage/Temporal_Info/Ancillary_Temporal_Keyword")
   :CollectionProgress (value-of doc "/DIF/Data_Set_Progress")
   :SpatialKeywords (values-at doc "/DIF/Location")
   :Projects (for [proj (select doc "/DIF/Project")]
               {:ShortName (value-of proj "Short_Name")
                :LongName (value-of proj "Long_Name")
                :Campaigns (values-at proj "Campaign")
                :StartDate (value-of proj "Start_Date")
                :EndDate (value-of proj "End_Date")})
   :Quality (value-of doc "/DIF/Quality")
   :AccessConstraints {:Description (value-of doc "/DIF/Access_Constraints")}
   :UseConstraints (value-of doc "/DIF/Use_Constraints")
   :Platforms (for [platform (select doc "/DIF/Platform")]
                {:ShortName (value-of platform "Short_Name")
                 :LongName (value-of platform "Long_Name")
                 :Type (not-not-provided (value-of platform "Type"))
                 :Characteristics (parse-characteristics platform)
                 :Instruments (for [inst (select platform "Instrument")]
                                {:ShortName (value-of inst "Short_Name")
                                 :LongName (value-of inst "Long_Name")
                                 :Technique (value-of inst "Technique")
                                 :NumberOfSensors (value-of inst "NumberOfSensors")
                                 :Characteristics (parse-characteristics inst)
                                 :OperationalModes (values-at inst "OperationalMode")
                                 :Sensors (for [sensor (select inst "Sensor")]
                                            {:ShortName (value-of sensor "Short_Name")
                                             :LongName (value-of sensor "Long_Name")
                                             :Technique (value-of sensor "Technique")
                                             :Characteristics (parse-characteristics sensor)})})})
   :TemporalExtents (for [temporal (select doc "/DIF/Temporal_Coverage")]
                      {:TemporalRangeType (value-of temporal "Temporal_Range_Type")
                       :PrecisionOfSeconds (value-of temporal "Precision_Of_Seconds")
                       :EndsAtPresentFlag (value-of temporal "Ends_At_Present_Flag")
                       :RangeDateTimes (for [rdt (select temporal "Range_DateTime")]
                                         {:BeginningDateTime (value-of rdt "Beginning_Date_Time")
                                          :EndingDateTime (value-of rdt "Ending_Date_Time")})
                       :SingleDateTimes (values-at temporal "Single_DateTime")
                       :PeriodicDateTimes (for [pdt (select temporal "Periodic_DateTime")]
                                            {:Name (value-of pdt "Name")
                                             :StartDate (value-of pdt "Start_Date")
                                             :EndDate (value-of pdt "End_Date")
                                             :DurationUnit (value-of pdt "Duration_Unit")
                                             :DurationValue (value-of pdt "Duration_Value")
                                             :PeriodCycleDurationUnit (value-of pdt "Period_Cycle_Duration_Unit")
                                             :PeriodCycleDurationValue (value-of pdt "Period_Cycle_Duration_Value")})})
   :SpatialExtent (spatial/parse-spatial doc)
   :Distributions (for [dist (select doc "/DIF/Distribution")]
                    {:DistributionMedia (value-of dist "Distribution_Media")
                     :DistributionSize (value-of dist "Distribution_Size")
                     :DistributionFormat (value-of dist "Distribution_Format")
                     :Fees (value-of dist "Fees")})
   :ProcessingLevel {:Id (value-of doc "/DIF/Product_Level_Id")}
   :AdditionalAttributes
   (for [aa (select doc "/DIF/AdditionalAttributes")]
     (fields-from aa :Name :Description :DataType :ParameterRangeBegin :ParameterRangeEnd
                  :Value :MeasurementResolution :ParameterUnitsOfMeasure
                  :ParameterValueAccuracy :ValueAccuracyExplanation))
   :PublicationReferences (for [pub-ref (select doc "/DIF/Reference")]
                            (into {} (map (fn [x]
                                            (if (keyword? x)
                                              [(csk/->PascalCaseKeyword x) (value-of pub-ref (str x))]
                                              x))
                                          [:Author
                                           :Publication_Date
                                           :Title
                                           :Series
                                           :Edition
                                           :Volume
                                           :Issue
                                           :Report_Number
                                           :Publication_Place
                                           :Publisher
                                           :Pages
                                           [:ISBN (value-of pub-ref "ISBN")]
                                           (when (= (value-of pub-ref "Persistent_Identifier/Type") "DOI")
                                             [:DOI {:DOI (value-of pub-ref "Persistent_Identifier/Identifier")}])
                                           [:RelatedUrl
                                            {:URLs (seq
                                                     (remove nil? [(value-of pub-ref "Online_Resource")]))}]
                                           :Other_Reference_Details])))
   :AncillaryKeywords (values-at doc  "/DIF/Ancillary_Keyword")})

(defn dif10-xml-to-umm-c
  "Returns UMM-C collection record from DIF10 collection XML document."
  [metadata]
  (js/coerce (parse-dif10-xml metadata)))
