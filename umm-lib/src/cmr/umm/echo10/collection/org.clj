(ns cmr.umm.echo10.collection.org
  "Archive and Processing Center elements of echo10 are mapped umm organization elements."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.data.xml :as x]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as c]))

(defn xml-elem->Organizations
  "Return organizations or []"
  [collection-element]
  (let [archive-ctr (cx/string-at-path collection-element [:ArchiveCenter])
        processing-ctr (cx/string-at-path collection-element [:ProcessingCenter])]
    (seq (concat
           (when processing-ctr
             [(c/map->Organization {:type :processing-center :org-name processing-ctr})])
           (when archive-ctr
             [(c/map->Organization {:type :archive-center :org-name archive-ctr})])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn- generate-center
  "Return archive or processing center based on org type. ECHO10 only has 1 of each
  archive or processing center so return the first"
  [center-type orgs]
  (when-let [center (first (filter #(= center-type (:type %)) orgs))]
    (let [elem-name (-> center-type
                        name
                        csk/->PascalCase
                        keyword)]
      (x/element elem-name {} (:org-name center)))))

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
  (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml)
  (parse-collection cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml)
  (xml-elem->Campaigns (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml))
  (cx/elements-at-path
    (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml)
    [:ArchiveCenter])

  (xml-elem->Organizations (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml))
  (let [orgs (vector (c/map->Organization {:type :archive-center :org-name "ac se"})
                     (c/map->Organization {:type :processing-center :org-name "pro se"}))
        arctr (generate-archive-center orgs)
        prctr (generate-processing-center orgs)]
    (vector arctr prctr))

  (clojure.repl/dir camel-snake-kebab))

  ;;;;;;;;;;;;
