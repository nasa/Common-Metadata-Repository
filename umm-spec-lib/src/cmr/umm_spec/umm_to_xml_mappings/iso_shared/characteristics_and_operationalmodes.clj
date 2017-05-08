(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.characteristics-and-operationalmodes
  "Functions for generating ISOSMAP XML elements from UMM characteristics and operationalmodes records."
  (:require
    [cmr.common.xml.gen :refer :all]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.util :refer [char-string]]))

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

(defn generate-operationalmodes
  "Returns the generated operationalmodes content generator instructions, with the type
  argument used for the EOS_AdditionalAttributeTypeCode codeListValue attribute value and content."
  [type operationalmodes]
  [:eos:otherProperty
   [:gco:Record
    [:eos:AdditionalAttributes
     (for [ops-mode operationalmodes]
       [:eos:AdditionalAttribute
        [:eos:reference
         [:eos:EOS_AdditionalAttributeDescription
          [:eos:type
           [:eos:EOS_AdditionalAttributeTypeCode
            {:codeList (str (:earthdata iso/code-lists) "#EOS_AdditionalAttributeTypeCode")
             :codeListValue type}
            type]]
          [:eos:name
           (char-string "OperationalMode")]]]
        [:eos:value
         (char-string ops-mode)]])]]])
