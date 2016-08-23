(ns cmr.umm.iso-smap.collection.org
  "Contains functions for parsing and generating the ISO SMAP Archive and Processing Center"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-smap.helper :as h]))

(defn- xml-elem->archive-center
  "Returns the archive center from parsed xml structure"
  [id-elems]
  (let [archive-ctr-elem (h/xml-elem-with-path-value
                           id-elems
                           [:pointOfContact :CI_ResponsibleParty :role :CI_RoleCode]
                           "distributor")]
    (cx/string-at-path
      archive-ctr-elem
      [:pointOfContact :CI_ResponsibleParty :organisationName :CharacterString])))

(defn- xml-elem->processing-center
  "Returns the processing center from parsed xml structure"
  [id-elems]
  (let [elems (mapcat #(cx/elements-at-path
                         %
                         [:citation :CI_Citation :citedResponsibleParty :CI_ResponsibleParty])
                      id-elems)
        proc-ctr-elem (h/xml-elem-with-path-value elems [:role :CI_RoleCode] "originator")]
    (cx/string-at-path proc-ctr-elem [:organisationName :CharacterString])))

(defn xml-elem->Organizations
  "Return organizations or []"
  [id-elems]
  (let [archive-ctr (xml-elem->archive-center id-elems)
        processing-ctr (xml-elem->processing-center id-elems)]
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
  [orgs]
  (when-let [archive-center (get-organization-name :archive-center orgs)]
    (x/element
      :gmd:pointOfContact {}
      (x/element
        :gmd:CI_ResponsibleParty {}
        (h/iso-string-element :gmd:organisationName archive-center)
        (x/element
          :gmd:role {}
          (x/element
            :gmd:CI_RoleCode (h/role-code-attributes "distributor") "distributor"))))))

(defn generate-processing-center
  "Return processing center ignoring other type of organization like archive center"
  [orgs]
  (when-let [processing-center (get-organization-name :processing-center orgs)]
    (x/element
      :gmd:citedResponsibleParty {}
      (x/element
        :gmd:CI_ResponsibleParty {}
        (h/iso-string-element :gmd:organisationName processing-center)
        (x/element
          :gmd:role {}
          (x/element
            :gmd:CI_RoleCode (h/role-code-attributes "originator") "originator"))))))




