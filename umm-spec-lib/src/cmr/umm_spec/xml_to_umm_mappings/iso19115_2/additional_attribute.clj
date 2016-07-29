(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute
  "Functions for parsing UMM additional attribute records out of ISO19115-2 XML documents."
  (:require [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.common.xml.parse :refer :all]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]))

(def ^:private content-info-base-xpath
  "Defines the base xpath for the ISO additional Attributes of content information type."
  (str "/gmi:MI_Metadata/gmd:contentInfo/gmd:MD_CoverageDescription/gmd:dimension/gmd:MD_Band"
       "/gmd:otherProperty/gco:Record/eos:AdditionalAttributes/eos:AdditionalAttribute"))

(def ^:private data-quality-info-base-xpath
  "Defines the base xpath for the ISO additional Attributes of data quality information type."
  (str "/gmi:MI_Metadata/gmd:dataQualityInfo/gmd:DQ_DataQuality/gmd:lineage/gmd:LI_Lineage"
       "/gmd:processStep/gmi:LE_ProcessStep/gmi:processingInformation/eos:EOS_Processing"
       "/gmd:otherProperty/gco:Record/eos:AdditionalAttributes/eos:AdditionalAttribute"))

(def ^:private additional-attribute-xpath
  "Defineds the base xpath within the individual additional attribute element."
  "eos:reference/eos:EOS_AdditionalAttributeDescription")

(defn- elements->additional-attributes
  "Returns the additional attributes parsed from the given additional attributes elements."
  [aas]
  (when aas
    (for [aa aas]
      {:Group (char-string-value
                aa (str additional-attribute-xpath "/eos:identifier/gmd:MD_Identifier/gmd:code"))
       :Name (char-string-value aa (str additional-attribute-xpath "/eos:name"))
       :DataType (value-of aa (str additional-attribute-xpath
                                   "/eos:dataType/eos:EOS_AdditionalAttributeDataTypeCode"))
       :Value (char-string-value aa "eos:value")
       :Description (su/with-default (char-string-value aa (str additional-attribute-xpath "/eos:description")))
       :MeasurementResolution (char-string-value aa (str additional-attribute-xpath
                                                         "/eos:measurementResolution"))
       :ParameterRangeBegin (char-string-value aa (str additional-attribute-xpath
                                                       "/eos:parameterRangeBegin"))
       :ParameterRangeEnd (char-string-value aa (str additional-attribute-xpath
                                                     "/eos:parameterRangeEnd"))
       :ParameterUnitsOfMeasure (char-string-value aa (str additional-attribute-xpath
                                                           "/eos:parameterUnitsOfMeasure"))
       :ParameterValueAccuracy (char-string-value aa (str additional-attribute-xpath
                                                          "/eos:parameterValueAccuracy"))
       :ValueAccuracyExplanation (char-string-value aa (str additional-attribute-xpath
                                                            "/eos:valueAccuracyExplanation"))})))

(defn- parse-content-info-additional-attributes
  "Returns the additional attributes parsed from contentInfo path of the given xml document."
  [doc]
  (elements->additional-attributes (select doc content-info-base-xpath)))

(defn- parse-data-quality-info-additional-attributes
  "Returns the additional attributes parsed from dataQualityInfo path of the given xml document."
  [doc]
  (elements->additional-attributes (select doc data-quality-info-base-xpath)))

(defn parse-additional-attributes
  "Returns the parsed additional attributes from the given xml document."
  [doc]
  (concat (parse-content-info-additional-attributes doc)
          (parse-data-quality-info-additional-attributes doc)))
