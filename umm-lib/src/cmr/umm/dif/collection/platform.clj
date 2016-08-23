(ns cmr.umm.dif.collection.platform
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.dif.dif-core :as dif]))

(defn xml-elem->Instrument
  [elem]
  (let [short-name (cx/string-at-path elem [:Short_Name])
        long-name (cx/string-at-path elem [:Long_Name])]
    (c/map->Instrument
      {:short-name short-name
       :long-name long-name})))

(defn xml-elem->Platform
  [elem]
  (let [short-name (cx/string-at-path elem [:Short_Name])
        long-name (cx/string-at-path elem [:Long_Name])]
    (c/map->Platform
      {:short-name short-name
       :long-name long-name
       ;; DIF does not have platform type in its xml, but it is a required field in ECHO10.
       ;; We make a dummy type here to facilitate cross format conversion
       :type c/not-provided})))

(defn xml-elem->Platforms
  [collection-element]
  (let [platforms (seq (map xml-elem->Platform
                            (cx/elements-at-path collection-element [:Source_Name])))
        instruments (seq (map xml-elem->Instrument
                              (cx/elements-at-path collection-element [:Sensor_Name])))]
    ;; When there is only one platform in the collection, associate the instruments on that platform.
    ;; Otherwise, create a dummy platform to hold all instruments and add that to the platforms.
    (if (= 1 (count platforms))
      (map #(assoc % :instruments instruments) platforms)
      (if instruments
        (conj platforms (c/map->Platform {:short-name c/not-provided
                                          :long-name c/not-provided
                                          :type c/not-provided
                                          :instruments instruments}))
        platforms))))

(defn- generate-element
  "Generate a xml element with just Short_Name and Long_Name"
  [elem-key values]
  (for [value values]
    (let [{:keys [short-name long-name]} value]
      (x/element elem-key {}
                 (x/element :Short_Name {} short-name)
                 (x/element :Long_Name {} long-name)))))

(defn generate-instruments
  [instruments]
  (generate-element :Sensor_Name instruments))

(defn generate-platforms
  [platforms]
  (generate-element :Source_Name platforms))
