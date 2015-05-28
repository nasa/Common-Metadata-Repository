(ns cmr.umm.dif10.collection.product-specific-attribute
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
  [collection-element]
  (seq (map xml-elem->ProductSpecificAttribute
            (cx/elements-at-path collection-element
                                 [:Additional_Attributes]))))

(defn generate-product-specific-attributes
  [psas]
  (for [psa psas]
    (let [{:keys [data-type name description parameter-range-begin parameter-range-end value]} psa]
      (x/element :Additional_Attributes {}
                 (x/element :Name {} name)
                 (x/element :DataType {} (psa/gen-data-type data-type))
                 (x/element :Description {} description)
                 ;; ParameterRangeBegin is a required field in DIF 10
                 (x/element :ParameterRangeBegin {} (or parameter-range-begin "Not provided"))
                 (gu/optional-elem :ParameterRangeEnd parameter-range-end)
                 (gu/optional-elem :Value value)))))
