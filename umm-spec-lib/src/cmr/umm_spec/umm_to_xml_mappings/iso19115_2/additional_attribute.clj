(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.additional-attribute
  "Functions for generating ISO19115-2 XML elements from UMM additional attribute records."
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]))

(defn generate-additional-attributes
  "Returns the content generator instructions for generating ISO19115 additional attributes."
  [aas]
  (when (seq aas)
    [:gmd:contentInfo
     [:gmd:MD_CoverageDescription
      [:gmd:attributeDescription {:gco:nilReason "missing"}]
      [:gmd:contentType
       [:gmd:MD_CoverageContentTypeCode
        {:codeList (str (:ngdc iso/code-lists) "#MD_CoverageContentTypeCode")
         :codeListValue "physicalMeasurement"} "physicalMeasurement"]]
      [:gmd:dimension
       [:gmd:MD_Band
        iso/gmd-echo-attributes-info
        [:gmd:otherProperty
         [:gco:Record
          [:eos:AdditionalAttributes
           (for [aa aas]
             [:eos:AdditionalAttribute
              [:eos:reference
               [:eos:EOS_AdditionalAttributeDescription
                [:eos:type
                 [:eos:EOS_AdditionalAttributeTypeCode
                  {:codeList (str (:earthdata iso/code-lists) "#EOS_AdditionalAttributeTypeCode")
                   :codeListValue "contentInformation"} "contentInformation"]]
                (when-let [group (:Group aa)]
                  [:eos:identifier
                   [:gmd:MD_Identifier
                    [:gmd:code
                     (char-string group)]]])
                [:eos:name
                 (char-string (:Name aa))]
                [:eos:description
                 (char-string (:Description aa))]
                [:eos:dataType
                 [:eos:EOS_AdditionalAttributeDataTypeCode
                  {:codeList (str (:earthdata iso/code-lists) "#EOS_AdditionalAttributeDataTypeCode")
                   :codeListValue (:DataType aa)} (:DataType aa)]]
                [:eos:measurementResolution
                 (char-string (:MeasurementResolution aa))]
                [:eos:parameterRangeBegin
                 (char-string (:ParameterRangeBegin aa))]
                [:eos:parameterRangeEnd
                 (char-string (:ParameterRangeEnd aa))]
                [:eos:parameterUnitsOfMeasure
                 (char-string (:ParameterUnitsOfMeasure aa))]
                [:eos:parameterValueAccuracy
                 (char-string (:ParameterValueAccuracy aa))]
                [:eos:valueAccuracyExplanation
                 (char-string (:ValueAccuracyExplanation aa))]]]
              (when-let [value (:Value aa)]
                [:eos:value
                 (char-string value)])])]]]]]]]))


