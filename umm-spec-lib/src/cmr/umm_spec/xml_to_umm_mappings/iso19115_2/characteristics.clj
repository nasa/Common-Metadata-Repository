(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.characteristics
  "Functions for parsing UMM characteristics records out of ISO 19115-2 XML documents."
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.iso19115-util :refer [char-string-value]]))

(def characteristics-xpath
  "eos:otherProperty/gco:Record/eos:AdditionalAttributes/eos:AdditionalAttribute")

(def pc-attr-base-path
  "eos:reference/eos:EOS_AdditionalAttributeDescription")

(defn parse-characteristics
  "Returns the parsed platform characteristics from the platform element."
  [element]
  (for [chars (select element characteristics-xpath)]
    {:Name        (char-string-value chars (str pc-attr-base-path "/eos:name"))
     :Description (char-string-value chars (str pc-attr-base-path "/eos:description"))
     :DataType    (value-of chars (str pc-attr-base-path "/eos:dataType/eos:EOS_AdditionalAttributeDataTypeCode"))
     :Unit        (char-string-value chars (str pc-attr-base-path "/eos:parameterUnitsOfMeasure"))
     :Value       (char-string-value chars (str "eos:value"))}))