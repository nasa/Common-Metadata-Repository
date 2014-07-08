(ns cmr.transmit.transformer
   "Provide functions to invoke the transformer app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.config :as config]
            [cheshire.core :as json]
            [cmr.system-trace.http :as ch]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.transmit.connection :as conn]
            [cmr.common.mime-types :as mt]))

(deftracefn get-formatted-concept-revisions
  "Ask the transformer app for the concepts given by the concept-id, revision-id tuples in the
  given format."
  [context concept-tuples format]
  (let [conn (config/context->app-connection context :transformer)
        mime-type (mt/format->mime-type format)
        tuples-json-str (json/encode concept-tuples)
        request-url (str (conn/root-url conn) "/concepts")
        response (client/post request-url {:body tuples-json-str
                                           :content-type :json
                                           :accept mime-type
                                           :throw-exceptions false
                                           :headers (ch/context->http-headers context)
                                           :connection-manager (conn/conn-mgr conn)})
        status (:status response)]
    (case status
      404
      (let [err-msg "Unable to find all concepts."]
        (errors/throw-service-error :not-found err-msg))

      200
      (json/decode (:body response) true)

      ;; default
      (errors/internal-error! (str "Get concept revisions failed. Transformer app response status code: "
                                   status
                                   " "
                                   response)))))

(deftracefn get-latest-formatted-concepts
  "Ask the transformer app for the latest version of the concepts given by the concept-ids in the
  given format."
  [context concept-ids format]
  (let [conn (config/context->app-connection context :transformer)
        mime-type (mt/format->mime-type format)
        ids-json-str (json/encode concept-ids)
        request-url (str (conn/root-url conn) "/latest-concepts")
        response (client/post request-url {:body ids-json-str
                                           :content-type :json
                                           :accept mime-type
                                           :throw-exceptions false
                                           :headers (ch/context->http-headers context)
                                           :connection-manager (conn/conn-mgr conn)})
        status (:status response)]
    (case status
      404
      (let [err-msg "Unable to find all concepts."]
        (errors/throw-service-error :not-found err-msg))

      200
      (json/decode (:body response) true)

      ;; default
      (errors/internal-error! (str "Get concept revisions failed. Transformer app response status code: "
                                   status
                                   " "
                                   response)))))