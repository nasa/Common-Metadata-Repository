(ns cmr.umm.dif.collection.extended-metadata
  "Provide functions to parse and generate DIF Extended_Metadata elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(def PRODUCT_LEVEL_ID_EXTERNAL_META_NAME
  "ProductLevelId")

(def COLLECTION_DATA_TYPE_EXTERNAL_META_NAME
  "CollectionDataType")

(def SPATIAL_COVERAGE_EXTERNAL_META_NAME
  "GranuleSpatialRepresentation")

(def ADDITIONAL_ATTRIBUTE_EXTERNAL_META_NAME
  "AdditionalAttribute")

(def non-additional-attributes
  "Set of extended metadata names which do not map to additional attributes"
  #{PRODUCT_LEVEL_ID_EXTERNAL_META_NAME COLLECTION_DATA_TYPE_EXTERNAL_META_NAME
    SPATIAL_COVERAGE_EXTERNAL_META_NAME})

(defn- string-value-with-attr
  "Returns the string value of the first element with the given attribute"
  [elements attr]
  (when-let [elem (first (filter #(= (name attr) (get-in % [:attrs :type])) elements))]
    (str (first (:content elem)))))

(defn- xml-elem->additional-attribute
  "Translates extended metadata element to a UMM additional attribute. DIF 9 extended metadata does
  not support the concept of ranges for values."
  [extended-elem]
  (let [group (cx/string-at-path extended-elem [:Group])
        name (cx/string-at-path extended-elem [:Name])
        description (cx/string-at-path extended-elem [:Description])
        data-type (cx/string-at-path extended-elem [:Type])
        value (cx/string-at-path extended-elem [:Value])]
    {:group group
     :name name
     :description description
     :data-type data-type
     :value value}))

(defn- xml-elem->potential-additional-attributes
  "Returns everyting from the Extended_Metadata within the collection XML as UMM additional
  attributes."
  [collection-element]
  (map xml-elem->additional-attribute
       (cx/elements-at-path
         collection-element
         [:Extended_Metadata :Metadata])))

(defn xml-elem->additional-attributes
  "Returns the parsed additional attributes from the collection XML. Filters out anything from the
  Extended_Metadata section which maps to other fields within the UMM other than additional
  attributes."
  [collection-element]
  (remove #(contains? non-additional-attributes (:name %))
          (xml-elem->potential-additional-attributes collection-element)))

(defn extended-metadata-value
  "Returns the single value of the extended metadata with the given name."
  [xml-struct extended-metadata-name]
  (when-let [ems (xml-elem->potential-additional-attributes xml-struct)]
    (when-let [elem (seq (filter #(= extended-metadata-name (:name %)) ems))]
      (:value (first elem)))))

(defn generate-metadata-elements
  "Generate a Metadata element based on the passed in metadata maps. The keys in the map are the
  same as the ones used in UMM additional attributes."
  [additional-attributes]
  (for [aa additional-attributes]
    (let [{:keys [group name description data-type value parameter-range-begin
                  parameter-range-end]} aa
          ;; DIF9 does not support ranges in Extended_Metadata - Order of preference for the value
          ;; is value, then parameter-range-begin, then parameter-range-end.
          value (or value parameter-range-begin parameter-range-end)]
      (x/element :Metadata {}
                 (when group (x/element :Group {} group))
                 (x/element :Name {} name)
                 (when description (x/element :Description {} description))
                 (x/element :Type {} (psa/gen-data-type data-type))
                 ;; false is a valid value
                 (when-not (nil? value)
                   (x/element :Value {} value))))))


