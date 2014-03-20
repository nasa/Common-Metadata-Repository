(ns cmr.ingest.services.ingest
  (:require [cmr.ingest.data :as data]
            [cmr.common.log :refer (debug info warn error)]))

(defn save-concept
  "Store a concept in mdb and indexer and return concept-id and revision-id."
  [system concept]
  (let [{:keys [db idx-db]} system
        {:keys [concept-type provider-id native-id]} concept
        {:keys [concept-id]} (data/get-concept-id db concept-type provider-id native-id)
        {:keys [revision-id]} (data/save-concept db (assoc concept :concept-id  concept-id))]
    (data/index-concept idx-db concept-id revision-id)
    {:concept-id concept-id, :revision-id revision-id}))

(defn delete-concept
  "Delete a concept from mdb and indexer."
  [system concept-attribs]
  (let [{:keys [db idx-db]} system
        {:keys [concept-type provider-id native-id]}  concept-attribs
        {:keys [concept-id]} (data/get-concept-id db concept-type provider-id native-id)
        {:keys [revision-id]} (data/delete-concept db concept-id)]
    (data/delete-concept-from-index idx-db concept-id revision-id)
    {:concept-id concept-id, :revision-id revision-id}))