(ns cmr.umm.echo10.collection.campaign
  (:require
   [camel-snake-kebab.core :as csk]
   [clj-time.format :as f]
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]
   [cmr.umm.generator-util :as gu]
   [cmr.umm.umm-collection :as c]))

(defn xml-elem->Campaign
  [campaign-elem]
  (let [short-name (cx/string-at-path campaign-elem [:ShortName])
        long-name (cx/string-at-path campaign-elem [:LongName])]
    (c/map->Project
      {:short-name short-name
       :long-name long-name})))

(defn xml-elem->Campaigns
  [collection-element]
  (seq (map xml-elem->Campaign
            (cx/elements-at-path collection-element [:Campaigns :Campaign]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-campaigns
  [projects]
  (when (seq projects)
    (x/element
      :Campaigns {}
      (for [proj projects]
        (let [{:keys [short-name long-name]} proj]
          (x/element :Campaign {}
                     (x/element :ShortName {} short-name)
                     (when long-name
                       (x/element :LongName {} long-name))))))))


(comment
  ;;;;;;;;;
  (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml)
  (parse-collection cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml)
  (xml-elem->Campaigns (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml))
  (cx/elements-at-path
    (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml)
    [:Campaigns :Campaign])
  (t/xml-elem->Temporal (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml))
  (psa/xml-elem->ProductSpecificAttributes (x/parse-str cmr.umm.test.echo10.echo10-collection-tests/all-fields-collection-xml)))
  ;;;;;;;;;;;;
