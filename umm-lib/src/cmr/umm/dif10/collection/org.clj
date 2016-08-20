(ns cmr.umm.dif10.collection.org
  "Data Center elements of DIF10 are mapped to umm organization elements."
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [clojure.set :as set]
            [cmr.common.xml :as cx]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.dif.dif-core :as dif]
            [cmr.umm.umm-collection :as c]))

(def dif10-umm-org-type-mapping
  "Mapping of organization types between DIF10 and UMM. The keys are DIF10 organization
  types and the values are corresponding UMM organization types."
  {:distributor :distribution-center
   :archiver :archive-center
   :originator :originating-center
   :processor :processing-center})

(defn xml-elem->Organization
  [org-elem]
  (let [org-type (csk/->kebab-case-keyword (cx/string-at-path org-elem [:Organization_Type]))
        org-name (cx/string-at-path org-elem [:Organization_Name :Short_Name])]
    (c/map->Organization {:type (org-type dif10-umm-org-type-mapping)
                          :org-name org-name})))

(defn xml-elem->Organizations
  "Returns UMM Organizations from a parsed Collection XML structure"
  [xml-struct]
  (seq (map xml-elem->Organization
            (cx/elements-at-path xml-struct [:Organization]))))

(defn generate-organizations
  "Return archive or processing center based on org type"
  [orgs]
  (for [org orgs]
    (x/element :Organization {}
               (x/element :Organization_Type {}
                          (str/upper-case
                            (name ((:type org)
                                   (set/map-invert dif10-umm-org-type-mapping)))))
               (x/element :Organization_Name {}
                          (x/element :Short_Name {} (:org-name org)))
               ;; Added since Personnel is a required field in DIF10. CMRIN-79
               (x/element :Personnel {}
                          (x/element :Role {} "DATA CENTER CONTACT")
                          (x/element :Contact_Person {}
                                     (x/element :Last_Name {} c/not-provided))))))
