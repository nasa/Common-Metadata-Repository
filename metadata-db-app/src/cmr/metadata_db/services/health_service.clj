(ns cmr.metadata-db.services.health-service
  (:require
   [cmr.common.services.health-helper :as hh]
   [cmr.metadata-db.services.util :as db-util]
   [cmr.oracle.connection :as conn]
   [cmr.common.util :as util]))

(defn- health-fn
  "Returns the health state of the app."
  [context]
  (let [db-health (conn/health (db-util/context->db context))
        ok? (every? :ok? [db-health])]
    {:ok? ok?
     :dependencies {:oracle db-health}}))

(defn health
  "Returns the metadata-db health with timeout handling."
  [context]
  (let [timeout-ms (* util/seconds-in-milliseconds (+ 1 (hh/health-check-timeout-seconds)))]
    (hh/get-health #(health-fn context) timeout-ms)))
