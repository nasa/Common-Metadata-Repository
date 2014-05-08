(ns cmr.index-set.config.elasticsearch-config
  (:require [cheshire.core :as cheshire]
            [clojure.data.codec.base64 :as b64]
            [clojure.java.io :as io]
            [cmr.common.config :as config]))

(def config-file
  (io/resource "config/elasticsearch_config.json"))

(def default-config
  (let [{:strs [host port password]} (cheshire/decode (slurp config-file))]
    {:host host
     :port port
     :admin-token (str "Basic " (b64/encode (.getBytes password)))}))

(def elastic-host (config/config-value-fn :elastic-host (:host default-config)))

(def elastic-port (config/config-value-fn :elastic-port (:port default-config)))

(def elastic-admin-token (config/config-value-fn :elastic-admin-token (:admin-token default-config)))

(defn config
  "Return the configuration for elasticsearch"
  []
  {:host (elastic-host)
   :port (elastic-port)
   :admin-token (elastic-admin-token)})

;; index name and config for storing index-set requests
;; index the request after creating all of the requested indices successfully
;; foot print of this index will remain small
(def idx-cfg-for-index-sets
  {:index-name "index-sets"
   :settings {"index" {"number_of_shards" 1
                       "number_of_replicas"  0
                       "refresh_interval" "30s"}}
   :mapping {"set" { "dynamic"  "strict"
                    "_source"  {"enabled" true}
                    "_all"     {"enabled" false}
                    "_id"      {"path" "index-set-id"}
                    :properties {:index-set-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                 :index-set-name {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                                 :index-set-name.lowercase {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                                 :index-set-request {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}})

