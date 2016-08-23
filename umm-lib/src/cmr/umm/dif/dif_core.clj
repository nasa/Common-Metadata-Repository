(ns cmr.umm.dif.dif-core
  "Contains main functions for parsing and generating DIF XML")

(defprotocol UmmToDifXml
  "Functions for converting umm items to xml."
  (umm->dif-xml
    [item]
    "Converts the item to xml."))
