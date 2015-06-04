(ns cmr.umm.iso-mends.core
  "Contains main functions for parsing and generating ISO XML"
  (require [cmr.common.xml :as cx]))

(defprotocol UmmToIsoMendsXml
  "Functions for converting umm items to ISO MENDS xml."
  (umm->iso-mends-xml
    [item]
    "Converts the item to xml with optional indent flag to print indented."))

(defn id-elem
  "Returns MD_DataIdentification element from given ISO XML document."
  [iso-xml]
  (cx/element-at-path iso-xml [:identificationInfo :MD_DataIdentification]))
