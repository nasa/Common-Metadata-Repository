(ns cmr.umm.echo10.collection.platform
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.echo10.collection.instrument :as inst]))

(defn xml-elem->Platform
  [platform-elem]
  (let [short-name (cx/string-at-path platform-elem [:ShortName])
        long-name (cx/string-at-path platform-elem [:LongName])
        type (cx/string-at-path platform-elem [:Type])
        instruments (inst/xml-elem->Instruments platform-elem)]
    (c/map->Platform
      {:short-name short-name
       :long-name long-name
       :type type
       :instruments instruments})))

(defn xml-elem->Platforms
  [collection-element]
  (seq (map xml-elem->Platform
            (cx/elements-at-path
              collection-element
              [:Platforms :Platform]))))

(defn generate-platforms
  [platforms]
  (when-not (empty? platforms)
    (x/element
      :Platforms {}
      (for [platform platforms]
        (let [{:keys [short-name long-name type instruments]} platform]
          (x/element :Platform {}
                     (x/element :ShortName {} short-name)
                     (x/element :LongName {} long-name)
                     (x/element :Type {} type)
                     (inst/generate-instruments instruments)))))))
