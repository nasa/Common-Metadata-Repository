(ns cmr.umm.iso-mends.collection.org
  "Archive and Processing Center elements of ISO are mapped umm organization elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-mends.collection.keyword :as k]
            [cmr.umm.iso-mends.collection.helper :as h]))

(defn xml-elem->Organizations
  "Return organizations or []"
  [collection-element]
  (let [id-elem (cx/element-at-path collection-element [:identificationInfo :MD_DataIdentification])
        archive-ctr (k/xml-elem->data-center id-elem)
        processing-ctr (cx/string-at-path
                         collection-element
                         [:dataQualityInfo :DQ_DataQuality :lineage :LI_Lineage :processStep :LE_ProcessStep
                          :processor :CI_ResponsibleParty :organisationName :CharacterString])]
    (seq (concat
           (when processing-ctr
             [(c/map->Organization {:type :processing-center :org-name processing-ctr})])
           (when archive-ctr
             [(c/map->Organization {:type :archive-center :org-name archive-ctr})])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn get-organization-name
  "Returns the ogranization name of the given type from the list of organizations"
  [type orgs]
  (:org-name (first (filter #(= type (:type %)) orgs))))

(defn generate-archive-center
  "Generate archive center element"
  [archive-center]
  (k/generate-keywords "dataCenter" archive-center))

(defn generate-processing-center
  "Return processing center ignoring other type of organization like archive center"
  [orgs]
  (let [processing-center (get-organization-name :processing-center orgs)]
    (x/element
      :gmd:processor {}
      (x/element
        :gmd:CI_ResponsibleParty {}
        (h/iso-string-element :gmd:organisationName processing-center)
        (x/element
          :gmd:role {}
          (x/element
            :gmd:CI_RoleCode (h/role-code-attributes "processor") "processor"))))))




