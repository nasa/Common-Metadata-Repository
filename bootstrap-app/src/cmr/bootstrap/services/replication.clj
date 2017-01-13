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
   [cmr.metadata-db.data.providers :as providers]
   [cmr.metadata-db.services.concept-service :as s]
   [cmr.metadata-db.services.provider-validation :as pv]
   [cmr.oracle.connection :as oracle]
   [cmr.oracle.sql-utils :as su :refer [select from]]
   [inflections.core :as inf])
  (:import
   (cmr.oracle.connection OracleStore)))

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

;; ----------------------------
;; Helpers for querying the METADATA_DB tables
;; Code taken from mdb-migrate-helper, concept-tables, and Oracle providers namespaces.
(defn dbresult->provider
  "Converts a map result from the database to a provider map"
  [{:keys [provider_id short_name cmr_only small]}]
  {:provider-id provider_id
   :short-name short_name
   :cmr-only (== 1 cmr_only)
   :small (== 1 small)})

(extend-protocol providers/ProvidersStore
  OracleStore

  (get-providers
    [db]
    (map dbresult->provider
         (j/query db ["SELECT * FROM METADATA_DB.providers"]))))

(def all-provider-concept-types
  "All the concept types that have tables for each (non-small) provider"
  [:collection :granule :service])

(defmulti get-table-name
  "Get the name for the table for a given provider and concept-type"
  (fn [provider-id concept-type]
    concept-type))

(defmethod get-table-name :access-group
  [_ _]
  "METADATA_DB.cmr_groups")

(defmethod get-table-name :acl
  [_ _]
  "METADATA_DB.cmr_acls")

(defmethod get-table-name :tag
  [_ _]
  "METADATA_DB.cmr_tags")

(defmethod get-table-name :tag-association
  [_ _]
  "METADATA_DB.cmr_tag_associations")

(defmethod get-table-name :humanizer
  [_ _]
  "METADATA_DB.cmr_humanizers")

(defmethod get-table-name :default
  [provider concept-type]
  ;; Dont' remove the next line - needed to prevent SQL injection
  (pv/validate-provider provider)
  (let [{:keys [provider-id small]} provider
        db-provider-id (if small pv/small-provider-id provider-id)]
    (format "METADATA_DB.%s_%s" (str/lower-case db-provider-id) (inf/plural (name concept-type)))))

(defn get-collection-tablenames
  "Gets a list of all the collection tablenames. Primarily for enabling migrations of existing
  provider tables."
  [db]
  (distinct (map #(get-table-name % :collection) (providers/get-providers db))))

(defn get-granule-tablenames
  "Gets a list of all the granule tablenames. Primarily for enabling migrations of existing
  provider tables."
  [db]
  (distinct (map #(get-table-name % :granule) (providers/get-providers db))))

(defn get-concept-tablenames
  "Returns a sequence of table names for the given concept types, or all concept types
  if none are specified, for all the existing providers."
  ([db]
   ;; use all concept types
   (apply get-concept-tablenames db (keys s/num-revisions-to-keep-per-concept-type)))
  ([db & concept-types]
   (distinct
    (->
     (for [provider (providers/get-providers db)
           concept-type concept-types]
       (get-table-name provider concept-type))
      ;; Ensure that we return the small provider tables even if there are no providers in our
      ;; system yet.
     (into (when (contains? (set concept-types) :collection) ["METADATA_DB.small_prov_collections"]))
     (into (when (contains? (set concept-types) :granule) ["METADATA_DB.small_prov_granules"]))
     (into (when (contains? (set concept-types) :service) ["METADATA_DB.small_prov_services"]))
     (into (when (contains? (set concept-types) :access-group) ["METADATA_DB.cmr_groups"]))
     (into (when (contains? (set concept-types) :tag) ["METADATA_DB.cmr_tags"]))))))

(defconfig source-database-link
  "Database link used for retrieving metadata from the source database to correct replication errors
  caused by DMS in the target database."
  {:default "localhost" :type String})

(defn fix-null-replicated-concepts-query-str
  "Query to fix the NULL replicated concepts. Needs 4 parameters - replicated destination table,
  source table, database link, revision date time."
  [table revision-datetime]
  (format "update %s c1 set metadata = (select c2.metadata from %s@%s c2 where c2.id = c1.id) where c1.metadata is null and c1.deleted = 0 and c1.revision_date > to_timestamp ('%s', 'YYYY-MM-DD HH24:MI:SS.FF')"
          table
          table
          (source-database-link)
          (.toString (cr/to-sql-time revision-datetime))))

(defn- fix-null-replicated-blobs
  "AWS DMS is replicating BLOBs that are over 4K in size as NULL. We need to identify all of them
  and fix them."
  [db revision-datetime]
  (let [
        all-tables (conj (get-concept-tablenames db :collection) "METADATA_DB.NSIDC_ECS_GRANULES")]
    (doseq [table all-tables]
      (let [curr-time (System/currentTimeMillis)
            stmt (fix-null-replicated-concepts-query-str table revision-datetime)]
        (debug "Fixing replicated BLOBs for:" table "starting with statement:" stmt)
        (j/db-do-commands db stmt)
        (debug "Fixing replicated BLOBs for:" table "with revision-datetime" revision-datetime "took" (- (System/currentTimeMillis) curr-time)) "ms"))))

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
        ;; Fix any NULL replicated BLOBs
        _ (fix-null-replicated-blobs metadata-db revision-datetime)
        ;; Perform the indexing
        {:keys [max-revision-date]} (bulk-index/index-data-later-than-date-time
                                     (:system context)
                                     (t/minus revision-datetime (t/seconds buffer)))]
    ;; Update the latest replicated revision date in the database
    (when max-revision-date
      (let [stmt (format "UPDATE %s SET %s = ?" replication-status-table replication-date-column)]
        (j/db-do-prepared bootstrap-db stmt [(cr/to-sql-time max-revision-date)])))))

(defconfig recently-replicated-interval
  "How often to index recently replicated concepts."
  {:default 120 :type Long})

(defconfig index-recently-replicated
  "Whether to index recently replicated concepts by a background job. This should only be set to
  true in environments where there is an actively running DMS task replicating data from another
  environment."
  {:default false :type Boolean})

;; ------------
;; Jobs

(def-stateful-job IndexRecentlyReplicatedJob
  [context system]
  (index-replicated-concepts {:system system}))

(def index-recently-replicated-job
  {:job-type IndexRecentlyReplicatedJob
   :interval (recently-replicated-interval)})

(comment
 (defn connect-to-db
   "Creates and returns a database connection"
   [connection-name db-url user password]
   (cmr.common.lifecycle/start
    (cmr.oracle.connection/create-db (cmr.oracle.connection/db-spec connection-name db-url false "" user password)) nil))

 (def wl-rds-db
    "RDS WL instance connection"
    (connect-to-db "NGAP-ORACLE-WL-RDS2" "thin:@url" "METADATA_DB" "xxxxx"))

 (fix-null-replicated-blobs wl-rds-db "3016-01-01T10:00:00Z")

 (let [boot-sys (cmr.bootstrap.system/create-system)
       db (cmr.common.lifecycle/start (get-in boot-sys [:embedded-systems :metadata-db :db]) boot-sys)]
   (fix-null-replicated-blobs db "3016-01-01T10:00:00Z")))
