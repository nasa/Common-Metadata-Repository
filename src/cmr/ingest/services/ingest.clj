(ns cmr.ingest.services.ingest
  (:require [cmr.ingest.data :as data]
            [cmr.common.log :refer (debug info warn error)]))

(defn get-concept-id
  "Fetch concept-id from metadata db given concept attribs."
  [system concept]
  (let [{:keys [db]} system
        {:keys [concept-type provider-id native-id]} concept]
    (data/get-concept-id db concept-type provider-id native-id)))
    
(defn save-concept
  "Store a concept record and return the revision"
  [system concept]
  (let [{:keys [db]} system]
    (data/save-concept db concept)))

(defn stage-concept-for-indexing
  "Stage attributes of a concept for indexer app consumption."
  [system concept]
  (let [{:keys [idx-db]} system
        {:keys [concept-id revision-id]} concept]
    (data/stage-concept-for-indexing idx-db concept-id revision-id)))