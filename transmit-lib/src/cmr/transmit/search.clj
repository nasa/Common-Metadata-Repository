(ns cmr.transmit.search
  "Provide functions to invoke search app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.config :as config]
            [cheshire.core :as cheshire]
            [cmr.system-trace.http :as ch]
            [cmr.transmit.connection :as conn]
            [cmr.common.util :as util :refer [defn-timed]]))

(defn-timed find-granule-hits
  "Returns granule hits that match the given search parameters."
  [context params]
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules")
        response (client/get request-url {:accept :xml
                                          :query-params (assoc params :page-size 0)
                                          :headers (assoc (ch/context->http-headers context)
                                                          config/token-header (config/echo-system-token))
                                          :throw-exceptions false
                                          :connection-manager (conn/conn-mgr conn)})
        {:keys [status headers body]} response]
    (case status
      200 (Integer/parseInt (get headers "CMR-Hits"))
      ;; default
      (errors/internal-error!
        (format "Granule search failed. status: %s body: %s"
                status body)))))

