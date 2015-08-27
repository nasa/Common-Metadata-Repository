(ns cmr.bootstrap.services.jobs
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.jobs :refer [def-stateful-job]]
            [cmr.bootstrap.config :as cfg]
            [cmr.bootstrap.services.bootstrap-service :as bootstrap-service]))

(def-stateful-job DbSynchronizeJob
  [ctx system]
  (bootstrap-service/db-synchronize
    {:system system}
    true ;; synchronous
    {:sync-types [:missing :deletes]}))

(defn jobs
  "Returns the background jobs that needs to be run in bootstrap app."
  []
  (when (cfg/db-synchronization-enabled)
    [{:job-type DbSynchronizeJob
      :daily-at-hour-and-minute [23 59]}]))
