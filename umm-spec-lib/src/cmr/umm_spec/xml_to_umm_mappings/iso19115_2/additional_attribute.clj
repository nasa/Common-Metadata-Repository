(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute
  "Functions for parsing UMM additional attribute records out of ISO19115-2 XML documents."
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]))

(def content-info-base-xpath
  "Defines the base xpath for the ISO additional Attributes of content information type."
  (str "/gmi:MI_Metadata/gmd:contentInfo/gmd:MD_CoverageDescription/gmd:dimension/gmd:MD_Band"
       "/gmd:otherProperty/gco:Record/eos:AdditionalAttributes/eos:AdditionalAttribute"))

(def content-info-attribute-xpath
  "Defineds the base xpath within the individual additional attribute of content info type."
  "eos:reference/eos:EOS_AdditionalAttributeDescription")

(defn parse-additional-attributes
  "Returns the parsed additional attributes from the given xml document."
  [doc]
  (when-let [aas (select doc content-info-base-xpath)]
    (for [aa aas]
      {:Group (char-string-value
                aa (str content-info-attribute-xpath "/eos:identifier/gmd:MD_Identifier/gmd:code"))
       :Name (char-string-value aa (str content-info-attribute-xpath "/eos:name"))
       :DataType (value-of aa (str content-info-attribute-xpath
                                   "/eos:dataType/eos:EOS_AdditionalAttributeDataTypeCode"))
       :Value (char-string-value aa "eos:value")
       :Description (char-string-value aa (str content-info-attribute-xpath "/eos:description"))
       :MeasurementResolution (char-string-value aa (str content-info-attribute-xpath
                                                         "/eos:measurementResolution"))
       :ParameterRangeBegin (char-string-value aa (str content-info-attribute-xpath
                                                       "/eos:parameterRangeBegin"))
       :ParameterRangeEnd (char-string-value aa (str content-info-attribute-xpath
                                                     "/eos:parameterRangeEnd"))
       :ParameterUnitsOfMeasure (char-string-value aa (str content-info-attribute-xpath
                                                           "/eos:parameterUnitsOfMeasure"))
       :ParameterValueAccuracy (char-string-value aa (str content-info-attribute-xpath
                                                          "/eos:parameterValueAccuracy"))
       :ValueAccuracyExplanation (char-string-value aa (str content-info-attribute-xpath
                                                            "/eos:valueAccuracyExplanation"))})))
