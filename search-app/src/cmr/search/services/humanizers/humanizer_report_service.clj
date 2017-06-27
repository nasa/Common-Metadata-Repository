(ns cmr.search.services.humanizers.humanizer-report-service
  "Provides functions for reporting on humanizers"
  (:require
   [clojure.data.csv :as csv]
   [cmr.common-app.humanizer :as h]
   [cmr.common-app.cache.consistent-cache :as consistent-cache]
   [cmr.common-app.cache.cubby-cache :as cubby-cache]
   [cmr.common.cache.fallback-cache :as fallback-cache]
   [cmr.common.cache :as cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as util]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.data.metadata-retrieval.revision-format-map :as rfm]
   [cmr.search.services.humanizers.humanizer-messages :as msg]
   [cmr.search.services.humanizers.humanizer-service :as hs]
   [cmr.umm-spec.umm-spec-core :as umm-spec-core])
  (:import
   (java.io StringWriter)))

(def report-cache-key
  "The key used to store the humanizer report cache in the system cache map."
  :humanizer-report-cache)

(def csv-report-cache-key
  "The key used when setting the cache value of the report data."
  :humanizer-report)

(def humanizer-not-found-error
  {:type :not-found :errors ["Humanizer does not exist."]})

(def CSV_HEADER
  ["provider", "concept_id", "short_name" "version", "original_value",
   "humanized_value"])

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
  ;; Currently not throwing an exception if the cache is empty. May want to
  ;; change in the future to throw an exception.
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
          parents (util/get-in-all collection (drop-last path))
          :let [parents (util/seqify parents)]
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
  (let [[t1 collection-batches] (util/time-execution
                                  (get-all-collections context))
         string-writer (StringWriter.)
         idx-atom (atom 0)]
    (info (format "get-all-collections: %d ms. Processing %d batches of size %d"
                  t1
                  (count collection-batches)
                  (humanizer-report-collection-batch-size)))
    (csv/write-csv string-writer [CSV_HEADER])
    (let [humanizers (hs/get-humanizers context)
          [t4 csv-string]
          (util/time-execution
            (doseq [batch collection-batches]
              (let [[t2 humanized-rows]
                    (util/time-execution
                      (doall
                        (pmap (fn [coll]
                                (-> (h/umm-collection->umm-collection+humanizers coll humanizers)
                                    humanized-collection->reported-rows))
                              batch)))
                    [t3 rows] (util/time-execution
                                (apply concat humanized-rows))]
                (csv/write-csv string-writer rows)
                (info "Batch " (swap! idx-atom inc) " Size " (count batch)
                       "Write humanizer report of " (count rows) " rows"
                       "get humanized rows:" t2
                       "concat humanized rows:" t3))))]
      (info "Create report " t4)
      (str string-writer))))

(defn safe-generate-humanizers-report-csv
  "This convenience function wraps the report generator in a try/catch,
  thowing no exception when there is no humanizer, in which case an empty
  report is generated. All other exceptions will be propogated."
  [context]
  (try
    (generate-humanizers-report-csv context)
    (catch Exception e
      (if (= (ex-data e) humanizer-not-found-error)
        (warn (.getMessage e) msg/returning-empty-report)
        (throw e)))))

(defn create-report-cache
  "This function creates the composite cache that is used for caching the
  humanizer report. With the given composition we get the following features:
  * A cubby cache that holds the generated report (centralized storage in
    an ElasticSearch backend);
  * A fast access in-memory cache that sits on top of cubby, providing
    quick local results after the first call to cubby; this cache is kept
    consistent across all instancs of CMR, so no matter which host the LB
    serves, all the content is the same;
  * A single-threaded cache that circumvents potential race conditions
    between HTTP requests for a report and Quartz cluster jobs that save
    report data.

  Note that in the future this particular cache may be useful for other
  content we generate via one or more job schedulers, at which point we'll
  want to move it to a more general ns."
  []
  (stl-cache/create-single-thread-lookup-cache
   (fallback-cache/create-fallback-cache
    (consistent-cache/create-consistent-cache)
    (cubby-cache/create-cubby-cache))))

(defn- create-and-save-humanizer-report
  "Helper function to create the humanizer report, save it to the cache, and return the content."
  [context]
  (info "Generating humanizer report.")
  (let [[report-generation-time humanizers-report] (util/time-execution
                                                    (safe-generate-humanizers-report-csv context))]
    (info (format "Humanizer report generated in %d ms." report-generation-time))
    (cache/set-value (cache/context->cache context report-cache-key)
                     csv-report-cache-key
                     humanizers-report)
    humanizers-report))

(defn humanizers-report-csv
  "Returns a report of the humanizers currently used in collections as a CSV.
  If the report has not yet been generated by the job scheduler, a write is
  queued to the single-threaded cache followed immediately by a read for the
  newly cached data.

  This is the function that is called by the web service."
  [context regenerate?]
  (if regenerate?
    (create-and-save-humanizer-report context)
    (cache/get-value (cache/context->cache context report-cache-key)
                     csv-report-cache-key
                     ;; If there is a cache miss, generate the report and then
                     ;; return its value
                     #(safe-generate-humanizers-report-csv context))))

;; A job for generating the humanizers report
(defjob HumanizerReportGeneratorJob
  [_ctx system]
  (create-and-save-humanizer-report {:system system}))

(def humanizer-report-generator-job
  "The job definition used by the system job scheduler."
  {:job-type HumanizerReportGeneratorJob
   :interval (* 60 60 24) ; every 24 hours
   :start-delay 0})
