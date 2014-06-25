(ns cmr.umm.dif.core
  "Contains main functions for parsing and generating DIF XML")

(defprotocol UmmToDifXml
  "Functions for converting umm items to xml."
  (umm->dif-xml
    [item] [item indent?]
    "Converts the item to xml with optional indent flag to print indented. Passing true for indent?
    uses a slower output function and should only be used for debugging."))
