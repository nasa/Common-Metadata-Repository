(ns cmr.umm-spec.xml-to-umm-mappings.dif10
  "Defines mappings from DIF10 XML into UMM records"
  (:require [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.simple-xpath :refer [select]]
            [camel-snake-kebab.core :as csk]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.dif10.spatial :as spatial]
            [cmr.umm-spec.util :as u :refer [without-default-value-of]]))

(defn- parse-characteristics
  [el]
  (for [characteristic (select el "Characteristics")]
    (fields-from characteristic :Name :Description :DataType :Unit :Value)))

(defn- parse-projects
  [doc]
  (when-not (= u/not-provided (value-of doc "/DIF/Project[1]/Short_Name"))
    (for [proj (select doc "/DIF/Project")]
      {:ShortName (value-of proj "Short_Name")
       :LongName (value-of proj "Long_Name")
       :Campaigns (values-at proj "Campaign")
       :StartDate (date-at proj "Start_Date")
       :EndDate (date-at proj "End_Date")})))

(defn- parse-instruments
  [platform-el]
  (when-not (= u/not-provided (value-of platform-el "Instrument[1]/Short_Name"))
    (for [inst (select platform-el "Instrument")]
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
                   :Characteristics (parse-characteristics sensor)})})))

(defn parse-dif10-xml
  "Returns collection map from DIF10 collection XML document."
  [doc]
  {:EntryTitle (value-of doc "/DIF/Entry_Title")
   :EntryId (value-of doc "/DIF/Entry_ID")
   :Version (without-default-value-of doc "/DIF/Version")
   :Abstract (value-of doc "/DIF/Summary/Abstract")
   :CollectionDataType (value-of doc "/DIF/Collection_Data_Type")
   :Purpose (value-of doc "/DIF/Summary/Purpose")
   :DataLanguage (value-of doc "/DIF/Dataset_Language")
   :TemporalKeywords (values-at doc "/DIF/Temporal_Coverage/Temporal_Info/Ancillary_Temporal_Keyword")
   :CollectionProgress (value-of doc "/DIF/Dataset_Progress")
   :SpatialKeywords (values-at doc "/DIF/Location/Location_Category")
   :Projects (parse-projects doc)
   :Quality (value-of doc "/DIF/Quality")
   :AccessConstraints {:Description (value-of doc "/DIF/Access_Constraints")}
   :UseConstraints (value-of doc "/DIF/Use_Constraints")
   :Platforms (for [platform (select doc "/DIF/Platform")]
                {:ShortName (value-of platform "Short_Name")
                 :LongName (value-of platform "Long_Name")
                 :Type (without-default-value-of platform "Type")
                 :Characteristics (parse-characteristics platform)
                 :Instruments (parse-instruments platform)})
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
   (for [aa (select doc "/DIF/Additional_Attributes")]
     {:Name (value-of aa "Name")
      :DataType (value-of aa "DataType")
      :Description (without-default-value-of aa "Description")
      :MeasurementResolution (value-of aa "MeasurementResolution")
      :ParameterRangeBegin (without-default-value-of aa "ParameterRangeBegin")
      :ParameterRangeEnd (value-of aa "ParameterRangeEnd")
      :ParameterUnitsOfMeasure (value-of aa "ParameterUnitsOfMeasure")
      :ParameterValueAccuracy (value-of aa "ParameterValueAccuracy")
      :ValueAccuracyExplanation (value-of aa "ValueAccuracyExplanation")
      :Value (value-of aa "Value")})
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
   :AncillaryKeywords (values-at doc  "/DIF/Ancillary_Keyword")
   :RelatedUrls (for [related-url (select doc "/DIF/Related_URL")]
                  { :URLs (values-at related-url "URL")
                    :Description (value-of related-url "Description")
                    :ContentType {:Type (value-of related-url "URL_Content_Type/Type")
                                  :Subtype (value-of related-url "URL_Content_Type/Subtype")}
                    :MimeType (value-of related-url "Mime_Type")})
   :ScienceKeywords (for [sk (select doc "/DIF/Science_Keywords")]
                         {:Category (value-of sk "Category")
                          :Topic (value-of sk "Topic")
                          :Term (value-of sk "Term")
                          :VariableLevel1 (value-of sk "Variable_Level_1")
                          :VariableLevel2 (value-of sk "Variable_Level_2")
                          :VariableLevel3 (value-of sk "Variable_Level_3")
                          :DetailedVariable (value-of sk "Detailed_Variable")})})

(defn dif10-xml-to-umm-c
  "Returns UMM-C collection record from DIF10 collection XML document."
  [metadata]
  (js/coerce (parse-dif10-xml metadata)))
