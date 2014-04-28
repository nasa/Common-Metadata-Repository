(ns cmr.umm.echo10.collection.org
  "Archive and Processing Center elements of echo10 are mapped umm organization elements."
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [clj-time.format :as f]
            [camel-snake-kebab :as csk]
            [cmr.common.services.errors :as errors]
            [cmr.umm.generator-util :as gu]))

(defn xml-elem->Organizations
  "Return organizations or []"
  [collection-element]
  (let [archive-ctr (cx/string-at-path collection-element [:ArchiveCenter])
        processing-ctr (cx/string-at-path collection-element [:ProcessingCenter])]
    (vec (filter #(not (nil? %))
                 (list (when processing-ctr
                         (c/map->Organization {:type "processing-center" :short-name processing-ctr}))
                       (when archive-ctr
                         (c/map->Organization {:type "archive-center" :short-name archive-ctr})))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-center
  "Return archive or processing center based on org type"
  [orgs org-type]
  (when (and orgs (not (empty? orgs)))
    (filter #(not (nil? %))
            (for [org orgs]
              (let [{:keys [type short-name]} org
                    center (when (= org-type type)
                             (x/element (if (= "archive-center" type)
                                          :ArchiveCenter
                                          :ProcessingCenter) {} short-name))]
                center)))))

(defn generate-archive-center
  "Return archive center ignoring other type of organization like processing center"
  [orgs]
  (generate-center orgs "archive-center"))

(defn generate-processing-center
  "Return processing center ignoring other type of organization like archive center"
  [orgs]
  (generate-center orgs "processing-center"))


(comment
  ;;;;;;;;;
  (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
  (parse-collection cmr.umm.test.echo10.collection/all-fields-collection-xml)
  (xml-elem->Campaigns (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  (cx/elements-at-path
    (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
    [:ArchiveCenter])
  (cx/string-at-path
    (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
    [:ArchiveCenterqq])

  (xml-elem->Organizations (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  (let [orgs (vector (c/map->Organization {:type "archive-center" :short-name "ac se"})
                     (c/map->Organization {:type "processing-center" :short-name "pro se"}))
        arctr (generate-archive-center orgs)
        prctr (generate-processing-center orgs)]
    (vector arctr prctr))


  ;;;;;;;;;;;;
  )


