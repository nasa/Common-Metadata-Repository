(ns cmr.transmit.search
  "Provide functions to invoke search app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.config :as config]
            [cheshire.core :as cheshire]
            [cmr.system-trace.http :as ch]
            [cmr.transmit.connection :as conn]))

(comment
  (let [context (cmr.common.dev.capture-reveal/reveal context)
        params (cmr.common.dev.capture-reveal/reveal params)]
  (config/context->app-connection context :search)
  ))

(defn find-granules
  "Find granules match the given parameters."
  [context params]
  (cmr.common.dev.capture-reveal/capture-all)
  (let [conn (config/context->app-connection context :search)
        request-url (str (conn/root-url conn) "/granules")
        response (client/get request-url {:accept :json
                                          :query-params params
                                          :headers (assoc (ch/context->http-headers context)
                                                          config/token-header (config/echo-system-token))
                                          :throw-exceptions false
                                          :connection-manager (conn/conn-mgr conn)})
        {:keys [status body]} response]
    (case status
      200 (cheshire/decode body true)
      ;; default
      (errors/internal-error!
        (format "Granule search failed. status: %s body: %s"
                status body)))))

