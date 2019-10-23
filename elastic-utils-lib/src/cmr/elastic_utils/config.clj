(ns cmr.elastic-utils.config
  "Contains configuration functions for communicating with elastic search"
  (:require
   [clojure.data.codec.base64 :as b64]
   [cmr.common.config :as config :refer [defconfig]]))

(defconfig elastic-host
  "Elastic host or VIP."
  {:default "localhost"})

(defconfig elastic-port
  "Port elastic is listening on."
  {:default 9210 :type Long})

(defconfig elastic-admin-token
    "Token used for basic auth authentication with elastic."
    {:default (str "Basic " (b64/encode (.getBytes "echo-elasticsearch")))})

(defconfig elastic-scroll-timeout
  "Timeout for ES scrolling"
  {:default "5m"})

(defconfig elastic-query-timeout
  "Timeout for ES queries"
  {:default "170s"})

(defconfig elastic-unknown-host-retries
  "Number of times to retry on ES unknown host error"
  {:default 3 :type Long})

(defconfig elastic-unknown-host-retry-interval-ms
  "Number of milliseconds to retry an ES unknown host error"
  {:default 500 :type Long})

(defconfig elastic-scroll-search-type
  "Search type to use with scrolling - either 'scan' or 'query_then_fetch'"
  {:default "query_then_fetch"})

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
