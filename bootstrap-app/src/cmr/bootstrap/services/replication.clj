(ns cmr.bootstrap.services.replication
  "This namespace contains all of the logic for indexing recently replicated data from the AWS
  Database Migration Service (DMS).

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
  the same out of order problem as revision-dates, so we could not just use transaction IDs."
  (:require
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [def-stateful-job]]))

(def replication-status-table
  "The name of the database table where replication status is stored."
  "REPLICATION_STATUS")

(def buffer
  "Number of seconds buffer to account for the fact that revision dates are not guaranteed to be
  inserted into the database in chronological order, but will be extremely close."
  20)

(defn index-replicated-concepts
  "TODO: Indexes recently replicated concepts."
  [system]
  nil)

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
  (index-replicated-concepts system))

(def index-recently-replicated-job
  {:job-type IndexRecentlyReplicatedJob
   :interval (recently-replicated-interval)})
