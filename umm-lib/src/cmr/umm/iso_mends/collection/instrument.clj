(ns cmr.umm.iso-mends.collection.instrument
  "Contains functions for parsing and generating the ISO MENDS instrument"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-mends.collection.sensor :as sensor]
            [cmr.umm.iso-mends.collection.keyword :as k]
            [cmr.umm.iso-mends.collection.helper :as h]
            [cmr.umm.generator-util :as gu]))

(defn- xml-elem->Instrument
  [instrument-elem]
  (let [short-name (cx/string-at-path instrument-elem
                                      [:identifier :MD_Identifier :code :CharacterString])
        long-name (cx/string-at-path instrument-elem
                                     [:identifier :MD_Identifier :description :CharacterString])
        sensors (sensor/xml-elem->Sensors instrument-elem)]
    (c/map->Instrument {:short-name short-name
                        :long-name long-name
                        :sensors sensors})))

(defn- xml-elem->Instrument-mapping
  [instrument-elem]
  ;; the instrument element could be under tag of either :EOS_Instrument or :MI_Instrument
  ;; here we just parse the :content to avoid using two different xpaths.
  (let [instrument-elem (first (:content instrument-elem))
        id (get-in instrument-elem [:attrs :id])
        instrument (xml-elem->Instrument instrument-elem)]
    [(str "#" id) instrument]))

(defn xml-elem->Instruments-mapping
  "Returns the instrument id to Instrument mapping by parsing the given xml element"
  [collection-element]
  (into {} (map xml-elem->Instrument-mapping
                (cx/elements-at-path
                  collection-element
                  [:acquisitionInformation :MI_AcquisitionInformation :instrument]))))

(defn- instrument-with-id
  "Returns the instrument with generated ids for ISO xml generation"
  [platform-id instrument]
  (let [instrument-id (gu/generate-id)
        sensors (sensor/sensors-with-id (:sensors instrument) instrument-id)]
    (-> instrument
        (assoc :sensors sensors)
        (assoc :instrument-id instrument-id)
        (assoc :platform-id platform-id))))

(defn instruments-with-id
  "Returns the instruments with generated ids for ISO xml generation"
  [instruments platform-id]
  (map (partial instrument-with-id platform-id) instruments))

(defn- instrument->keyword
  "Returns the ISO keyword for the given instrument"
  [instrument]
  (let [{:keys [short-name long-name]} instrument]
    (if (empty? long-name)
      short-name
      (str short-name " > " long-name))))

(defn- get-instrument-tag
  "Returns the instrument tag :eos:EOS_Instrument if there are sensors in the instrument,
  otherwise returns :gmi:MI_Instrument"
  [instrument]
  (if (empty? (:sensors instrument))
    :gmi:MI_Instrument
    :eos:EOS_Instrument))

(defn generate-instruments
  [instruments]
  (for [instrument instruments]
    (let [{:keys [long-name short-name sensors instrument-id platform-id]} instrument
          title (instrument->keyword instrument)]
      (x/element :gmi:instrument {}
                 (x/element (get-instrument-tag instrument) {:id instrument-id}
                            (x/element :gmi:citation {}
                                       (x/element :gmd:CI_Citation {}
                                                  (h/iso-string-element :gmd:title title)
                                                  (x/element :gmd:date {:gco:nilReason "unknown"})))
                            (x/element :gmi:identifier {}
                                       (x/element :gmd:MD_Identifier {}
                                                  (h/iso-string-element :gmd:code short-name)
                                                  (h/iso-string-element :gmd:description long-name)))
                            (h/iso-string-element :gmi:type "")
                            (x/element :gmi:description {:gco:nilReason "missing"})
                            (x/element :gmi:mountedOn {:xlink:href (str "#" platform-id)})
                            (sensor/generate-sensors sensors))))))

(defn generate-instrument-keywords
  [instruments]
  (let [keywords (map instrument->keyword instruments)]
    (k/generate-keywords "instrument" keywords)))



