(ns cmr.ingest.services.ingest
  (:require [cmr.ingest.data.mdb :as mdb]
            [cmr.ingest.data.indexer :as indexer]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as serv-errors]
            [cmr.system-trace.core :refer [deftracefn]]))

(def cmr-valid-content-types
  #{"echo10+xml", "iso_prototype+xml", "iso:smap+xml",
    "iso19115+xml", "dif+xml"})

(deftracefn save-concept
  "Store a concept in mdb and indexer and return concept-id and revision-id."
  [context concept]
  (let [metadata (:metadata concept)
        content-type (:format concept)
        smallest-xml-file-length (.length "<a/>\n")
        xml-content? (> (.length metadata) smallest-xml-file-length)
        valid-content-type? (contains? cmr-valid-content-types (clojure.string/trim content-type))]
    (cond (not xml-content?) (serv-errors/throw-service-error :bad-request "Invalid XML file.")
          (not valid-content-type?) (serv-errors/throw-service-error :bad-request 
                                                                     "Invalid content-type: %s. Valid content-types %s."
                                                                     content-type cmr-valid-content-types)
          :else   (let [{:keys [concept-type provider-id native-id]} concept
                        concept-id (mdb/get-concept-id context concept-type provider-id native-id)
                        revision-id (mdb/save-concept context (assoc concept :concept-id  concept-id))]
                    (indexer/index-concept context concept-id revision-id)
                    {:concept-id concept-id, :revision-id revision-id}))))

(deftracefn delete-concept
  "Delete a concept from mdb and indexer."
  [context concept-attribs]
  (let [{:keys [concept-type provider-id native-id]}  concept-attribs
        concept-id (mdb/get-concept-id context concept-type provider-id native-id)
        revision-id (mdb/delete-concept context concept-id)]
    (indexer/delete-concept-from-index context concept-id revision-id)
    {:concept-id concept-id, :revision-id revision-id}))


