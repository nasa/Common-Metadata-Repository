(ns cmr.umm.dif.collection.extended-metadata
  "Provide functions to parse and generate DIF Extended_Meatadata elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]))

(defn xml-elem->extended-metadata
  [extended-elem]
  (let [name (cx/string-at-path extended-elem [:Name])
        value (cx/string-at-path extended-elem [:Value])]
    {:name name
     :value value}))

(defn xml-elem->extended-metadatas
  [collection-element]
  (let [extended-metadatas (map xml-elem->extended-metadata
                                (cx/elements-at-path
                                  collection-element
                                  [:Extended_Metadata :Metadata]))]
    (when-not (empty? extended-metadatas)
      extended-metadatas)))

(defn generate-extended-metadatas
  [extended-metadatas]
  (when (and extended-metadatas (not (empty? extended-metadatas)))
    (x/element :Extended_Metadata {}
               (for [em extended-metadatas]
                 (let [{:keys [name value]} em]
                   (x/element :Metadata {}
                              (x/element :Name {} name)
                              (x/element :Value {} value)))))))
