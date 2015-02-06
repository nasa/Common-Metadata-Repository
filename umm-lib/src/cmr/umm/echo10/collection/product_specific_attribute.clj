(ns cmr.umm.echo10.collection.product-specific-attribute
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.generator-util :as gu]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(defn xml-elem->ProductSpecificAttribute
  [psa-elem]
  (let [name (cx/string-at-path psa-elem [:Name])
        description (cx/string-at-path psa-elem [:Description])
        data-type (psa/parse-data-type (cx/string-at-path psa-elem [:DataType]))
        begin (psa/parse-value data-type (cx/string-at-path psa-elem [:ParameterRangeBegin]))
        end (psa/parse-value data-type (cx/string-at-path psa-elem [:ParameterRangeEnd]))
        value (psa/parse-value data-type (cx/string-at-path psa-elem [:Value]))]
    (c/map->ProductSpecificAttribute
      {:name name :description description :data-type data-type
       :parameter-range-begin begin :parameter-range-end end
       :value value})))

(defn xml-elem->ProductSpecificAttributes
  [collection-element]
  (seq (map xml-elem->ProductSpecificAttribute
            (cx/elements-at-path collection-element
                                 [:AdditionalAttributes :AdditionalAttribute]))))

(defn generate-product-specific-attributes
  [psas]
  (when (seq psas)
    (x/element
      :AdditionalAttributes {}
      (for [psa psas]
        (let [{:keys [data-type name description parameter-range-begin parameter-range-end value]} psa]
          (x/element :AdditionalAttribute {}
                     (x/element :Name {} name)
                     (x/element :DataType {} (psa/gen-data-type data-type))
                     (x/element :Description {} description)
                     (gu/optional-elem :ParameterRangeBegin (psa/gen-value data-type parameter-range-begin))
                     (gu/optional-elem :ParameterRangeEnd (psa/gen-value data-type parameter-range-end))
                     (gu/optional-elem :Value (psa/gen-value data-type value))))))))

