(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.characteristics
  "Functions for generating ISO19115-2 XML elements from UMM characteristics records."
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.iso19115-util :as iso]))

(defn generate-characteristics
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
           [:eos:EOS_AdditionalAttributeTypeCode
            {:codeList (str (:earthdata iso/code-lists) "#EOS_AdditionalAttributeTypeCode")
             :codeListValue type}
            type]]
          [:eos:name
           (char-string (:Name characteristic))]
          [:eos:description
           (char-string (:Description characteristic))]
          [:eos:dataType
           [:eos:EOS_AdditionalAttributeDataTypeCode
            {:codeList (str (:earthdata iso/code-lists) "#EOS_AdditionalAttributeDataTypeCode")
             :codeListValue (:DataType characteristic)}
            (:DataType characteristic)]]
          [:eos:parameterUnitsOfMeasure
           (char-string (:Unit characteristic))]]]
        [:eos:value
         (char-string (:Value characteristic))]])]]])