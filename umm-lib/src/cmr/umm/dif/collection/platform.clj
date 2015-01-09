(ns cmr.umm.dif.collection.platform
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->Platform
  [platform-elem]
  (let [short-name (cx/string-at-path platform-elem [:Short_Name])
        long-name (cx/string-at-path platform-elem [:Long_Name])]
    (c/map->Platform
      {:short-name short-name
       :long-name long-name
       ;; DIF does not have platform type in its xml, but it is a required field in ECHO10.
       ;; We make a dummy type here to facilitate cross format conversion
       :type "dummy"})))

(defn xml-elem->Platforms
  [collection-element]
  (seq (map xml-elem->Platform
            (cx/elements-at-path collection-element [:Source_Name]))))

(defn generate-platforms
  [platforms]
  (for [platform platforms]
    (let [{:keys [short-name long-name]} platform]
      (x/element :Source_Name {}
                 (x/element :Short_Name {} short-name)
                 (x/element :Long_Name {} long-name)))))
