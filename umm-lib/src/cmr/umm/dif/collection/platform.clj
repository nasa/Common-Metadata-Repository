(ns cmr.umm.dif.collection.platform
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]))

(defn xml-elem->Instrument
  [elem]
  (let [short-name (cx/string-at-path elem [:Short_Name])
        long-name (cx/string-at-path elem [:Long_Name])]
    (coll/map->Instrument
     {:short-name short-name
      :long-name long-name})))

(defn xml-elem->Platform
  [elem]
  (let [short-name (cx/string-at-path elem [:Short_Name])
        long-name (cx/string-at-path elem [:Long_Name])]
    (coll/map->Platform
     {:short-name short-name
      :long-name long-name
      ;; DIF does not have platform type in its xml, but it is a required field in ECHO10.
      ;; We make a dummy type here to facilitate cross format conversion
      :type coll/not-provided})))

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
        (conj platforms (coll/map->Platform {:short-name coll/not-provided
                                             :long-name coll/not-provided
                                             :type coll/not-provided
                                             :instruments instruments}))
        platforms))))

(defn- generate-element
  "Generate a xml element with just Short_Name and Long_Name"
  [elem-key values]
  (for [value values]
    (let [{:keys [short-name long-name]} value]
      (xml/element elem-key {}
                 (xml/element :Short_Name {} short-name)
                 (xml/element :Long_Name {} long-name)))))

(defn generate-instruments
  [instruments]
  (generate-element :Sensor_Name instruments))

(defn generate-platforms
  [platforms]
  (generate-element :Source_Name platforms))
