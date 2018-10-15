(ns cmr.umm-spec.umm-g.additional-attribute
  "Contains functions for parsing UMM-G JSON AdditionalAttributes into umm-lib granule model
  ProductSpecificAttributeRefs and generating UMM-G JSON AdditionalAttributes from umm-lib
  granule model ProductSpecificAttributeRefs."
  (:require
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.ProductSpecificAttributeRef))

(defn- umm-g-additional-attribute->ProductSpecificAttributeRef
  "Returns the umm-lib granule model ProductSpecificAttributeRef from the given
  UMM-G AdditionalAttribute."
  [additional-attribute]
  (g/map->ProductSpecificAttributeRef
    {:name (:Name additional-attribute)
     :values (:Values additional-attribute)}))

(defn umm-g-additional-attributes->ProductSpecificAttributeRefs
  "Returns the umm-lib granule model ProductSpecificAttributeRefs from the given
  UMM-G AdditionalAttributes."
  [additional-attributes]
  (seq (map umm-g-additional-attribute->ProductSpecificAttributeRef additional-attributes)))

(defn ProductSpecificAttributeRefs->umm-g-additional-attributes
  "Returns the UMM-G AdditionalAttributes from the given umm-lib granule model
  ProductSpecificAttributeRefs."
  [additional-attributes]
  (when (seq additional-attributes)
    (for [additional-attribute additional-attributes]
      {:Name (:name additional-attribute)
       :Values (:values additional-attribute)})))
