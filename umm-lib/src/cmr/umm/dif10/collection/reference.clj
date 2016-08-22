(ns cmr.umm.dif10.collection.reference
  "Functions to parse and generate DIF10 Reference elements"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.umm-collection :as c]))

(def umm-dif-publication-reference-mappings
  "A seq of [umm-key dif-tag-name] which maps between the UMM
  PublicationReference fields and the DIF Reference XML element."
  (map (fn [x]
         (if (keyword? x)
           [(csk/->kebab-case-keyword x) x]
           x))
       [:Author
        :Publication_Date
        :Title
        :Series
        :Edition
        :Volume
        :Issue
        :Report_Number
        :Publication_Place
        :Publisher
        :Pages
        :ISBN
        ;; DIF 10 has two different types of identifiers DOI and ARK, UMM needs to be updated
        ;; [:doi [:Persistent_Identifier :Identifier]]. CMRIN-76
        [:related-url :Online_Resource]
        :Other_Reference_Details]))

(defn- xml-elem->Reference
  [reference]
  (c/map->PublicationReference
    (into {} (for [[umm-key dif-tag] umm-dif-publication-reference-mappings]
               [umm-key (cx/string-at-path reference [dif-tag])]))))

(defn xml-elem->References
  [collection-element]
  (seq (map xml-elem->Reference
            (cx/elements-at-path collection-element [:Reference]))))

(defn generate-references
  "Returns a seq of DIF 10 Reference elements from a seq of UMM publication references."
  [references]
  (for [reference references]
    (x/element :Reference {}
               (for [[umm-key dif-tag] umm-dif-publication-reference-mappings
                     :let [v (get reference umm-key)]
                     :when v]
                 (x/element dif-tag {} v)))))

(defn xml-elem->Citations
  "Returns a list of collection citations which are strings."
  [collection-element]
  (seq (cx/strings-at-path collection-element [:Dataset_Citation :Other_Citation_Details])))

(defn generate-dataset-citations
  "Generates a dataset citation given a umm-collection map"
  [collection]
  (for [citation (:collection-citations collection)]
    (x/element :Dataset_Citation {}
               (x/element :Other_Citation_Details {} citation))))
