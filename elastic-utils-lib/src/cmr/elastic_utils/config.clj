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
   ;; This can be set to specify an Apached HTTP retry handler function to use. The arguments of the
   ;; function is that as specified in clj-http's documentation. It returns true or false of whether
   ;; to retry again
   :retry-handler nil
   :admin-token (elastic-admin-token)})