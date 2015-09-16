(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2
  "Defines mappings from UMM records into ISO19115-2 XML."
  (:require [clojure.string :as str]
            [cmr.umm-spec.umm-to-xml-mappings.iso-util :refer [gen-id]]
            [cmr.umm-spec.xml.gen :refer :all]))

(def iso19115-2-xml-namespaces
  {:xmlns:xs "http://www.w3.org/2001/XMLSchema"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:eos "http://earthdata.nasa.gov/schema/eos"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:swe "http://schemas.opengis.net/sweCommon/2.0/"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"})

(def echo-attributes-info
  [:eos:otherPropertyType
   [:gco:RecordType {:xlink:href "http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])"}
    "Echo Additional Attributes"]])

(defn- date-mapping
  "Returns the date element mapping for the given name and date value in string format."
  [date-name value]
  [:gmd:date
   [:gmd:CI_Date
    [:gmd:date
     [:gco:DateTime value]]
    [:gmd:dateType
     [:gmd:CI_DateTypeCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode"
                            :codeListValue date-name} date-name]]]])

(def attribute-type-code-list
  "http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode")

(def attribute-data-type-code-list
  "http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode")

(defn- generate-title
  "Returns an ISO title string from the ShortName and LongName fields of the given record."
  [record]
  (let [{short-name :ShortName long-name :LongName} record]
    (if (seq long-name) (str short-name " > " long-name) short-name)))

(defn- generate-descriptive-keywords
  "Returns the content generator instructions for the given descriptive keywords."
  [keywords keyword-type]
  [:gmd:MD_Keywords
   (for [keyword keywords]
     [:gmd:keyword [:gco:CharacterString keyword]])
   [:gmd:type
    [:gmd:MD_KeywordTypeCode
     {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_KeywordTypeCode"
      :codeListValue keyword-type} keyword-type]]
   [:gmd:thesaurusName {:gco:nilReason "unknown"}]])

(defn- generate-projects-keywords
  "Returns the content generator instructions for descriptive keywords of the given projects."
  [projects]
  (let [project-keywords (map generate-title projects)]
    (generate-descriptive-keywords project-keywords "project")))

(defn- generate-characteristics
  "Returns the generated characteristics content generator instructions, with the type
  argument used for the EOS_AdditionalAttributeTypeCode codeListValue attribute value and content."
  [type characteristics]
  [:eos:otherProperty
   [:gco:Record
    [:eos:AdditionalAttributes
     (for [characteristic characteristics]
       [:eos:AdditionalAttribute
        [:eos:reference
         [:eos:EOS_AdditionalAttributeDescription
          [:eos:type
           [:eos:EOS_AdditionalAttributeTypeCode {:codeList attribute-type-code-list
                                                  :codeListValue type}
            type]]
          [:eos:name
           (char-string (:Name characteristic))]
          [:eos:description
           (char-string (:Description characteristic))]
          [:eos:dataType
           [:eos:EOS_AdditionalAttributeDataTypeCode {:codeList attribute-data-type-code-list
                                                      :codeListValue (:DataType characteristic)}
            (:DataType characteristic)]]
          [:eos:parameterUnitsOfMeasure
           (char-string (:Unit characteristic))]]]
        [:eos:value
         (char-string (:Value characteristic))]])]]])

(defn- generate-sensors
  "Returns content generator instructions for the given sensors."
  [sensors]
  (for [sensor sensors]
    [:eos:sensor
     [:eos:EOS_Sensor
      [:eos:citation
       [:gmd:CI_Citation
        [:gmd:title
         [:gco:CharacterString (generate-title sensor)]]
        [:gmd:date {:gco:nilReason "unknown"}]]]
      [:eos:identifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (:ShortName sensor))]
        [:gmd:description
         (char-string (:LongName sensor))]]]
      [:eos:type
       (char-string (:Technique sensor))]
      [:eos:description {:gco:nilReason "missing"}]
      echo-attributes-info
      (generate-characteristics "sensorInformation" (:Characteristics sensor))]]))

(defn- generate-instruments
  "Returns content generator instructions for the given instruments."
  [instruments]
  (for [instrument instruments]
    [:gmi:instrument
     [:eos:EOS_Instrument
      [:gmi:citation
       [:gmd:CI_Citation
        [:gmd:title
         [:gco:CharacterString (generate-title instrument)]]
        [:gmd:date {:gco:nilReason "unknown"}]]]
      [:gmi:identifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (:ShortName instrument))]
        [:gmd:description
         (char-string (:LongName instrument))]]]
      [:gmi:type
       (char-string (:Technique instrument))]
      [:gmi:description {:gco:nilReason "missing"}]
      [:eos:otherPropertyType
       [:gco:RecordType {:xlink:href "http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])"}
        "Echo Additional Attributes"]]
      (generate-characteristics "instrumentInformation" (:Characteristics instrument))
      (generate-sensors (:Sensors instrument))]]))

(defn- generate-platforms
  "Returns the content generator instructions for the given platforms."
  [platforms]
  (for [platform platforms]
    [:gmi:platform
     [:eos:EOS_Platform
      [:gmi:identifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (:ShortName platform))]
        [:gmd:description
         (char-string (:LongName platform))]]]
      [:gmi:description (char-string (:Type platform))]
      (generate-instruments (:Instruments platform))

      ;; Characteristics
      (when-let [characteristics (:Characteristics platform)]
        [:eos:otherPropertyType
         [:gco:RecordType {:xlink:href "http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])"}
          "Echo Additional Attributes"]]
        (generate-characteristics "platformInformation" characteristics))]]))

