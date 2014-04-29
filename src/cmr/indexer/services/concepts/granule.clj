(ns cmr.indexer.services.concepts.granule
  "Contains functions to parse and convert granule concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.indexer.services.index-service :as idx]
            [cmr.umm.echo10.granule :as granule]
            [cmr.indexer.data.metadata-db :as mdb]
            [cmr.indexer.services.temporal :as temporal]
            [cmr.indexer.services.concepts.granule.attribute :as attrib]))

(defmethod idx/parse-concept :granule
  [concept]
  (granule/parse-granule (:metadata concept)))

(defn- get-parent-collection
  [context parent-collection-id]
  (let [concept (mdb/get-latest-concept context parent-collection-id)]
    ;; Concept id associated with parsed data to use in error messages.
    (assoc (idx/parse-concept concept) :concept-id parent-collection-id)))

(defmethod idx/concept->elastic-doc :granule
  [context concept umm-granule]
  (let [{:keys [concept-id extra-fields provider-id]} concept
        {:keys [parent-collection-id]} extra-fields
        parent-collection (get-parent-collection context parent-collection-id)
        {:keys [granule-ur temporal project-refs]} umm-granule
        start-date (temporal/start-date :granule temporal)
        end-date (temporal/end-date :granule temporal)]
    {:concept-id concept-id
     :collection-concept-id parent-collection-id
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :granule-ur granule-ur
     :granule-ur.lowercase (s/lower-case granule-ur)
     :project-refs project-refs
     :project-refs.lowercase (map s/lower-case project-refs)
     :attributes (attrib/psa-refs->elastic-docs parent-collection umm-granule)
     :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
     :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))}))
