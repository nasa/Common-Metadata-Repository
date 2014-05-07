(ns cmr.umm.echo10.collection.platform
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->Platform
  [platform-elem]
  (let [short-name (cx/string-at-path platform-elem [:ShortName])
        long-name (cx/string-at-path platform-elem [:LongName])
        type (cx/string-at-path platform-elem [:Type])]
    (c/map->Platform
      {:short-name short-name
       :long-name long-name
       :type type})))

(defn xml-elem->Platforms
  [collection-element]
  (let [platforms (map xml-elem->Platform
                       (cx/elements-at-path
                         collection-element
                         [:Platforms :Platform]))]
    (when-not (empty? platforms)
      platforms)))

(defn generate-platforms
  [platforms]
  (when-not (empty? platforms)
    (x/element
      :Platforms {}
      (for [platform platforms]
        (let [{:keys [short-name long-name type]} platform]
          (x/element :Platform {}
                     (x/element :ShortName {} short-name)
                     (x/element :LongName {} long-name)
                     (x/element :Type {} type)))))))