(defn- generate-projects
  [projects]
  (for [proj projects]
    (let [{short-name :ShortName} proj]
      [:gmi:operation
       [:gmi:MI_Operation
        [:gmi:description
         (char-string (generate-title proj))]
        [:gmi:identifier
         [:gmd:MD_Identifier
          [:gmd:code
           (char-string short-name)]]]
        [:gmi:status ""]
        [:gmi:parentOperation {:gco:nilReason "inapplicable"}]]])))

(defn umm-c-to-iso19115-2-xml
  "Returns the generated ISO19115-2 xml from UMM collection record c."
  [c]
  (xml
    [:gmi:MI_Metadata
     iso19115-2-xml-namespaces
     [:gmd:fileIdentifier (char-string (:EntryTitle c))]
     [:gmd:language (char-string "eng")]
     [:gmd:characterSet
      [:gmd:MD_CharacterSetCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode"
                                 :codeListValue "utf8"} "utf8"]]
     [:gmd:hierarchyLevel
      [:gmd:MD_ScopeCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
                          :codeListValue "series"} "series"]]
     [:gmd:contact {:gco:nilReason "missing"}]
     [:gmd:dateStamp
      [:gco:DateTime "2014-08-25T15:25:44.641-04:00"]]
     [:gmd:metadataStandardName (char-string "ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data")]
     [:gmd:metadataStandardVersion (char-string "ISO 19115-2:2009(E)")]
     [:gmd:identificationInfo
      [:gmd:MD_DataIdentification
       [:gmd:citation
        [:gmd:CI_Citation
         [:gmd:title (char-string (:EntryTitle c))]
         (date-mapping "revision" "2000-12-31T19:00:00-05:00")
         (date-mapping "creation" "2000-12-31T19:00:00-05:00")
         [:gmd:identifier
          [:gmd:MD_Identifier
           [:gmd:code (char-string (:EntryId c))]
           [:gmd:version (char-string (:Version c))]]]]]
       [:gmd:abstract (char-string (:Abstract c))]
       [:gmd:purpose {:gco:nilReason "missing"} (char-string (:Purpose c))]
       [:gmd:status
        (when-let [collection-progress (:CollectionProgress c)]
          [:gmd:MD_ProgressCode
           {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ProgressCode"
            :codeListValue (str/lower-case collection-progress)}
           collection-progress])]
       (when-let [projects (:Projects c)]
         [:gmd:descriptiveKeywords (generate-projects-keywords projects)])
       (when-let [spatial-keywords (:SpatialKeywords c)]
         [:gmd:descriptiveKeywords (generate-descriptive-keywords spatial-keywords "place")])
       (when-let [temporal-keywords (:TemporalKeywords c)]
         [:gmd:descriptiveKeywords (generate-descriptive-keywords temporal-keywords "temporal")])
       [:gmd:resourceConstraints
        [:gmd:MD_LegalConstraints
         [:gmd:useLimitation (char-string (:UseConstraints c))]
         [:gmd:useLimitation
          [:gco:CharacterString (str "Restriction Comment:" (-> c :AccessConstraints :Description))]]
         [:gmd:otherConstraints
          [:gco:CharacterString (str "Restriction Flag:" (-> c :AccessConstraints :Value))]]]]
       [:gmd:language (char-string (:DataLanguage c))]
       [:gmd:extent
        [:gmd:EX_Extent
         (for [temporal (:TemporalExtents c)
               rdt (:RangeDateTimes temporal)]
           [:gmd:temporalElement
            [:gmd:EX_TemporalExtent
             [:gmd:extent
              [:gml:TimePeriod {:gml:id (gen-id)}
               [:gml:beginPosition (:BeginningDateTime rdt)]
               [:gml:endPosition (:EndingDateTime rdt)]]]]])
         (for [temporal (:TemporalExtents c)
               date (:SingleDateTimes temporal)]
           [:gmd:temporalElement
            [:gmd:EX_TemporalExtent
             [:gmd:extent
              [:gml:TimeInstant {:gml:id (gen-id)}
               [:gml:timePosition date]]]]])]]
       [:gmd:processingLevel
        [:gmd:MD_Identifier
         [:gmd:code (char-string (-> c :ProcessingLevel :Id))]
         [:gmd:description (char-string (-> c :ProcessingLevel :ProcessingLevelDescription))]]]]]
     [:gmd:contentInfo
      [:gmd:MD_ImageDescription
       [:gmd:attributeDescription ""]
       [:gmd:contentType ""]
       [:gmd:processingLevelCode
        [:gmd:MD_Identifier
         [:gmd:code (char-string (-> c :ProcessingLevel :Id))]
         [:gmd:description (char-string (-> c :ProcessingLevel :ProcessingLevelDescription))]]]]]
     [:gmd:dataQualityInfo
      [:gmd:DQ_DataQuality
       [:gmd:scope
        [:gmd:DQ_Scope
         [:gmd:level
          [:gmd:MD_ScopeCode
           {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
            :codeListValue "series"}
           "series"]]]]
       [:gmd:report
        [:gmd:DQ_AccuracyOfATimeMeasurement
         [:gmd:measureIdentification
          [:gmd:MD_Identifier
           [:gmd:code
            (char-string "PrecisionOfSeconds")]]]
         [:gmd:result
          [:gmd:DQ_QuantitativeResult
           [:gmd:valueUnit ""]
           [:gmd:value
            [:gco:Record {:xsi:type "gco:Real_PropertyType"}
             [:gco:Real (:PrecisionOfSeconds (first (:TemporalExtents c)))]]]]]]]]]
     [:gmi:acquisitionInformation
      [:gmi:MI_AcquisitionInformation
       (generate-projects (:Projects c))
       (generate-platforms (:Platforms c))]]]))

