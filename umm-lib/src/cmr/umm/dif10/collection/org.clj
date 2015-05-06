(ns cmr.umm.dif10.collection.org
  "Data Center elements of DIF10 are mapped to umm organization elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.collection :as c]))

(def organization-types {:distributor :distribution-center
                         :archiver :archive-center
                         ; :originator ?
                         :processor :processing-center})

(defn xml-elem->Organization
  [org-elem]
  (let [org-type (csk/->kebab-case-keyword (cx/string-at-path org-elem [:Organization_Type]))
        org-name (cx/string-at-path org-elem [:Organization_Name :Long_Name])]
    (c/map->Organization {:type (org-type organization-types) :org-name org-name})))

(defn xml-elem->Organizations
  "Returns UMM Organizations from a parsed Collection XML structure"
  [xml-struct]
  (seq (map xml-elem->Organization
            (cx/elements-at-path xml-struct [:Organization]))))
