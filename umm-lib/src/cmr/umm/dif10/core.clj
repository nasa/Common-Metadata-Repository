(ns cmr.umm.dif10.core
  "Contains main functions for parsing and generating DIF 10 XML")

(defprotocol UmmToDifXml
  "Functions for converting umm items to xml."
  (umm->dif10-xml
    [item] [item indent?]
    "Converts the item to xml with optional indent flag to print indented. Passing true for indent?
    uses a slower output function and should only be used for debugging."))