(ns cmr.elastic-utils.config
  "Contains configuration functions for communicating with elastic search"
  (:require
   [clojure.data.codec.base64 :as b64]
   [cmr.common.log :as log :refer [info warn error]]
   [cmr.common.config :as config :refer [defconfig]]))

(declare elastic-name)
(def elastic-name
  "elastic")

(declare gran-elastic-name)
(def gran-elastic-name
  "gran-elastic")

(declare es-unlimited-page-size)
(defconfig es-unlimited-page-size
  "This is the number of items we will request from elastic search at a time when
  the page size is set to unlimited."
  {:default 10000
   :type Long})

(declare es-max-unlimited-hits)
(defconfig es-max-unlimited-hits
  "Sets an upper limit in order to get all results from elastic search
  without paging. This is used by CMR applications to load data into their
  caches."
  {:default 200000
   :type Long})
;; TODO CMR-10636 Need to add these env vars to AWS parameter store because the names were changed, for EVERY DEPLOYMENT ENV
(declare gran-elastic-host)
(defconfig gran-elastic-host
           "Elastic host or VIP for granule ES cluster."
           {:default "localhost"})
;; TODO CMR-10636 Need to add these env vars to AWS parameter store because the names were changed, for EVERY DEPLOYMENT ENV
(declare elastic-host)
(defconfig elastic-host
           "Elastic host for non-granule ES cluster"
           {:default "localhost"})

;; TODO CMR-10636 Need to add these env vars to AWS parameter store because the names were changed, for EVERY DEPLOYMENT ENV
(declare gran-elastic-port)
(defconfig gran-elastic-port
           "Port elastic is listening on."
           {:default 9210 :type Long})
;; TODO CMR-10636 Need to add these env vars to AWS parameter store because the names were changed, for EVERY DEPLOYMENT ENV. The old ENV VAR was called elastic-port, which represented the original cluster, now it's going to represent the new cluster, so BE CAREFUL
(declare elastic-port)
(defconfig elastic-port
           "Port elastic non-granule is listening on."
           {:default 9211 :type Long})

(declare elastic-admin-token)
(defconfig elastic-admin-token
    "Token used for basic auth authentication with elastic."
    {:default (str "Basic " (b64/encode (.getBytes "echo-elasticsearch")))})

(declare elastic-scroll-timeout)
(defconfig elastic-scroll-timeout
  "Timeout for ES scrolling"
  {:default "5m"})

(declare elastic-query-timeout)
(defconfig elastic-query-timeout
  "Timeout for ES queries"
  {:default "170s"})

(declare elastic-unknown-host-retries)
(defconfig elastic-unknown-host-retries
  "Number of times to retry on ES unknown host error"
  {:default 3 :type Long})

(declare elastic-unknown-host-retry-interval-ms)
(defconfig elastic-unknown-host-retry-interval-ms
  "Number of milliseconds to retry an ES unknown host error"
  {:default 500 :type Long})

(declare elastic-scroll-search-type)
(defconfig elastic-scroll-search-type
  "Search type to use with scrolling - either 'scan' or 'query_then_fetch'"
  {:default "query_then_fetch"})

;; Note: this config is common in multiple packages as they all need to have this value but we do
;; not want to require these apps to depend on each other just to get the defconf. If you rename,
;; do so in all three locations.
;; indexer-app/src/cmr/indexer/data/index_set.clj
;; search-app/src/cmr/search/data/elastic_search_index.clj
(declare collections-index-alias)
(defconfig collections-index-alias
  "The alias to use for the collections index."
  {:default "collection_search_alias" :type String})

(declare numeric-range-execution-mode)
(defconfig numeric-range-execution-mode
  "Defines the execution mode to use for numeric ranges in an elasticsearch search"
  {:default "fielddata"})

(declare numeric-range-use-cache)
(defconfig numeric-range-use-cache
  "Indicates whether the numeric range should use the field data cache or not."
  {:type Boolean
   :default false})

(defn gran-elastic-config
  "Returns the elastic config as a map"
  []
  {:host (gran-elastic-host)
   :port (gran-elastic-port)
   ;; This can be set to specify an Apached HTTP retry handler function to use. The arguments of the
   ;; function is that as specified in clj-http's documentation. It returns true or false of whether
   ;; to retry again
   :retry-handler nil
   :admin-token (elastic-admin-token)})

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

(defn es-cluster-name-str->keyword
  [es-cluster-name]
  (let [es-cluster-name-keyword (if (keyword? es-cluster-name)
                                  es-cluster-name
                                  (keyword es-cluster-name))]
    (if (or (= es-cluster-name-keyword (keyword cmr.elastic-utils.config/gran-elastic-name))
            (= es-cluster-name-keyword (keyword cmr.elastic-utils.config/elastic-name)))
      es-cluster-name-keyword
      (throw (Exception. (str "Expected es-cluster-name to be " cmr.elastic-utils.config/gran-elastic-name " or " cmr.elastic-utils.config/elastic-name ", but got value of " es-cluster-name " instead."))))))