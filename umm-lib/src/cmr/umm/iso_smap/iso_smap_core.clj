(ns cmr.umm.iso-smap.iso-smap-core
  "Contains main functions for parsing and generating SMAP ISO XML")

(defprotocol UmmToIsoSmapXml
  "Functions for converting umm items to ISO SMAP xml."
  (umm->iso-smap-xml
    [item]
    "Converts the item to xml."))
