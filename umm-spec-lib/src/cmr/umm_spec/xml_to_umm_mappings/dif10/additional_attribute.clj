(ns cmr.umm-spec.xml-to-umm-mappings.dif10.additional-attribute
  (:require [cmr.common.xml.simple-xpath :refer [select]]
            [cmr.common.xml.parse :refer :all]
            [cmr.common.util :as util]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.xml-to-umm-mappings.dif9.additional-attribute :as d9-aa]))

(defn xml-elem->AdditionalAttribute
  [aa-elem sanitize?]
  (let [attribs {:Name (value-of aa-elem "Name")
                 :Description (su/with-default (value-of aa-elem "Description") sanitize?)
                 :DataType (value-of aa-elem "DataType")
                 :ParameterRangeBegin (value-of aa-elem "ParameterRangeBegin")
                 :ParameterRangeEnd (value-of aa-elem "ParameterRangeEnd")
                 :Value (value-of aa-elem "Value")}]
    (util/remove-nil-keys attribs)))

(defn xml-elem->AdditionalAttributes
  "Extracts Additional_Attributes and Extended_Metadata from DIF10 XML and includes both
  concatenated together as UMM AdditionalAttributes"
  [doc sanitize?]
  (let [additional-attributes (mapv #(xml-elem->AdditionalAttribute % sanitize?)
                                    (select doc "/DIF/Additional_Attributes"))
        extended-metadata (d9-aa/xml-elem->AdditionalAttributes doc sanitize?)]
    (seq (into additional-attributes extended-metadata))))
