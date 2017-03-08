(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute
  "Functions for parsing UMM additional attribute records out of ISO19115-2 XML documents."
  (:require
   [clojure.string :as s]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]
   [cmr.umm-spec.util :as su]))

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
  [aas sanitize?]
  (when aas
    (for [aa aas
          ;; ISO19115 schema validation does not catch maxOccurs errors on reference elements.
          ;; Some ISO19115 xml may have more than one eos:EOS_AdditionalAttributeDescription
          ;; elements but still passes validation. See CMR-3832.
          ;; Here we just parse out all descriptions as additional attributes.
          description (select aa additional-attribute-xpath)]
      {:Group (char-string-value description "eos:identifier/gmd:MD_Identifier/gmd:code")
       :Name (char-string-value description "eos:name")
       :DataType (when-let [data-type (value-of description "eos:dataType/eos:EOS_AdditionalAttributeDataTypeCode")]
                   (s/trim data-type))
       :Value (char-string-value aa "eos:value")
       :Description (su/with-default (char-string-value description "eos:description") sanitize?)
       :MeasurementResolution (char-string-value description "eos:measurementResolution")
       :ParameterRangeBegin (char-string-value description "eos:parameterRangeBegin")
       :ParameterRangeEnd (char-string-value description "eos:parameterRangeEnd")
       :ParameterUnitsOfMeasure (char-string-value description "eos:parameterUnitsOfMeasure")
       :ParameterValueAccuracy (char-string-value description "eos:parameterValueAccuracy")
       :ValueAccuracyExplanation (char-string-value
                                  description "eos:valueAccuracyExplanation")})))

(defn- parse-content-info-additional-attributes
  "Returns the additional attributes parsed from contentInfo path of the given xml document."
  [doc sanitize?]
  (elements->additional-attributes (select doc content-info-base-xpath) sanitize?))

(defn- parse-data-quality-info-additional-attributes
  "Returns the additional attributes parsed from dataQualityInfo path of the given xml document."
  [doc sanitize?]
  (elements->additional-attributes (select doc data-quality-info-base-xpath) sanitize?))

(defn parse-additional-attributes
  "Returns the parsed additional attributes from the given xml document."
  [doc sanitize?]
  (concat (parse-content-info-additional-attributes doc sanitize?)
          (parse-data-quality-info-additional-attributes doc sanitize?)))
