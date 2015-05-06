(ns cmr.umm.dif10.collection.platform
  (:require [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.dif10.collection.instrument :as inst]
            [cmr.umm.dif10.collection.characteristic :as char]))

(defn xml-elem->Platform
  [platform-elem]
  (let [short-name (cx/string-at-path platform-elem [:Short_Name])
        long-name (cx/string-at-path platform-elem [:Long_Name])
        type (cx/string-at-path platform-elem [:Type])
        characteristics (char/xml-elem->Characteristics platform-elem)
        instruments (inst/xml-elem->Instruments platform-elem)]
    (c/map->Platform
      {:short-name short-name
       :long-name long-name
       :type type
       :instrument instruments
       :characteristics characteristics})))

(defn xml-elem->Platforms
  [collection-element]
  (seq (map xml-elem->Platform
            (cx/elements-at-path
              collection-element
              [:Platform]))))
