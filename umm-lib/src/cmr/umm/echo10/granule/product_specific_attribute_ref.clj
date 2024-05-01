(ns cmr.umm.echo10.granule.product-specific-attribute-ref
  "Contains functions for parsing and generating the ECHO10 dialect for product specific attribute refs"
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-granule :as granule]))

(defn xml-elem->ProductSpecificAttributeRef
  [psa-elem]
  (let [name (cx/string-at-path psa-elem [:Name])
        values (cx/strings-at-path psa-elem [:Values :Value])]
    (granule/->ProductSpecificAttributeRef name values)))

(defn xml-elem->ProductSpecificAttributeRefs
  [granule-element]
  (let [psas (map xml-elem->ProductSpecificAttributeRef
                  (cx/elements-at-path
                    granule-element
                    [:AdditionalAttributes :AdditionalAttribute]))]
    (when (seq psas)
      psas)))

(defn generate-product-specific-attribute-refs
  [psas]
  (when (seq psas)
    (xml/element
      :AdditionalAttributes {}
      (for [{:keys [name values]} psas]
        (xml/element :AdditionalAttribute {}
                     (xml/element :Name {} name)
                     (xml/element :Values {}
                                  (for [value values]
                                    (xml/element :Value {} value))))))))
