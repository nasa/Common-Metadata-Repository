(ns cmr.umm.echo10.collection.campaign
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [clj-time.format :as f]
            [camel-snake-kebab :as csk]
            [cmr.common.services.errors :as errors]
            [cmr.umm.generator-util :as gu]))

#_(defn xml-elem->Campaign
  [campaign-elem]
  (let [short-name (cx/string-at-path campaign-elem [:ShortName])
        long-name (cx/string-at-path campaign-elem [:LongName])]
    (c/map->Project
      {:short-name short-name
       :long-name long-name})))

#_(defn xml-elem->Campaigns
  [collection-element]
  (let [campaigns (map xml-elem->Campaign
                  (cx/elements-at-path
                    collection-element
                    [:Campaigns :Campaign]))]
    (when (not (empty? campaigns))
      campaigns)))


(defn xml-elem->Campaigns
  "Accumulate campaigns of a collection into umm project list."
  [collection-element]
  (reduce (fn [project-list campaign-elem]
            (let [short-name (cx/string-at-path campaign-elem [:ShortName])
                  long-name (cx/string-at-path campaign-elem [:LongName])
                  project  (c/map->Project {:short-name short-name
                                            :long-name long-name})]
              (conj project-list project)))
          '()
          (reverse (cx/elements-at-path
            collection-element
            [:Campaigns :Campaign]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-campaigns
  [projects]
  (when (and projects (not (empty? projects)))
    (x/element
      :Campaigns {}
      (for [proj projects]
        (let [{:keys [short-name long-name]} proj]
          (x/element :Campaign {}
                     (x/element :ShortName {} short-name)
                     (x/element :LongName {} long-name)))))))


(comment
  ;;;;;;;;;
  (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
  (xml-elem->Campaigns (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  (cx/elements-at-path
            (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml)
            [:Campaigns :Campaign])
  (t/xml-elem->TemporalCoverage (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  (psa/xml-elem->ProductSpecificAttributes (x/parse-str cmr.umm.test.echo10.collection/all-fields-collection-xml))
  ;;;;;;;;;;;;
  )

