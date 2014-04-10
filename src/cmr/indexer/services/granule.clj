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
  ;; Currently every field we need can be extracted from concept
  ;; In the future, we will have to use fields in the umm-concept
  (let [{:keys [concept-id collection-concept-id provider-id granule-ur]} concept]
    {:concept-id concept-id
     :collection-concept-id collection-concept-id
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :granule-ur granule-ur
     :granule-ur.lowercase (s/lower-case granule-ur)}))
