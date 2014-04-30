(ns cmr.transmit.metadata-db
  "Provide functions to invoke metadata db app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [cmr.system-trace.http :as ch]
            [cmr.system-trace.core :refer [deftracefn]]))

;; Defines the host and port of metadata db
(def endpoint
  {:host "localhost"
   :port "3001"})

(deftracefn get-concept
  "Retrieve the concept with the given concept and revision-id"
  [context concept-id revision-id]
  (let [{:keys [host port]} endpoint
        response (client/get (format "http://%s:%s/concepts/%s/%s" host port concept-id revision-id)
                             {:accept :json
                              :throw-exceptions false
                              :headers (ch/context->http-headers context)})]
    (if (= 200 (:status response))
      (cheshire/decode (:body response) true)
      (errors/throw-service-error
        :not-found
        (str "Failed to retrieve concept " concept-id "/" revision-id " from metadata-db: " (:body response))))))

(deftracefn get-latest-concept
  "Retrieve the latest version of the concept"
  [context concept-id]
  (let [{:keys [host port]} endpoint
        response (client/get (format "http://%s:%s/concepts/%s" host port concept-id)
                             {:accept :json
                              :throw-exceptions false
                              :headers (ch/context->http-headers context)})]
    (if (= 200 (:status response))
      (cheshire/decode (:body response) true)
      (errors/throw-service-error
        :not-found
        (str "Failed to retrieve concept " concept-id " from metadata-db: " (:body response))))))

