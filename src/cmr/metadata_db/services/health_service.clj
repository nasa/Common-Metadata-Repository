(ns cmr.metadata-db.services.health-service
  (require [cmr.oracle.connection :as conn]
           [cmr.transmit.echo.rest :as rest]
           [cmr.system-trace.core :refer [deftracefn]]
           [cmr.metadata-db.services.util :as util]))

(deftracefn health
  "Returns the health state of the app."
  [context]
  (let [db-health (conn/health (util/context->db context))
        echo-rest-health (rest/health context)
        ok? (every? :ok? [db-health echo-rest-health])]
    {:ok? ok?
     :dependencies {:oracle db-health
                    :echo echo-rest-health}}))

