(ns cmr.umm.echo10.core
  "Contains main functions for parsing and generating ECHO10 XML")

(defprotocol UmmToEcho10Xml
  "Functions for converting umm items to xml."
  (umm->echo10-xml
    [item] [item indent?]
    "Converts the item to xml with optional indent flag to print indented. Passing true for indent?
    uses a slower output function and should only be used for debugging."))
