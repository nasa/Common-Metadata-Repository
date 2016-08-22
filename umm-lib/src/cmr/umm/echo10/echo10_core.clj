(ns cmr.umm.echo10.echo10-core
  "Contains main functions for parsing and generating ECHO10 XML")

(defprotocol UmmToEcho10Xml
  "Functions for converting umm items to xml."
  (umm->echo10-xml
    [item]
    "Converts the item to xml."))
