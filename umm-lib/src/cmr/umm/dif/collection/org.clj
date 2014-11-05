(ns cmr.umm.dif.collection.org
  "Data Center elements of DIF are mapped to umm organization elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.dif.collection.personnel :as pe]))

(defn xml-elem->Organization
  "Returns UMM Organization from a parsed Data_Center XML structure"
  [xml-struct]
  (let [short-name (cx/string-at-path xml-struct [:Data_Center_Name :Short_Name])
        personnel (pe/xml-elem->personnel xml-struct)]
    (c/map->Organization {:type :distribution-center
                          :org-name short-name
                          :personnel personnel})))

(defn xml-elem->Organizations
  "Returns UMM Organizations from a parsed Collection XML structure"
  [xml-struct]
  (when-let [centers (cx/elements-at-path xml-struct [:Data_Center])]
    (map xml-elem->Organization centers)))

(defn generate-data-center
  "Return archive or processing center based on org type"
  [orgs]
  (for [org orgs :when (= :distribution-center (:type org))]
    (x/element :Data_Center {}
               (x/element :Data_Center_Name {}
                          (x/element :Short_Name {} (:org-name org)))
               (pe/generate-personnel (:personnel org)))))
