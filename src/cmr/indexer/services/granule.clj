(ns cmr.indexer.services.granule
  "Contains functions to parse and convert granule concept"
  (:require [clojure.string :as s]
            [cmr.indexer.services.index-service :as idx]
            [cmr.umm.echo10.granule :as granule]))

(defmethod idx/parse-concept :granule
  [concept]
  (granule/parse-granule (:metadata concept)))

(defmethod idx/concept->elastic-doc :granule
  [concept umm-concept]
  (let [{:keys [concept-id extra-fields provider-id]} concept
        {:keys [parent-collection-id]} extra-fields
        {:keys [granule-ur]} umm-concept]
    {:concept-id concept-id
     :collection-concept-id parent-collection-id
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :granule-ur granule-ur
     :granule-ur.lowercase (s/lower-case granule-ur)}))
