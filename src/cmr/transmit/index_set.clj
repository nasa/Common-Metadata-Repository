(ns cmr.transmit.index-set
  "Provide functions to invoke index set app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cheshire.core :as cheshire]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]))

(defn get-index-set
  "Submit a request to index-set app to fetch an index-set assoc with an id"
  [context id]
  (let [conn (config/context->app-connection context :index-set)
        response (client/request
                   {:method :get
                    :url (format "%s/index-sets/%s" (conn/root-url conn) (str id))
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (conn/conn-mgr conn)
                    :headers {"echo-token" (config/echo-system-token)}})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    (case status
      404 nil
      200 body
      (errors/internal-error! (format "Unexpected error fetching index-set with id: %s,
                                      Index set app reported status: %s, error: %s"
                                      id status (pr-str (flatten (:errors body))))))))
