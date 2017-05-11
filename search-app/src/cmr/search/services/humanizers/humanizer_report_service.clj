(ns cmr.search.services.humanizers.humanizer-report-service
  "Provides functions for reporting on humanizers"
  (:require
   [clojure.data.csv :as csv]
   [cmr.common-app.humanizer :as h]
   [cmr.common-app.cache.consistent-cache :as consistent-cache]
   [cmr.common-app.cache.cubby-cache :as cubby-cache]
   [cmr.common.cache.fallback-cache :as fallback-cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as u]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.data.metadata-retrieval.revision-format-map :as rfm]
   [cmr.search.services.humanizers.humanizer-service :as hs]
   [cmr.umm-spec.umm-spec-core :as umm-spec-core])
  (:import
   (java.io StringWriter)))

(def csv-report-cache-key
  "The key used when setting the cache value of the report data."
  "humanizer-report")

(def CSV_HEADER
  ["provider", "concept_id", "short_name" "version", "original_value", "humanized_value"])

(defconfig humanizer-report-collection-batch-size
  "The size of the batches to use to process collections for the humanizer report"
  {:default 500 :type Long})

(defn- rfm->umm-collection
  "Takes a revision format map and parses it into a UMM-spec record."
  [context revision-format-map]
  (let [concept-id (:concept-id revision-format-map)
        umm (umm-spec-core/parse-metadata
             context
             (rfm/revision-format-map->concept :native revision-format-map))]
    (assoc umm
           :concept-id concept-id
           :provider-id (:provider-id (concepts/parse-concept-id concept-id)))))

(defn- rfms->umm-collections
  "Parse multiple revision format maps into UMM-spec records"
  [context rfms]
  (map #(rfm->umm-collection context %) rfms))

(defn- get-all-collections
  "Retrieves all collections from the Metadata cache, partitions them into batches of size
  humanizer-report-collection-batch-size, so the batches can be processed lazily to avoid out of memory errors."
  [context]
  ;; Currently not throwing an exception if the cache is empty. May want to change in the future
  ;; to throw an exception.
  (let [rfms (metadata-cache/all-cached-revision-format-maps context)]
    (map
     #(rfms->umm-collections context %)
     (partition-all (humanizer-report-collection-batch-size) rfms))))

(defn humanized-collection->reported-rows
  "Takes a humanized collection and returns rows to populate the CSV report."
  [collection]
  (let [{:keys [provider-id concept-id ShortName Version]} collection]
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
      [provider-id concept-id ShortName Version original-value humanized-string-value])))

(defn generate-humanizers-report-csv
  "Returns a report on humanizers in use in collections as a CSV."
  [context]
  (let [[t1 collection-batches] (u/time-execution
                                  (get-all-collections context))
         string-writer (StringWriter.)
         idx-atom (atom 0)]
    (info "get-all-collections:" t1
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
                (info "Batch " (swap! idx-atom inc) " Size " (count batch)
                       "Write humanizer report of " (count rows) " rows"
                       "get humanized rows:" t2
                       "concat humanized rows:" t3))))]
      (info "Create report " t4)
      (str string-writer))))

(defn safe-generate-humanizers-report-csv
  "This convenience function wraps the report generator in a try/catch."
  [context]
  (try
    (generate-humanizers-report-csv context)
    (catch Exception e
      (warn (.getMessage e) "Returning empty report."))))

(def report-cache-key
  "The key used to store the humanizer report cache in the system cache map."
  :report-cache)

(defn get-cache
  "A utility function for returning the system report cache instance."
  [context]
  (get-in context [:system :caches report-cache-key]))

(defconfig report-cache-consistent-timeout-seconds
  "The number of seconds between when the report cache should check with cubby
  for data consistency."
  {:default 3600
   :type Long})

(defn create-report-cache
  "Used to create the cache that will be used for caching the humanizer
  report. We get the following features with this setup:
  * A cubby cache that holds the generated report
  * A fast access in-memory cache that sits on top of cubby, providing
    quick local results after the first call to cubby
  * A single-threaded "
  []
  (fallback-cache/create-fallback-cache
   (consistent-cache/create-consistent-cache
    {:hash-timeout-seconds (report-cache-consistent-timeout-seconds)})
   (cubby-cache/create-cubby-cache)))

;; A job for generating the humanizers report
(defjob HumanizerReportGeneratorJob
  [ctx system]
  (let [context {:system system}]
    (.set-value
     (get-cache context)
     csv-report-cache-key
     (safe-generate-humanizers-report-csv context))))

(def humanizer-report-generator-job
  {:job-type HumanizerReportGeneratorJob
   ;; 24 hours in seconds
   ;:interval (* 60 60 24)
   :interval 10
   :start-delay 0})

(defn humanizers-report-csv
  "Returns a report on humanizers in use in collections as a CSV.

  This is the function that is called by the web service."
  [context]
  (let [data (.get-value
              (get-cache context)
              csv-report-cache-key)]
    ;; XXX do check on data ... if not there, get it or something
    data))
