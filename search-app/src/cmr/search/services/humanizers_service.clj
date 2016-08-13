(ns cmr.search.services.humanizers-service
  "Provides functions for reporting on humanizers"
  (require [cmr.common.concepts :as concepts]
           [cmr.common.util :as u]
           [cmr.common-app.humanizer :as humanizer]
           [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
           [cmr.search.data.metadata-retrieval.revision-format-map :as rfm]
           [cmr.umm-spec.legacy :as umm-legacy]
           [cmr.common.log :as log :refer (debug info warn error)]
           [clojure.data.csv :as csv])
  (:import java.io.StringWriter))

(def CSV_HEADER
  ["provider", "concept_id", "short_name" "version", "original_value", "humanized_value"])

(def report-collection-batch-size
  "The size of the batches to use to process collections for the humanizer report"
  100)

(defn- rfm->umm-collection
  "Takes a revision format map and parses it into a UMM lib record."
  [context revision-format-map]
  (let [concept-id (:concept-id revision-format-map)
        umm (umm-legacy/parse-concept
             context
             (rfm/revision-format-map->concept :native revision-format-map))]
    (assoc umm
           :concept-id concept-id
           :provider-id (:provider-id (concepts/parse-concept-id concept-id)))))

(defn- rfms->umm-collections
  [context rfms]
  (map #(rfm->umm-collection context %) rfms))

(defn- get-all-collections
  "Retrieves all collections from the Metadata cache"
  [context n]
    ;; Currently not throwing an exception if the cache is empty. May want to change in the future
    ;; to throw an exception.
    ;; TODO should this use pmap? (Test performance in workload and then look at times if too slow.)
    ;(map #(rfm->umm-collection context %) (metadata-cache/all-cached-revision-format-maps context)))
  (let [[t1 rfms] (u/time-execution (metadata-cache/all-cached-revision-format-maps context))]
    (debug "Get rfms" t1)
    (let [[t2 collections] (u/time-execution (u/map-n-all #(rfms->umm-collections context %) n rfms))]
      (debug "Convert to collections" t2)
      collections)))

(comment
 (do
   (def context {:system (get-in user/system [:apps :search])})
   (metadata-cache/refresh-cache context))

 (->> (get-all-collections context)
      (map humanizer/umm-collection->umm-collection+humanizers)
      (mapcat humanized-collection->reported-fields)))

(defn humanized-collection->reported-rows
  "Takes a humanized collection and returns rows to populate the CSV report."
  [collection]
  (let [{:keys [provider-id concept-id product]} collection
        {:keys [short-name version-id]} product]
    (for [paths (vals humanizer/humanizer-field->umm-paths)
          path paths
          parents (u/get-in-all collection (drop-last path))
          :let [parents (if (sequential? parents)
                          parents
                          [parents])]
          parent parents
          :let [field (last path)
                humanized-value (get parent (humanizer/humanizer-key field))]
          :when (and (some? humanized-value) (:reportable humanized-value))
          :let [humanized-string-value (:value humanized-value)
                original-value (get parent field)]]
      [provider-id concept-id short-name version-id original-value humanized-string-value])))

(defn- get-humanized-rows
  [collections]
  (pmap (fn [coll]
          (->> coll
               humanizer/umm-collection->umm-collection+humanizers
               humanized-collection->reported-rows))
    collections))

(defn humanizers-report-csv
  "Returns a report on humanizers in use in collections as a CSV."
  [context n]
  (let [[t1 collection-batches] (u/time-execution
                                 (get-all-collections context n))
        string-writer (StringWriter.)
        idx-atom (atom 0)]
    (debug "get-all-collections:" t1
           "processing " (count collection-batches) " batches of size" n)
    (csv/write-csv string-writer [CSV_HEADER])
    (doseq [batch collection-batches]
     (debug "processing batch " (swap! idx-atom inc) " of size " (count batch))
     (let [[t2 humanized-rows] (u/time-execution
                                   (pmap (fn [coll]
                                           (->> coll
                                                humanizer/umm-collection->umm-collection+humanizers
                                                humanized-collection->reported-rows))
                                         batch))]
       (debug "get humanized rows" t2)
       (let [[t3 rows] (u/time-execution
                         (apply concat humanized-rows))]
          (debug "write " (count rows) "rows to csv")
          (csv/write-csv string-writer rows)
        (debug "Write humanizer report of " (count humanized-rows) " rows"
               "In batches of size " n
               "get-all-collections:" t1
               "get humanized rows:" t2
               "concat humanized rows:" t3))))
    (debug "Finished processing batches")
    (str string-writer)))
