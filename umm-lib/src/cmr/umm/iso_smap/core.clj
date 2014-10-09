(ns cmr.umm.iso-smap.core
  "Contains main functions for parsing and generating SMAP ISO XML")

(defprotocol UmmToIsoSmapXml
  "Functions for converting umm items to ISO SMAP xml."
  (umm->iso-smap-xml
    [item] [item indent?]
    "Converts the item to xml with optional indent flag to print indented. Passing true for indent?
    uses a slower output function and should only be used for debugging."))
