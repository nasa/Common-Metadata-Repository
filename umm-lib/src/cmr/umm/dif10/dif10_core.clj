(ns cmr.umm.dif10.dif10-core
  "Contains main functions for parsing and generating DIF 10 XML")

(defprotocol UmmToDif10Xml
  "Functions for converting umm items to xml."
  (umm->dif10-xml
    [item]
    "Converts the item to xml."))
