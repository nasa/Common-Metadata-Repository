(ns cmr.umm.echo10.granule.project-ref
  "Contains functions for parsing and generating the ECHO10 dialect for project-refs (campaigns)"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.granule :as g]))

(defn xml-elem->project-refs
  "Parses an ECHO10 Campaigns element of a Granule XML document and returns the short names."
  [granule-elem]
  (let [prefs (cx/strings-at-path
                granule-elem
                [:Campaigns :Campaign :ShortName])]
    (when (not (empty? prefs))
      prefs)))

(defn generate-project-refs
  "Generates the Campaigns element of an ECHO10 XML from a UMM Granule project-refs entry."
  [prefs]
  (when (not (empty? prefs))
    (x/element :Campaigns {}
               (for [pref prefs]
                 (x/element :Campaign {}
                            (x/element :ShortName {} pref))))))
