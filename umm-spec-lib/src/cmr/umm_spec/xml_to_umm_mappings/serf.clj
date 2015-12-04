(ns cmr.umm-spec.xml-to-umm-mappings.serf
  "Defines mappings from SERF XML into UMM records"
  (:require [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.simple-xpath :refer [select]]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as string]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.util :as u :refer [without-default-value-of]]
            [cmr.umm-spec.date-util :as date]))

(defn- parse-characteristics
  [el]
  (for [characteristic (select el "Characteristics")]
    (fields-from characteristic :Name :Description :DataType :Unit :Value)))

(defn- parse-instruments
  [doc]
    (for [inst (select doc "/SERF/Sensor_Name")]
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
                   :Characteristics (parse-characteristics sensor)})}))

(defn- parse-projects
  [doc]
  (for [proj (select doc "/SERF/Project")]
    {:ShortName (value-of proj "Short_Name")
     :LongName (value-of proj "Long_Name")
     :Campaigns (values-at proj "Campaign")
     :StartDate (date-at proj "Start_Date")
     :EndDate (date-at proj "End_Date")}))

(defn- parse-data-dates
  "Returns seq of UMM-C DataDates parsed from DIF 10 XML document."
  [doc]
  (let [[md-dates-el] (select doc "/DIF/Metadata_Dates")
        tag-types [["Data_Creation"      "CREATE"]
                   ["Data_Last_Revision" "UPDATE"]
                   ["Data_Future_Review" "REVIEW"]
                   ["Data_Delete"        "DELETE"]]]
    (filter :Date
            (for [[tag date-type] tag-types]
              {:Type date-type
               :Date (date/not-default (value-of md-dates-el tag))}))))

(defn parse-serf-xml
  "Returns collection map from DIF10 collection XML document."
  [doc]
  {:EntryTitle (value-of doc "/SERF/Entry_Title")
   :EntryId (value-of doc "/SERF/Entry_ID")
   :Abstract (value-of doc "/SERF/Summary/Abstract")
   :Purpose (value-of doc "/SERF/Summary/Purpose")
   :ServiceLanguage (value-of doc "/SERF/Service_Language")
   :Responsibilities (value-of doc "/SERF/Service_Provider")
   :RelatedUrls (for [related-url (select doc "/SERF/Related_URL")] ;; TODO: Figure out if /SERF/Multimedia_Sample relates to RelatedUrls
                  {:URLs (values-at related-url "URL")
                   :Protocol (value-of related-url "Protocol")
                   :Description (value-of related-url "Description")
                   :ContentType {:Type (value-of related-url "URL_Content_Type/Type")
                                 :Subtype (value-of related-url "URL_Content_Type/Subtype")}
                   :MimeType (value-of related-url "Mime_Type")})
   :ServiceCitation (value-of doc "/SERF/Service_Citation")
   :Quality (value-of doc "/SERF/Quality")
   :UseConstraints (value-of doc "/SERF/Use_Constraints")
   :AccessConstraints (value-of doc "/SERF/Use_Constraints")
   :MetadataAssociations (for [ma (select doc "/SERF/Parent_SERF")]
                           {:EntryId (value-of ma "Entry_Id/Short_Name")
                            :Version (without-default-value-of ma "Entry_Id/Version")
                            :Description (without-default-value-of ma "Description")
                            :Type (string/upper-case (without-default-value-of ma "Type"))})
   :PublicationReferences (for [pub-ref (select doc "/SERF/Reference")]
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
   :ISOTopicCategories (values-at doc "/SERF/ISO_Topic_Category")
   :Platforms (for [platform (select doc "/SERF/Source_Name")]
                {:ShortName (value-of platform "Short_Name")
                 :LongName (value-of platform "Long_Name")
                 :Type (without-default-value-of platform "Type")
                 :Characteristics (parse-characteristics platform)
                 :Instruments (parse-instruments doc)})
   :Distributions (for [dist (select doc "/SERF/Distribution")]
                    {:DistributionMedia (value-of dist "Distribution_Media")
                     :DistributionSize (value-of dist "Distribution_Size")
                     :DistributionFormat (value-of dist "Distribution_Format")
                     :Fees (value-of dist "Fees")})
   :AdditionalAttributes
   (for [aa (select doc "/SERF/ExtendedMetadata/Metadata")]
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
  
   :AncillaryKeywords (values-at doc "/SERF/Keyword")
   :Projects (parse-projects doc)
   :ScienceKeywords (for [sk (select doc "/SERF/Science_Keywords")]
                      {:Category (value-of sk "Category")
                       :Topic (value-of sk "Topic")
                       :Term (value-of sk "Term")
                       :VariableLevel1 (value-of sk "Variable_Level_1")
                       :VariableLevel2 (value-of sk "Variable_Level_2")
                       :VariableLevel3 (value-of sk "Variable_Level_3")
                       :DetailedVariable (value-of sk "Detailed_Variable")})})
 

(defn serf-xml-to-umm-s
  "Returns UMM-S service record from a SERF XML document."
  [metadata]
  (js/coerce (parse-serf-xml metadata)))
