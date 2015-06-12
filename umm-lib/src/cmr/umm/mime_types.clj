(ns cmr.umm.mime-types
  "Provides a mapping of all supported mime-types."
  (:require [cmr.common.mime-types :as mt]))

;; body element (metadata) of a request arriving at ingest app should be in xml format and mime type
;; should be of the items in this def.

;; Need to determine which ISO types will be supported when implementing ISO user stories.
(def concept-type->valid-mime-types
  {:collection #{mt/echo10 mt/iso-smap mt/iso mt/dif mt/dif10}
   :granule #{mt/echo10 mt/iso-smap}})
