(ns cmr.indexer.data.concepts.deleted-granule
  "Contains functions to parse and convert deleted-granule index document"
  (:require
   [cmr.indexer.data.elasticsearch :as es]))

(def deleted-granule-index-name
  "The name of the index in elastic search."
  "1_deleted_granules")

(def deleted-granule-type-name
  "The name of the mapping type within the cubby elasticsearch index."
  "deleted-granule")

(defn remove-deleted-granule
  "Remove deleted granule from deleted granule index for given concept id"
  [context concept-id revision-id options]
  (let [elastic-options (select-keys options [:ignore-conflict?])]
    (es/delete-document
      context [deleted-granule-index-name] deleted-granule-type-name
      concept-id revision-id nil elastic-options)))

(defn deleted-granule->elastic-doc
  "Returns elastic json that can be used to insert the given granule concept in elasticsearch."
  [concept]
  (let [{:keys [concept-id provider-id extra-fields revision-date]} concept
        {:keys [granule-ur parent-collection-id]} extra-fields]
    {:concept-id concept-id
     :revision-date revision-date
     :provider-id provider-id
     :granule-ur granule-ur
     :parent-collection-id parent-collection-id}))

(defn index-deleted-granule
  "Index a deleted granule with given concept"
  [context concept concept-id revision-id elastic-version elastic-options]
  (let [es-doc (deleted-granule->elastic-doc concept)]
    (es/save-document-in-elastic
      context [deleted-granule-index-name] deleted-granule-type-name
      es-doc concept-id revision-id elastic-version elastic-options)))
