(ns cmr.metadata-db.services.concept-services
  "Sevices to support the business logic of the metadata db."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.log :refer (debug info warn error)]))

(defn get-concept
  "Get a concept by concept-id."
  [system concept-id revision-id]
  (let [{:keys [db]} system]
    (data/get-concept db concept-id revision-id)))

(defn get-concepts
  "Get multiple concepts by concept-id and revision-id."
  [system concept-id-revision-id-tuples]
  (let [{:keys [db]} system]
    (data/get-concepts db concept-id-revision-id-tuples)))

(defn save-concept
  "Store a concept record and return the revision."
  [system concept]
  (let [{:keys [db]} system]
    (data/save-concept db concept)))

(defn force-delete
  "Delete all concepts from the concept store."
  [system]
  (let [{:keys [db]} system]
    (data/force-delete db)))

(defn get-concept-id
  "Get a concept id for a given concept."
  [system concept]
  (let [{:keys [db]} system
        {:keys [concept-type provider-id native-id]} concept]
    (data/get-concept-id db concept-type provider-id native-id)))