(ns cmr.umm.echo10.collection.campaign
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]))

(defn xml-elem->Campaign
  [campaign-elem]
  (let [short-name (cx/string-at-path campaign-elem [:ShortName])
        long-name (cx/string-at-path campaign-elem [:LongName])]
    (coll/map->Project
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
    (xml/element
      :Campaigns {}
      (for [proj projects]
        (let [{:keys [short-name long-name]} proj]
          (xml/element :Campaign {}
                     (xml/element :ShortName {} short-name)
                     (when long-name
                       (xml/element :LongName {} long-name))))))))
