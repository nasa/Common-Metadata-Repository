(ns cmr.system-int-test.data2.data_util
  "Contains helper functions for the data2 functions"
  (:require
   [cmr.common.mime-types :as mime-types]
   [cmr.umm-spec.legacy :as umm-legacy]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]))

(def context (lkt/setup-context-for-test lkt/sample-keyword-map))

(defn mimic-ingest-retrieve-metadata-conversion
  "To mimic ingest, convert a collection to metadata in its native format then back to UMM. If
  native format is umm-json, do not do conversion since that will convert to echo10 in the
  parse-concept."
  ([collection]
   (mimic-ingest-retrieve-metadata-conversion collection (:format-key collection)))
  ([collection format-key]
   (if (= format-key :umm-json)
     collection
     (let [original-metadata (umm-legacy/generate-metadata context collection format-key)]
       (umm-legacy/parse-concept context {:metadata original-metadata
                                          :concept-type (umm-legacy/item->concept-type collection)
                                          :format (mime-types/format->mime-type format-key)})))))
