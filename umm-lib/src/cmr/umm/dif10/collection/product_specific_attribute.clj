(ns cmr.umm.dif10.collection.product-specific-attribute
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.generator-util :as gu]
            [cmr.umm.dif.dif-core :as dif]
            [cmr.umm.collection.product-specific-attribute :as psa]
            [cmr.umm.dif.collection.product-specific-attribute :as d9-psa]))

(defn xml-elem->ProductSpecificAttribute
  [psa-elem]
  (let [name (cx/string-at-path psa-elem [:Name])
        description (cx/string-at-path psa-elem [:Description])
        data-type (psa/parse-data-type (cx/string-at-path psa-elem [:DataType]))
        begin (cx/string-at-path psa-elem [:ParameterRangeBegin])
        end (cx/string-at-path psa-elem [:ParameterRangeEnd])
        value (cx/string-at-path psa-elem [:Value])]
    (c/map->ProductSpecificAttribute
      {:name name
       :description description
       :data-type data-type
       :parameter-range-begin begin
       :parameter-range-end end
       :value value
       :parsed-parameter-range-begin (psa/safe-parse-value data-type begin)
       :parsed-parameter-range-end (psa/safe-parse-value data-type end)
       :parsed-value (psa/safe-parse-value data-type value)})))

(defn xml-elem->ProductSpecificAttributes
  "Extracts Additional_Attributes and Extended_Metadata from DIF10 XML and includes both
  concatenated together as UMM AdditionalAttributes"
  [collection-element]
  (let [additional_attributes (mapv xml-elem->ProductSpecificAttribute
                                   (cx/elements-at-path collection-element
                                                        [:Additional_Attributes]))
        extended_metadata (d9-psa/xml-elem->ProductSpecificAttributes collection-element)]

    (seq (into additional_attributes extended_metadata))))

(defn generate-product-specific-attributes
  [psas]
  (for [psa psas]
    (let [{:keys [data-type name description parameter-range-begin parameter-range-end value]} psa
          description (if (empty? description)
                        c/not-provided
                        description)]
      (x/element :Additional_Attributes {}
                 (x/element :Name {} name)
                 (x/element :DataType {} (psa/gen-data-type data-type))
                 (x/element :Description {} description)
                 (gu/optional-elem :ParameterRangeBegin parameter-range-begin)
                 (gu/optional-elem :ParameterRangeEnd parameter-range-end)
                 (gu/optional-elem :Value value)))))
