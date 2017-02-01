(ns cmr.bootstrap.services.replication
  "This namespace contains all of the logic for indexing recently replicated data from the AWS
  Database Migration Service (DMS).

  Due to limitations with Oracle 12c, we will only perform replication in the operational
  environment. (Operations is using Oracle 10g, while both SIT and UAT are using Oracle 12c.)

  We keep track of the most recently replicated revision-date that we have indexed in a database
  table. There is a job (which can be configured on or off) that if configured on runs every couple
  of minutes (interval is configurable) to determine what needs to be indexed and indexes those
  concepts. This includes all concept types. The job will only run on a single node.

  The job will check if the most recently replicated revision-date is different from what was
  indexed in the prior run. If it is different the job will index everything with a revision-date
  buffer of 20 seconds (buffer is configurable) before the most recently replicated revision-date
  to the current time.

  The reason we use a buffer is because concepts can be saved to the database with out of order
  revision-dates, but they should be extremely close in time. A 20 second buffer is overkill, but
  indexing the same concept multiple times is fine, missing a concept is not. Using Transaction IDs
  instead of revision-dates to try to track replication had several unknowns regarding whether we
  could reliably track the current transaction ID and whether transaction IDs could be 100%
  guaranteed to be in order. As a result we decided to use the revision-date approach.

  Note that during periods of time with no ingest we will continue to index the same concepts
  repeatedly though again this should not cause any problems other than increasing the number of
  documents in Elastic (because each index request will result in the creation of a new document and
  mark the prior document as deleted). We have a process to clean up deleted documents weekly, so
  this small increase in deleted documents should not be an operational concern."
  (:require
   [camel-snake-kebab.core :as csk]
   [clj-time.coerce :as cr]
   [clj-time.core :as t]
   [clojure.java.jdbc :as j]
   [clojure.string :as str]
   [cmr.bootstrap.data.bulk-index :as bulk-index]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [def-stateful-job]]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.metadata-db.data.oracle.concept-tables :as concept-tables]
   [cmr.metadata-db.data.oracle.providers :as providers]
   [cmr.metadata-db.services.concept-service :as s]
   [cmr.oracle.connection :as oracle]
   [cmr.oracle.sql-utils :as su :refer [select from]]))

(def replication-status-table
  "The name of the database table where replication status is stored."
  "REPLICATION_STATUS")

(def replication-date-column
  "The column name in the database storing the latest replication date for which we have processed
  indexing."
  "LAST_REPLICATED_REVISION_DATE")

(def buffer
  "Number of seconds buffer to account for the fact that revision dates are not guaranteed to be
  inserted into the database in chronological order."
  20)

(defconfig source-database-link
  "Database link used for retrieving metadata from the source database to correct replication errors
  caused by DMS in the target database."
  {:default "localhost" :type String})

