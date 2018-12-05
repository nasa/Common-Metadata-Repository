(ns cmr.umm-spec.xml-to-umm-mappings.dif9.extended-metadata
  "Provide functions to parse and generate DIF Extended_Metadata elements."
  (:require
   [clj-time.format :as f]
   [clojure.string :as str]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]))

(defn- xml-elem->additional-attribute
  "Translates extended metadata element to a UMM additional attribute. DIF 9 extended metadata does
  not support the concept of ranges for values."
  [extended-elem]
  {:Group (value-of extended-elem "Group")
   :Name (value-of extended-elem "Name")
   :Description (value-of extended-elem "Description")
   :DataType (value-of extended-elem "Type")
   :UpdateDate (f/parse (value-of extended-elem "Update_Date"))
   :Value (value-of extended-elem "Value")})

(defn xml-elem->additional-attributes
  "Returns the parsed additional attributes from the collection XML. Only takes from the
   Extended_Metadata anything that is specifically flagged as an additional attribute."
  [doc]
  (for [metadata (select doc "/DIF/Extended_Metadata/Metadata")
        :let [group (value-of metadata "Group")]
        :when (and (some? group) (str/includes? (str/lower-case group) "additionalattribute"))]
    (xml-elem->additional-attribute metadata)))
