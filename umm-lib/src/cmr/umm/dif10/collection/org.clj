(ns cmr.umm.dif10.collection.org
  "Data Center elements of DIF10 are mapped to umm organization elements."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.set :as set]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]))

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
    (coll/map->Organization {:type (org-type dif10-umm-org-type-mapping)
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
    (xml/element :Organization {}
               (xml/element :Organization_Type {}
                          (string/upper-case
                            (name ((:type org)
                                   (set/map-invert dif10-umm-org-type-mapping)))))
               (xml/element :Organization_Name {}
                          (xml/element :Short_Name {} (:org-name org)))
               ;; Added since Personnel is a required field in DIF10. CMRIN-79
               (xml/element :Personnel {}
                          (xml/element :Role {} "DATA CENTER CONTACT")
                          (xml/element :Contact_Person {}
                                     (xml/element :Last_Name {} coll/not-provided))))))