(defconfig tables-with-null-blobs
  "Configuration with additional tables that contain NULL BLOBs."
  {:default #{} :type :edn})

;; ----------------------------
;; Helper for querying the METADATA_DB tables
;; Code taken from mdb-migrate-helper

(defn get-concept-tablenames
  "Returns a sequence of table names for the given concept types, or all concept types
  if none are specified, for all the existing providers."
  ([db]
   ;; use all concept types
   (apply get-concept-tablenames db (keys s/num-revisions-to-keep-per-concept-type)))
  ([db & concept-types]
   (distinct
    (->
     (for [provider (map providers/dbresult->provider
                         (j/query db [(format "SELECT * FROM providers@%s" (source-database-link))]))
           concept-type concept-types]
       (concept-tables/get-table-name provider concept-type))
      ;; Ensure that we return the small provider tables even if there are no providers in our
      ;; system yet.
     (into (when (contains? (set concept-types) :collection) ["small_prov_collections"]))
     (into (when (contains? (set concept-types) :granule) ["small_prov_granules"]))
     (into (when (contains? (set concept-types) :service) ["small_prov_services"]))
     (into (when (contains? (set concept-types) :access-group) ["cmr_groups"]))
     (into (when (contains? (set concept-types) :acl) ["cmr_acls"]))
     (into (when (contains? (set concept-types) :tag) ["cmr_tags"]))
     (into (when (contains? (set concept-types) :humanizer) ["cmr_humanizers"]))))))


(defn fix-null-replicated-concepts-query-str
  "Query to fix the NULL replicated concepts. Takes the table name and revision date-time string."
  [table revision-datetime]
  (format (str "update %s c1 set metadata = (select c2.metadata from %s@%s c2 where c2.id = c1.id) "
               "where c1.metadata is null and c1.deleted = 0 and c1.revision_date > to_timestamp "
               "('%s', 'YYYY-MM-DD HH24:MI:SS.FF')")
          table
          table
          (source-database-link)
          (.toString (cr/to-sql-time revision-datetime))))

(defn- fix-null-replicated-blobs
  "AWS DMS is replicating BLOBs that are over 4K in size as NULL. We need to identify all of them
  and fix them."
  [db revision-datetime]
  (let [all-tables (apply conj (get-concept-tablenames db :collection :service :access-group :acl
                                                       :tag :humanizer)
                          (tables-with-null-blobs))]
    (doseq [table all-tables]
      (let [curr-time (System/currentTimeMillis)
            stmt (fix-null-replicated-concepts-query-str table revision-datetime)]
        (debug "Fixing replicated BLOBs for:" table "starting with statement:" stmt)
        (j/db-do-commands db stmt)
        (debug "Fixing replicated BLOBs for:" table "with revision-datetime" revision-datetime
               "took" (- (System/currentTimeMillis) curr-time)) "ms"))))

(defn index-replicated-concepts
  "Indexes recently replicated concepts."
  [context]
  ;; Get the latest replicated revision date from the database
  (let [bootstrap-db (get-in context [:system :db])
        metadata-db (get-in context [:system :embedded-systems :metadata-db :db])
        revision-datetime (j/with-db-transaction
                            [conn bootstrap-db]
                            (->> (su/find-one conn (select [(csk/->kebab-case-keyword
                                                             replication-date-column)]
                                                           (from replication-status-table)))
                                 :last_replicated_revision_date
                                 (oracle/oracle-timestamp->clj-time conn)))
        starting-time (System/currentTimeMillis)
        ;; Fix any NULL replicated BLOBs
        _ (fix-null-replicated-blobs metadata-db revision-datetime)
        ;; Perform the indexing
        {:keys [max-revision-date]} (bulk-index/index-data-later-than-date-time
                                     (:system context)
                                     (t/minus revision-datetime (t/seconds buffer)))
        ms-taken (- (System/currentTimeMillis) starting-time)
        ;; Change the max-revision-date to account for how long the whole task took to run to ensure
        ;; no concepts are missed
        max-revision-date (when max-revision-date
                            (t/minus max-revision-date (t/millis ms-taken)))]
    ;; Update the latest replicated revision date in the database
    (when max-revision-date
      (let [stmt (format "UPDATE %s SET %s = ?" replication-status-table replication-date-column)]
        (j/db-do-prepared bootstrap-db stmt [(cr/to-sql-time max-revision-date)])))))

;; ------------
;; Jobs

(defconfig recently-replicated-interval
  "How often to index recently replicated concepts."
  {:default 120 :type Long})

(defconfig index-recently-replicated
  "Whether to index recently replicated concepts by a background job. This should only be set to
  true in environments where there is an actively running DMS task replicating data from another
  environment."
  {:default false :type Boolean})

(def-stateful-job IndexRecentlyReplicatedJob
  [context system]
  (index-replicated-concepts {:system system}))

(def index-recently-replicated-job
  {:job-type IndexRecentlyReplicatedJob
   :interval (recently-replicated-interval)})
