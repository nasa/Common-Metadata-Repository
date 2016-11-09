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
  indexing the same concept multiple times is fine, missing a concept is not. Transaction IDs have
  the same out of order problem as revision-dates, so we could not just use transaction IDs.

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
   [cmr.bootstrap.data.bulk-index :as bulk-index]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.date-time-parser :as p]
   [cmr.common.jobs :refer [def-stateful-job]]
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

(defn index-replicated-concepts
  "Indexes recently replicated concepts."
  [context]
  ;; Get the latest replicated revision date from the database
  (let [db (get-in context [:system :db])
        revision-datetime (j/with-db-transaction
                            [conn db]
                            (->> (su/find-one conn (select [(csk/->kebab-case-keyword
                                                             replication-date-column)]
                                                           (from replication-status-table)))
                                 :last_replicated_revision_date
                                 (oracle/oracle-timestamp->clj-time conn)))
        ;; Perform the indexing
        {:keys [max-revision-date]} (bulk-index/index-data-later-than-date-time
                                     (:system context)
                                     (t/minus revision-datetime (t/seconds buffer)))]
    ;; Update the latest replicated revision date in the database
    (when max-revision-date
      (let [stmt (format "UPDATE %s SET %s = ?" replication-status-table replication-date-column)]
        (j/db-do-prepared db stmt [(cr/to-sql-time max-revision-date)])))))

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
