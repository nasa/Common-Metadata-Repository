(ns cmr.umm.iso-mends.collection.platform
  "Contains functions for parsing and generating the ISO MENDS platform"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-mends.collection.instrument :as inst]
            [cmr.umm.iso-mends.collection.keyword :as k]
            [cmr.umm.iso-mends.collection.helper :as h]
            [cmr.umm.generator-util :as gu]))

(defn xml-elem->Platform
  [instruments-mapping platform-elem]
  (let [short-name (cx/string-at-path platform-elem
                                      [:identifier :MD_Identifier :code :CharacterString])
        long-name (cx/string-at-path platform-elem
                                     [:identifier :MD_Identifier :description :CharacterString])
        type (cx/string-at-path platform-elem [:description :CharacterString])
        instrument-ids (map #(get-in % [:attrs :xlink/href]) (cx/elements-at-path platform-elem [:instrument]))
        instruments (seq (map (partial get instruments-mapping) (remove nil? instrument-ids)))]
    (c/map->Platform
      {:short-name short-name
       :long-name long-name
       :type type
       :instruments instruments})))

(defn xml-elem->Platforms
  [collection-element]
  (let [instruments-mapping (inst/xml-elem->Instruments-mapping collection-element)]
    (seq (map (partial xml-elem->Platform instruments-mapping)
              (cx/elements-at-path
                collection-element
                [:acquisitionInformation :MI_AcquisitionInformation :platform :EOS_Platform])))))

(defn- platform-with-id
  "Returns the platform with generated ids for ISO xml generation"
  [platform]
  (let [platform-id (gu/generate-id)
        instruments (inst/instruments-with-id (:instruments platform) platform-id)]
    (-> platform
        (assoc :instruments instruments)
        (assoc :platform-id platform-id))))

(defn platforms-with-id
  "Returns the platforms with generated ids for ISO xml generation"
  [platforms]
  (map platform-with-id platforms))

(defn generate-instruments
  [platforms]
  (inst/generate-instruments (mapcat :instruments platforms)))

(defn generate-platforms
  [platforms]
  (for [platform platforms]
    (let [{:keys [short-name long-name type platform-id instruments]} platform]
      (x/element
        :gmi:platform {}
        (x/element
          :eos:EOS_Platform {:id platform-id}
          (x/element :gmi:identifier {}
                     (x/element :gmd:MD_Identifier {}
                                (h/iso-string-element :gmd:code short-name)
                                (h/iso-string-element :gmd:description long-name)))
          (h/iso-string-element :gmi:description type)
          (if (empty? instruments)
            (x/element :gmi:instrument {:gco:nilReason "inapplicable"})
            (for [instrument instruments]
              (x/element :gmi:instrument {:xlink:href (str "#" (:instrument-id instrument))}))))))))

(defn- platform->keyword
  "Returns the ISO keyword for the given platform"
  [platform]
  (let [{:keys [short-name long-name]} platform]
    (if (empty? long-name)
      short-name
      (str short-name " > " long-name))))

(defn generate-platform-keywords
  [platforms]
  (let [keywords (map platform->keyword platforms)]
    (k/generate-keywords "platform" keywords)))

(defn generate-instrument-keywords
  [platforms]
  (inst/generate-instrument-keywords (mapcat :instruments platforms)))
