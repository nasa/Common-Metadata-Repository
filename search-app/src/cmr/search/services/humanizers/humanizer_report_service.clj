(ns cmr.search.services.humanizers.humanizer-report-service
  "Provides functions for reporting on humanizers"
  (require
   [clojure.data.csv :as csv]
   [cmr.common-app.humanizer :as h]
   [cmr.common.concepts :as concepts]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.util :as u]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.data.metadata-retrieval.revision-format-map :as rfm]
   [cmr.search.services.humanizers.humanizer-service :as hs]
   [cmr.umm-spec.legacy :as umm-legacy]
   [cmr.umm-spec.umm-spec-core :as umm-spec-core])
  (:import java.io.StringWriter))

(def CSV_HEADER
  ["provider", "concept_id", "short_name" "version", "original_value", "humanized_value"])

(defconfig humanizer-report-collection-batch-size
  "The size of the batches to use to process collections for the humanizer report"
  {:default 500 :type Long})

(defn- rfm->umm-collection
  "Takes a revision format map and parses it into a UMM-spec record."
  [revision-format-map]
  (let [concept-id (:concept-id revision-format-map)
        umm (umm-spec-core/parse-metadata
              (rfm/revision-format-map->concept :native revision-format-map))]
    (assoc umm
           :concept-id concept-id
           :provider-id (:provider-id (concepts/parse-concept-id concept-id)))))

(defn- get-all-collections
  "Retrieves all collections from the Metadata cache, partitions them into batches of size
  humanizer-report-collection-batch-size, so the batches can be processed lazily to avoid out of memory errors."
  [context]
  ;; Currently not throwing an exception if the cache is empty. May want to change in the future
  ;; to throw an exception.
  (let [rfms (metadata-cache/all-cached-revision-format-maps context)]
    (map
     #(map
       rfm->umm-collection
       (partition-all (humanizer-report-collection-batch-size) %))
     rfms)))

(defn humanized-collection->reported-rows
  "Takes a humanized collection and returns rows to populate the CSV report."
  [collection]
  (let [{:keys [provider-id concept-id Product]} collection
        {:keys [ShortName VersionId]} Product]
    (for [paths (vals h/humanizer-field->umm-paths)
          path paths
          parents (u/get-in-all collection (drop-last path))
          :let [parents (u/seqify parents)]
          parent parents
          :let [field (last path)
                humanized-value (get parent (h/humanizer-key field))]
          :when (and (some? humanized-value) (:reportable humanized-value))
          :let [humanized-string-value (:value humanized-value)
                original-value (get parent field)]]
      [provider-id concept-id ShortName VersionId original-value humanized-string-value])))

(defn humanizers-report-csv
  "Returns a report on humanizers in use in collections as a CSV."
  [context]
  (let [[t1 collection-batches] (u/time-execution
                                  (get-all-collections context))
        string-writer (StringWriter.)
        idx-atom (atom 0)]
    (debug "get-all-collections:" t1
           "processing " (count collection-batches)
           " batches of size" (humanizer-report-collection-batch-size))
    (csv/write-csv string-writer [CSV_HEADER])
    (let [humanizers (hs/get-humanizers context)
          [t4 csv-string]
          (u/time-execution
            (doseq [batch collection-batches]
              (let [[t2 humanized-rows]
                    (u/time-execution
                      (doall
                        (pmap (fn [coll]
                                (-> (h/umm-collection->umm-collection+humanizers coll humanizers)
                                    humanized-collection->reported-rows))
                              batch)))
                    [t3 rows] (u/time-execution
                                (apply concat humanized-rows))]
                (csv/write-csv string-writer rows)
                (debug "Batch " (swap! idx-atom inc) " Size " (count batch)
                       "Write humanizer report of " (count rows) " rows"
                       "get humanized rows:" t2
                       "concat humanized rows:" t3))))]
      (debug "Create report " t4)
      (str string-writer))))
