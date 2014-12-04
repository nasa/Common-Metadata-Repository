(ns cmr.bootstrap.services.jobs
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.jobs :refer [def-stateful-job]]
            [cmr.common.config :as cfg]
            [cmr.bootstrap.services.bootstrap-service :as bootstrap-service]
            [clj-time.core :as t]))

(def-stateful-job DbSynchronizeJob
  [ctx system]
  (warn "Boostrap db synchronize job is disabled for now via code until we're ready to enable it.")
  #_(bootstrap-service/db-synchronize
    {:system system}
    true ;; synchronous
    {:sync-types [:missing :deletes]}))

(def jobs
  [{:job-type DbSynchronizeJob
   :daily-at-hour-and-minute [23 59]}])
