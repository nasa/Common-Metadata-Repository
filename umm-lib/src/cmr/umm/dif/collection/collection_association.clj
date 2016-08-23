(ns cmr.umm.dif.collection.collection-association
  "Provide functions to parse and generate DIF collection associations."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]))

(defn xml-elem->CollectionAssociations
  [collection-element]
  (let [cas (cx/strings-at-path collection-element [:Parent_DIF])]
    (seq (map #(c/map->CollectionAssociation {:short-name %
                                              :version-id c/not-provided}) cas))))

(defn generate-collection-associations
  [cas]
  (for [ca cas]
    (let [{:keys [short-name]} ca]
      (x/element :Parent_DIF {} short-name))))
