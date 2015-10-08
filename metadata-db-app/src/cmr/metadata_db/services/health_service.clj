(ns cmr.metadata-db.services.health-service
  (require [cmr.oracle.connection :as conn]
           [cmr.transmit.echo.rest :as rest]
           [cmr.common.services.health-helper :as hh]
           [cmr.metadata-db.services.util :as util]))

(defn- health-fn
  "Returns the health state of the app."
  [context]
  (let [db-health (conn/health (util/context->db context))
        echo-rest-health (rest/health context)
        ok? (every? :ok? [db-health echo-rest-health])]
    {:ok? ok?
     :dependencies {:oracle db-health
                    :echo echo-rest-health}}))

(defn health
  "Returns the metadata-db health with timeout handling."
  [context]
  (let [timeout-ms (* 1000 (+ 1 (hh/health-check-timeout-seconds)))]
    (hh/get-health #(health-fn context) timeout-ms)))
