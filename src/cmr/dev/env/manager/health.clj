(ns cmr.dev.env.manager.health
  (:require
    [cheshire.core :as json]
    [clj-http.client :as httpc]
    [cmr.transmit.config :as transmit]
    [taoensso.timbre :as log]))

(def http-default-opts
  {:headers {:echo-token transmit/mock-echo-system-token}
   :accept :json
   :throw-exceptions false})

(defn http-health-resource
  [service-key]
  (str (transmit/application-public-root-url service-key)
       "health"))

(defn parse-response
  [response]
  (log/trace "HTTP health check response:" response)
  {:status (:status response)
   :value (json/parse-string (:body response) true)})

(defn http
  ([service-key]
    (http service-key http-default-opts))
  ([service-key opts]
    (http service-key (http-health-resource service-key) opts))
  ([_service-key health-check-url opts]
    (parse-response
      (httpc/get health-check-url opts))))
