(ns cmr.ingest.services.ingest
  (:require [cmr.ingest.data.mdb :as mdb]
            [cmr.ingest.data.indexer :as indexer]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as serv-errors]
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.echo10.granule :as g]
            [clojure.string :as string]
            [cmr.system-trace.core :refer [deftracefn]]))

;; body element (metadata) of a request arriving at ingest app should be in xml format and mime type
;; should be of the items in this def.
(def cmr-valid-content-types
  #{"application/echo10+xml", "application/iso_prototype+xml", "application/iso:smap+xml",
    "application/iso19115+xml", "application/dif+xml"})

;; metadata should be atleast this size to proceed with next steps of ingest workflow
(def smallest-xml-file-length (count "<a/>\n"))

(defmulti add-extra-fields
  "Parse the metadata of concept, add the extra fields to it and return the concept."
  (fn [context concept]
    (:concept-type concept)))

(defmethod add-extra-fields :collection
  [context concept]
  (let [collection (c/parse-collection (:metadata concept))
        {{:keys [short-name version-id]} :product
         entry-title :entry-title} collection]
    (assoc concept :extra-fields {:entry-title entry-title
                                  :short-name short-name
                                  :version-id version-id})))

(defmethod add-extra-fields :granule
  [context concept]
  (let [granule (g/parse-granule (:metadata concept))
        {:keys [collection-ref granule-ur]} granule
        params (merge {:provider-id (:provider-id concept)} collection-ref)
        params (into {} (remove (comp empty? second) params))
        parent-collection-id (mdb/get-collection-concept-id context params)]
    (assoc-in concept [:extra-fields :parent-collection-id] parent-collection-id)))

(deftracefn save-concept
  "Store a concept in mdb and indexer and return concept-id and revision-id."
  [context concept]
  (let [metadata (:metadata concept)
        content-type (:format concept)
        xml-content? (> (count metadata) smallest-xml-file-length)
        valid-content-type? (contains? cmr-valid-content-types (string/trim content-type))]
    (cond (not xml-content?) (serv-errors/throw-service-error :bad-request "Invalid XML file.")
          (not valid-content-type?) (serv-errors/throw-service-error :bad-request
                                                                     (format "Invalid content-type: %s. Valid content-types %s."
                                                                             content-type cmr-valid-content-types))
          :else (let [concept (add-extra-fields context concept)
                      {:keys [concept-id revision-id]} (mdb/save-concept context concept)]
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
