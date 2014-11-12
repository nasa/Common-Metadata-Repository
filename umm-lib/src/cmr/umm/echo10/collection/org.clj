(ns cmr.umm.echo10.collection.org
  "Archive and Processing Center elements of echo10 are mapped umm organization elements."
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.echo10.collection.personnel :as pe]
            [camel-snake-kebab :as csk]))

(defn- contacts-xml->Organizations
  "Return organizations for the contacts in the parsed xml structure."
  [contact-elements]
  (for [contact-element contact-elements]
    (let [role (keyword (cx/string-at-path contact-element [:Role]))
          org-name (cx/string-at-path contact-element [:OrganizationName])
          personnel (pe/xml-elem->personnel contact-element)]
      (c/map->Organization {:org-type role
                            :org-name org-name
                            :personnel personnel}))))

(defn xml-elem->Organizations
  "Return organizations or []"
  [collection-element]
  (let [archive-ctr (cx/string-at-path collection-element [:ArchiveCenter])
        processing-ctr (cx/string-at-path collection-element [:ProcessingCenter])
        contacts (cx/elements-at-path collection-element [:Contacts :Contact])]
    (seq (concat
           (when processing-ctr
             [(c/map->Organization {:org-type :processing-center :org-name processing-ctr})])
           (when archive-ctr
             [(c/map->Organization {:org-type :archive-center :org-name archive-ctr})])
           (when contacts
             (contacts-xml->Organizations contacts))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-center
  "Return archive or processing center based on org type"
  [center-type orgs]
  (for [org orgs
        :when (= center-type (:org-type org))]
    (let [elem-name (-> center-type
                        name
                        csk/->CamelCase
                        keyword)]
      (x/element elem-name {} (:org-name org)))))

(defn generate-archive-center
  "Return archive center ignoring other type of organization like processing center"
  [orgs]
  (generate-center :archive-center orgs))

(defn generate-processing-center
  "Return processing center ignoring other type of organization like archive center"
  [orgs]
  (generate-center :processing-center orgs))



(comment
  ;;;;;;;;;
  (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
  (parse-collection cmr.umm.test.echo10.collection/all-fields-collection-xml)
  (xml-elem->Campaigns (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  (cx/elements-at-path
    (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
    [:ArchiveCenter])

  (xml-elem->Organizations (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  (let [orgs (vector (c/map->Organization {:org-type :archive-center :org-name "ac se"})
                     (c/map->Organization {:org-type :processing-center :org-name "pro se"}))
        arctr (generate-archive-center orgs)
        prctr (generate-processing-center orgs)]
    (vector arctr prctr))

  (clojure.repl/dir camel-snake-kebab)

  ;;;;;;;;;;;;
  )


