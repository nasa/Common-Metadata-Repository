(ns cmr.opendap.data.size.granule
  (:require
   [clojure.data.xml :as xml]
   [cmr.opendap.data.size.util :as util]
   [xml-in.core :as xml-in]))

(defn extract-size-data
  [parsed-xml]
  (-> parsed-xml
      (xml-in/find-first [:Granule :DataGranule :SizeMBDataGranule])
      first
      read-string
      util/mb->bytes))

(defn file-size
  "Returns granule file size in bytes."
  [xml-metadata]
  (-> xml-metadata
      xml/parse-str
      extract-size-data))
