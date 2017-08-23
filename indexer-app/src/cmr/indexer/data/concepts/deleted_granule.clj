(ns cmr.indexer.data.concepts.deleted-granule
  "Contains functions to parse and convert deleted-granule index document"
  (:require
   [clj-time.core :as t]))

(defn deleted-granule->elastic-doc
  "Returns elastic json that can be used to insert the given granule concept in elasticsearch."
  [concept]
  (let [{:keys [concept-id provider-id extra-fields]} concept
        {:keys [granule-ur parent-collection-id]} extra-fields]
    {:concept-id concept-id
     :delete-time (t/now)
     :provider-id provider-id
     :granule-ur granule-ur
     :parent-collection-id parent-collection-id}))
