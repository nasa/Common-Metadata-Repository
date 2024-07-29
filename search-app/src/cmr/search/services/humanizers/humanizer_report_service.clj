(ns cmr.search.services.humanizers.humanizer-report-service
  "Provides functions for reporting on humanizers"
  (:require
   [clojure.data.csv :as csv]
   [cmr.common-app.humanizer :as h]
   [cmr.common-app.data.metadata-retrieval.collection-metadata-cache :as cmn-coll-metadata-cache]
   [cmr.common-app.data.metadata-retrieval.revision-format-map :as crfm]
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [defjob default-job-start-delay]]
   [cmr.common.log :refer [info warn error]]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.util :as util]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.services.humanizers.humanizer-messages :as msg]
   [cmr.search.services.humanizers.humanizer-service :as hs]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.umm-spec.umm-spec-core :as umm-spec-core])
  (:import
   (java.io StringWriter)))

(def humanizer-report-cache-key
  "The key used to store the humanizer report cache in the system cache map."
  :humanizer-report-cache)

(def humanizer-not-found-error
  {:type :not-found :errors ["Humanizer does not exist."]})

(def CSV_HEADER
  ["provider", "concept_id", "short_name" "version", "original_value",
   "humanized_value"])

(defconfig humanizer-report-collection-batch-size
  "The size of the batches to use to process collections for the humanizer report"
  {:default 500 :type Long})

(defconfig humanizer-report-generator-job-delay
  "Number of seconds the humanizer-report-generator-job needs to wait after collection
  cache refresh job starts.

  We want to add the delay so that the collection cache can be populated first.
  Splunk shows the average time taken for collection cache to be refreshed is around
  300 seconds"
  {:default 400 :type Long})

(defconfig humanizer-report-generator-job-wait
  "Number of milli-seconds humanizer-generator-job waits for the collection cache
  to be populated in the event when the delay is not long enough."
  {:default 60000 :type Long}) ;; one minute

(defconfig retry-count
  "Number of times humanizer-report-generator-job retries to get the collections
  from collection cache."
  {:default 20 :type Long})

(defn create-humanizer-report-cache-client
  "Create instance of humanizer-report-cache client that connects to the redis cache"
  []
  (redis-cache/create-redis-cache {:keys-to-track [humanizer-report-cache-key]
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

(defn- rfm->umm-collection
  "Takes a revision format map and parses it into a UMM-spec record."
  [context revision-format-map]
  (let [concept-id (:concept-id revision-format-map)
        umm (try
              (umm-spec-core/parse-metadata
               context
               (crfm/revision-format-map->concept :native revision-format-map))
              (catch Exception e
                (error (format "Exception caught trying to parse %s to umm that was retrieved from a revision-format-map %s" concept-id e))))]
    (when umm
      (assoc umm
             :concept-id concept-id
             :provider-id (:provider-id (concepts/parse-concept-id concept-id))))))

(defn- get-collection-metadata-cache-concept-ids-with-retry
  "Get all the collection ids from the cache, if nothing is returned,
  Wait configurable number of seconds before retrying configurable number of times."
  [context]
  (loop [retries (retry-count)]
    (rl-util/log-redis-reading-start "humanizer report service get-collection-metadata-cache-concept-ids" cmn-coll-metadata-cache/cache-key)
    (if-let [vec-concept-ids (metadata-cache/get-collection-metadata-cache-concept-ids context)]
      vec-concept-ids
      (when (> retries 0)
        (warn (format (str "Failed reading %s for the humanizer report. "
                           "Humanizer report generator job is sleeping for %d second(s)"
                           " before retrying to fetch from collection cache.")
                      cmn-coll-metadata-cache/cache-key
                      (/ (humanizer-report-generator-job-wait) 1000)))
        (Thread/sleep (humanizer-report-generator-job-wait))
        (recur (dec retries))))))

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
  (let [concept-ids (get-collection-metadata-cache-concept-ids-with-retry context)
        humanizers (hs/get-humanizers context)
        string-writer (StringWriter.)]
    (csv/write-csv string-writer [CSV_HEADER])
    (doseq [concept-id concept-ids]
      (let [rfm (metadata-cache/get-concept-id context concept-id)
            collection (rfm->umm-collection context rfm)
            humanized-rows (-> (h/umm-collection->umm-collection+humanizers collection humanizers)
                               humanized-collection->reported-rows)]
        (csv/write-csv string-writer humanized-rows)))
    (str string-writer)))

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

(defn- create-and-save-humanizer-report
  "Helper function to create the humanizer report, save it to the cache, and return the content."
  [context]
  (rl-util/log-refresh-start humanizer-report-cache-key)
  (let [[report-generation-time humanizers-report] (util/time-execution
                                                    (safe-generate-humanizers-report-csv context))
        _ (info (format "Humanizer report generated in %d ms." report-generation-time))
        [tm _] (util/time-execution
                (cache/set-value (cache/context->cache context humanizer-report-cache-key)
                                 humanizer-report-cache-key
                                 humanizers-report))]
    (rl-util/log-redis-write-complete "create-and-save-humanizer-report" humanizer-report-cache-key tm)
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
    (let [cache (cache/context->cache context humanizer-report-cache-key)
          [tm report] (util/time-execution
                       (cache/get-value cache humanizer-report-cache-key))]
      (if report
        (do
          (rl-util/log-redis-read-complete "humanizers-report-csv" humanizer-report-cache-key tm)
          report)
        (let [report (safe-generate-humanizers-report-csv context)
              [tm _] (util/time-execution
                      (cache/set-value cache humanizer-report-cache-key report))]
          (rl-util/log-redis-read-complete "humanizers-report-csv" humanizer-report-cache-key tm)
          report)))))

;; A job for generating the humanizers report
(defjob HumanizerReportGeneratorJob
  [_ctx system]
  (create-and-save-humanizer-report {:system system}))

(defn refresh-humanizer-report-cache-job
  [job-key]
  "The job definition used by the system job scheduler to refresh the humanizer report cache.
  This cache is directly reliant on the collection-metadata-cache being refreshed and present (refresh-collections-metadata-cache-job).
  Currently the collection metadata cache is refreshed daily at 6 AM UTC. So we will start this job an hour after that."
  {:job-type HumanizerReportGeneratorJob
   :job-key job-key
   :start-delay (* 10 60) ;; 10 minutes delay
   :daily-at-hour-and-minute [07 00]})
