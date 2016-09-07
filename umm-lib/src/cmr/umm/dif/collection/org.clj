(ns cmr.umm.dif.collection.org
  "Data Center elements of DIF are mapped to umm organization elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]))

(defn- data-centers->organizations
  "Returns a list of organzitions from the Data_Center XML elements. For each Data_Center,
  create a distribution-center and archive-center organization"
  [xml-struct]
  (when-let [centers (seq (cx/strings-at-path xml-struct [:Data_Center :Data_Center_Name :Short_Name]))]
    (concat
     (map #(c/map->Organization {:type :distribution-center :org-name %})
          centers)
     (map #(c/map->Organization {:type :archive-center :org-name %})
          centers))))

(defn- extended-metadata->organizations
  "Returns a list of processing center organizations from extended-metadata XML"
  [xml-struct]
  (for [element (cx/elements-at-path xml-struct [:Extended_Metadata :Metadata])
        :when (= "Processor" (cx/string-at-path element [:Name]))]
    (c/map->Organization {:type :processing-center :org-name (cx/string-at-path element [:Value])})))

(defn xml-elem->Organizations
  "Returns UMM Organizations from a parsed Collection XML structure"
  [xml-struct]
  (concat
    (data-centers->organizations xml-struct)
    (extended-metadata->organizations xml-struct)))

(defn generate-data-center
  "Return archive or processing center based on org type"
  [orgs]
  (for [org orgs :when (= :distribution-center (:type org))]
    (x/element :Data_Center {}
               (x/element :Data_Center_Name {}
                          (x/element :Short_Name {} (:org-name org)))
               ;; stubbed personnel
               (x/element :Personnel {}
                          (x/element :Role {} c/not-provided)
                          (x/element :Last_Name {} c/not-provided)))))

(defn generate-metadata
  "Processing centers are stored as Extended Metadata. Generate the metadata elements for each
  processing center. Use a default group name. Must end with .processor"
  [orgs]
  (for [org orgs :when (= :processing-center (:type org))]
    (x/element :Metadata {}
               (x/element :Group {} "gov.nasa.esdis.cmr.processor")
               (x/element :Name {} "Processor")
               (x/element :Value {} (:org-name org)))))
