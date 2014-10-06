(ns cmr.indexer.data.concepts.atom-helper
  "Contains functions to provide atom field values")

(def mime-type->atom-origianl-format
  "Defines the concept mime-type to atom original-format mapping"
  {"application/echo10+xml" "ECHO10"
   "application/iso:smap+xml" "SMAP_ISO"
   "application/iso19115+xml" "ISO19115"
   "application/dif+xml" "DIF"})