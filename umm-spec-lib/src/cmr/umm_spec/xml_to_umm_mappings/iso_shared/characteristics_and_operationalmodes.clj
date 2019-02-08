(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.characteristics-and-operationalmodes
  "Functions for parsing UMM characteristics and operationalmodes records out of ISO SMAP XML documents."
  (:require
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]
   [cmr.umm-spec.xml-to-umm-mappings.characteristics-data-type-normalization :as char-data-type-normalization]))

(def characteristics-and-operationalmodes-xpath
  "eos:otherProperty/gco:Record/eos:AdditionalAttributes/eos:AdditionalAttribute")

(def pc-attr-base-path
  "eos:reference/eos:EOS_AdditionalAttributeDescription")

(defn parse-characteristics
  "Returns the parsed characteristics from the element."
  [element]
  (seq (remove nil?
        (map char-data-type-normalization/normalize-data-type
          (remove nil?
            (for [chars (select element characteristics-and-operationalmodes-xpath)]
              (when-not (= "OperationalMode" (char-string-value chars (str pc-attr-base-path "/eos:name")))
                {:Name        (char-string-value chars (str pc-attr-base-path "/eos:name"))
                 :Description (char-string-value chars (str pc-attr-base-path "/eos:description"))
                 :DataType    (value-of chars (str pc-attr-base-path "/eos:dataType/eos:EOS_AdditionalAttributeDataTypeCode"))
                 :Unit        (char-string-value chars (str pc-attr-base-path "/eos:parameterUnitsOfMeasure"))
                 :Value       (char-string-value chars (str "eos:value"))})))))))

(defn parse-operationalmodes
  "Returns the parsed operationalmodes from the element."
  [element]
  (seq
    (remove nil?
      (for [chars (select element characteristics-and-operationalmodes-xpath)]
        (when (= "OperationalMode" (char-string-value chars (str pc-attr-base-path "/eos:name")))
          (char-string-value chars (str "eos:value")))))))
