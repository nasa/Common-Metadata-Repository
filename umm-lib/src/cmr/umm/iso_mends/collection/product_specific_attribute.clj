(ns cmr.umm.iso-mends.collection.product-specific-attribute
  "Contains functions for parsing and generating the ISO MENDS product specific attributes"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-mends.collection.helper :as h]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(defn xml-elem->ProductSpecificAttribute
  "Returns the parsed product specific attribute from the given xml element"
  [psa-elem]
  (let [name (cx/string-at-path
               psa-elem [:reference :EOS_AdditionalAttributeDescription :name :CharacterString])
        description (cx/string-at-path
                      psa-elem [:reference :EOS_AdditionalAttributeDescription :description :CharacterString])
        description-with-default (if (empty? description)
                                   c/not-provided
                                   description)
        data-type (psa/parse-data-type
                    (cx/string-at-path
                      psa-elem [:reference :EOS_AdditionalAttributeDescription :dataType
                                :EOS_AdditionalAttributeDataTypeCode]))
        begin (cx/string-at-path
                psa-elem [:reference :EOS_AdditionalAttributeDescription :parameterRangeBegin :CharacterString])
        end (cx/string-at-path
              psa-elem [:reference :EOS_AdditionalAttributeDescription :parameterRangeEnd :CharacterString])
        value (cx/string-at-path psa-elem [:value :CharacterString])]
    (c/map->ProductSpecificAttribute
      {:name name
       :description description-with-default
       :data-type data-type
       :parameter-range-begin begin
       :parameter-range-end end
       :value value
       :parsed-parameter-range-begin (psa/safe-parse-value data-type begin)
       :parsed-parameter-range-end (psa/safe-parse-value data-type end)
       :parsed-value (psa/safe-parse-value data-type value)})))

(defn xml-elem->ProductSpecificAttributes
  "Returns the parsed product specific attributes from the collection element"
  [collection-element]
  (seq (map xml-elem->ProductSpecificAttribute
            (cx/elements-at-path
              collection-element
              [:dataQualityInfo :DQ_DataQuality :lineage :LI_Lineage :processStep :LE_ProcessStep
               :processingInformation :EOS_Processing :otherProperty :Record
               :AdditionalAttributes :AdditionalAttribute]))))

(defn- string-element-if-exist
  "Returns the ISO string element if the value is not nil"
  [name-key value]
  (when (some? value) (h/iso-string-element name-key value)))

(def ^:private type-element
  "Defines the ISO19115 element for product specific attribute type.
  This is a placeholder element to pass the schema validation.
  Hard coded with a value since we don't use it in umm-lib and umm-lib is going away soon."
  (x/element
        :eos:type {}
        (x/element
          :eos:EOS_AdditionalAttributeTypeCode
          {:codeList "http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode"
           :codeListValue "processingInformation"}
          "processingInformation")))

(defn- generate-data-type
  "Returns the ISO19115 element for product specific attribute data type"
  [data-type]
  (when (some? data-type)
    (let [data-type (psa/gen-data-type data-type)]
      (x/element
        :eos:dataType {}
        (x/element
          :eos:EOS_AdditionalAttributeDataTypeCode
          {:codeList "http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode"
           :codeListValue data-type}
          data-type)))))

(defn generate-product-specific-attributes
  "Returns the ISO 19115 product specific attributes element containing the given product
  specific attributes"
  [psas]
  (when (seq psas)
    (x/element
      :gmi:processingInformation {}
      (x/element
        :eos:EOS_Processing {}
        (x/element :gmi:identifier {})
        (x/element
          :eos:otherProperty {}
          (x/element
            :gco:Record {}
            (x/element
              :eos:AdditionalAttributes {}
              (for [{:keys [data-type name description parameter-range-begin
                            parameter-range-end value]} psas]
                (x/element
                  :eos:AdditionalAttribute {}
                  (x/element
                    :eos:reference {}
                    (x/element
                      :eos:EOS_AdditionalAttributeDescription {}
                      type-element
                      (string-element-if-exist :eos:name name)
                      (string-element-if-exist :eos:description description)
                      (generate-data-type data-type)
                      (string-element-if-exist :eos:parameterRangeBegin parameter-range-begin)
                      (string-element-if-exist :eos:parameterRangeEnd parameter-range-end)))
                  (string-element-if-exist :eos:value value))))))))))
