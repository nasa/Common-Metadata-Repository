(ns cmr.umm.dif.collection.org
  "Data Center elements of DIF are mapped to umm organization elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->Organizations
  "Returns UMM Organizations from a parsed Collection XML structure"
  [xml-struct]
  (when-let [centers (seq (cx/strings-at-path xml-struct [:Data_Center :Data_Center_Name :Short_Name]))]
    (map #(c/map->Organization {:type :distribution-center :org-name %})
         centers)))

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
