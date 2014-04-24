(ns cmr.indexer.services.granule
  "Contains functions to parse and convert granule concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.indexer.services.index-service :as idx]
            [cmr.umm.echo10.granule :as granule]
            [cmr.indexer.services.temporal :as temporal]))

(defmethod idx/parse-concept :granule
  [concept]
  (granule/parse-granule (:metadata concept)))

(defmethod idx/concept->elastic-doc :granule
  [concept umm-concept]
  (let [{:keys [concept-id extra-fields provider-id]} concept
        {:keys [parent-collection-id]} extra-fields
        {:keys [granule-ur temporal-coverage]} umm-concept
        start-date (temporal/start-date :granule temporal-coverage)
        end-date (temporal/end-date :granule temporal-coverage)]
    {:concept-id concept-id
     :collection-concept-id parent-collection-id
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :granule-ur granule-ur
     :granule-ur.lowercase (s/lower-case granule-ur)
     :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
     :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))}))
