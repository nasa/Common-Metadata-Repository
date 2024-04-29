(ns cmr.umm.echo10.collection.campaign
  (:require
   [clojure.data.xml :as x]
   [cmr.common.xml :as cx]
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
