(ns cmr.umm.dif.core
  "Contains main functions for parsing and generating DIF XML")

(defprotocol UmmToDifXml
  "Functions for converting umm items to xml."
  (umm->dif-xml
    [item]
    "Converts the item to xml."))

(def value-not-provided
  "Value to use when DIF9 does not have a value for a field which is required in another format"
  "Not provided")
