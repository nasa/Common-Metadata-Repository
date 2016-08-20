(ns cmr.umm.echo10.granule.product-specific-attribute-ref
  "Contains functions for parsing and generating the ECHO10 dialect for product specific attribute refs"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-granule :as g]))


(defn xml-elem->ProductSpecificAttributeRef
  [psa-elem]
  (let [name (cx/string-at-path psa-elem [:Name])
        values (cx/strings-at-path psa-elem [:Values :Value])]
    (g/->ProductSpecificAttributeRef name values)))

(defn xml-elem->ProductSpecificAttributeRefs
  [granule-element]
  (let [psas (map xml-elem->ProductSpecificAttributeRef
                  (cx/elements-at-path
                    granule-element
                    [:AdditionalAttributes :AdditionalAttribute]))]
    (when (not (empty? psas))
      psas)))

(defn generate-product-specific-attribute-refs
  [psas]
  (when (not (empty? psas))
    (x/element
      :AdditionalAttributes {}
      (for [{:keys [name values]} psas]
        (x/element :AdditionalAttribute {}
                   (x/element :Name {} name)
                   (x/element :Values {}
                              (for [value values]
                                (x/element :Value {} value))))))))

