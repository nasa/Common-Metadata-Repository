(ns cmr.umm.mime-types
  "Provides a mapping of all supported mime-types.")

;; body element (metadata) of a request arriving at ingest app should be in xml format and mime type
;; should be of the items in this def.
(def CMR_VALID_CONTENT_TYPES
  #{"application/echo10+xml", "application/iso_prototype+xml", "application/iso:smap+xml",
    "application/iso19115+xml", "application/dif+xml"})

