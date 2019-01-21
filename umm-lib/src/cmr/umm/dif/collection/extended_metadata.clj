(ns cmr.umm.dif.collection.extended-metadata
  "Provide functions to parse and generate DIF Extended_Metadata elements."
  (:require
   [clojure.data.xml :as x]
   [cmr.common.xml :as cx]
   [cmr.umm.collection.product-specific-attribute :as psa]))

(def product_level_id_external_meta_name
  "ProductLevelId")

(def collection_data_type_external_meta_name
  "CollectionDataType")

(def spatial_coverage_external_meta_name
  "GranuleSpatialRepresentation")

(def restriction_flag_external_meta_name
  "Restriction")

(def additional_attribute_external_meta_name
  "AdditionalAttribute")

(def processing_center_external_meta_name
  "Processor")

(def non-additional-attributes
  "Set of extended metadata names which do not map to additional attributes"
  #{product_level_id_external_meta_name collection_data_type_external_meta_name
    spatial_coverage_external_meta_name restriction_flag_external_meta_name
    processing_center_external_meta_name})

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
  "Returns everything from the Extended_Metadata within the collection XML as UMM additional
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

(defn xml-elem->access-value
  "Returns the access value from a parsed Collection XML structure"
  [xml-struct]
  (let [access-value (extended-metadata-value xml-struct restriction_flag_external_meta_name)]
    (when access-value
      (Double/parseDouble access-value))))

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
