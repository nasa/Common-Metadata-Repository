(ns cmr.transmit.transformer
   "Provide functions to invoke the transformer app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.config :as config]
            [cheshire.core :as cheshire]
            [cmr.system-trace.http :as ch]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.transmit.connection :as conn]))

(deftracefn get-formatted-concept-revisions
  "Ask the transformer app for the concepts given by the concept-id, revision-id tuples in the
  given format."
  [context concept-tuples format]
  (let [conn (config/context->app-connection context :transformer)
        tuples-json-str (cheshire/generate-string concept-tuples)
        request-url (str (conn/root-url conn) "/concepts")
        response (client/post request-url {:body tuples-json-str
                                           :content-type :json
                                           :accept format
                                           :throw-exceptions false
                                           :headers (ch/context->http-headers context)
                                           :connection-manager (conn/conn-mgr conn)})
        status (:status response)]
    (case status
      404
      (let [err-msg "Unable to find all concepts."]
        (errors/throw-service-error :not-found err-msg))

      200
      (cheshire/decode (:body response) true)

      ;; default
      (errors/internal-error! (str "Get concept revisions failed. Transformer app response status code: "
                                   status
                                   " "
                                   response)))))