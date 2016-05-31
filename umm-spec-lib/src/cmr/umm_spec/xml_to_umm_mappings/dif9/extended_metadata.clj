(ns cmr.umm-spec.xml-to-umm-mappings.dif9.extended-metadata
  "Provide functions to parse and generate DIF Extended_Metadata elements."
  (:require [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.common.xml.parse :refer :all]))

(def product_level_id_external_meta_name
  "ProcessingLevelId")

(def product_level_desc_external_meta_name
  "ProcessingLevelDescription")

(def collection_data_type_external_meta_name
  "CollectionDataType")

(def spatial_coverage_external_meta_name
  "GranuleSpatialRepresentation")

(def restriction_flag_external_meta_name
  "Restriction")

(def additional_attribute_external_meta_name
  "AdditionalAttribute")

(def non-additional-attributes
  "Set of extended metadata names which do not map to additional attributes"
  #{product_level_id_external_meta_name product_level_desc_external_meta_name
    collection_data_type_external_meta_name spatial_coverage_external_meta_name
    restriction_flag_external_meta_name})

(defn- xml-elem->additional-attribute
  "Translates extended metadata element to a UMM additional attribute. DIF 9 extended metadata does
  not support the concept of ranges for values."
  [extended-elem]
  {:Group (value-of extended-elem "Group")
   :Name (value-of extended-elem "Name")
   :Description (value-of extended-elem "Description")
   :DataType (value-of extended-elem "Type")
   :UpdateDate (value-of extended-elem "Update_Date")
   :Value (value-of extended-elem "Value")})

(defn- xml-elem->potential-additional-attributes
  "Returns everything from the Extended_Metadata within the collection XML as UMM additional
  attributes."
  [doc]
  (map xml-elem->additional-attribute
       (select doc "/DIF/Extended_Metadata/Metadata")))

(defn xml-elem->additional-attributes
  "Returns the parsed additional attributes from the collection XML. Filters out anything from the
  Extended_Metadata section which maps to other fields within the UMM other than additional
  attributes."
  [doc]
  (remove #(contains? non-additional-attributes (:Name %))
          (xml-elem->potential-additional-attributes doc)))

