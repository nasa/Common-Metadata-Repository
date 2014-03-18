(ns cmr.ingest.services.ingest
  (:require [cmr.ingest.data :as data]
            [cmr.common.log :refer (debug info warn error)]))

(defn- get-concept-id
  "Fetch concept-id from metadata db given concept attribs."
  [system concept]
  (let [{:keys [db]} system
        {:keys [concept-type provider-id native-id]} concept]
    (data/get-concept-id db concept-type provider-id native-id)))

(defn- index-concept
  "Forward newly created concept for indexer app consumption."
  [system concept]
  (let [{:keys [idx-db]} system
        {:keys [concept-id revision-id]} concept]
    (data/index-concept idx-db concept-id revision-id)))

(defn save-concept
  "Store a concept and return the revision"
  [system concept]
  (let [concept-id (get-concept-id system concept)
        revision-id (data/save-concept (:db system) (assoc concept :concept-id  concept-id))]
    (index-concept system (assoc concept :concept-id  concept-id :revision-id revision-id))
    {:concept-id concept-id :revision-id revision-id}))