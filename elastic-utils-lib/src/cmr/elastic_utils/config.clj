(ns cmr.elastic-utils.config
  "Contains configuration functions for communicating with elastic search"
  (:require [cmr.common.config :as config]
            [clojure.data.codec.base64 :as b64]))

(def elastic-host
  (config/config-value-fn :elastic-host "localhost"))

(def elastic-port
  (config/config-value-fn :elastic-port 9210 #(Long. %)))

(def elastic-admin-token
  (config/config-value-fn :elastic-admin-token
                          "echo-elasticsearch"
                         #(str "Basic " (b64/encode (.getBytes %)))))

(defn elastic-config
  "Returns the elastic config as a map"
  []
  {:host (elastic-host)
   :port (elastic-port)
   :admin-token (elastic-admin-token)})