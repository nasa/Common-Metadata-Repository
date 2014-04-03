(ns cmr.index-set.config.elasticsearch-config
  (:require [cheshire.core :as cheshire]
            [clojure.data.codec.base64 :as b64]
            [clojure.java.io :as io]))

(def config-file
  (io/resource "config/elasticsearch_config.json"))

(defn config
  "Return the configuration for elasticsearch"
  []
  (let [{:strs [host port password]} (cheshire/decode (slurp config-file))]
    {:host host
     :port port
     :admin-token (str "Basic " (b64/encode (.getBytes password)))}))
