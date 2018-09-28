(ns cmr.opendap.config
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.file :as file]
   [cmr.opendap.util :as util]
   [environ.core :as environ]
   [taoensso.timbre :as log])
  (:import
    (clojure.lang Keyword)))

(def config-file "config/cmr-opendap/config.edn")

(defn cfg-data
  ([]
    (cfg-data config-file))
  ([filename]
    (file/read-edn-resource filename)))

(defn parse-kv
  [k v splitter-regex]
  [(mapv keyword (string/split k splitter-regex))
   (try
    (Integer/parseInt v)
    (catch Exception _e
      v))])

(defn normalize-env
  [[k v]]
  (let [key-name (name k)]
    (when
      (or
        (string/starts-with? key-name "cmr-")
        (string/starts-with? key-name "httpd-")
        (string/starts-with? key-name "logging-"))
      (parse-kv key-name v #"-"))))

(defn normalize-prop
  [[k v]]
  (let [key-name (name k)]
    (when-not
      (or
        (string/starts-with? key-name "java")
        (string/ends-with? key-name "class-path"))
      (parse-kv (name k) v #"-"))))

(defn nest-vars
  [acc [ks v]]
  (try
    (assoc-in acc ks v)
    (catch Exception _e
      (log/warn (format (str "Had a problem adding config data key %s (of "
                             "type %s) and value %s (of type %s)")
                        ks
                        (type ks)
                        v (type v)))
      acc)))

(defn props-data
  []
  (->> (#'environ/read-system-props)
       (map normalize-prop)
       (remove nil?)
       (reduce nest-vars {})
       ((fn [x] (log/trace "props-data:" x) x))))

(defn env-data
  []
  (->> (#'environ/read-system-env)
       (map normalize-env)
       (remove nil?)
       (reduce nest-vars {})
       ((fn [x] (log/trace "env-data:" x) x))))

(defn data
  []
  (util/deep-merge (cfg-data)
                   (props-data)
                   (env-data)))

(defn service-keys
  "We need to special-case two-word services, as split by the environment and
  system property parser above.

  Note: this function originally had more in it, but was moved into cmr.authz."
  [^Keyword service]
  [service])

(defn service->base-url
  [service]
  (format "%s://%s:%s"
          (or (:protocol service) "https")
          (:host service)
          (or (:port service) "443")))

(defn service->url
  [service]
  (format "%s%s"
          (service->base-url service)
          (or (get-in service [:relative :root :url])
              (:context service)
              "/")))

(defn service->base-public-url
  [service]
  (let [protocol (or (get-in service [:public :protocol]) "https")
        host (get-in service [:public :host])]
    (if (= "https" protocol)
      (format "%s://%s" protocol host)
      (format "%s://%s:%s" protocol host (get-in service [:public :port])))))

(defn service->public-url
  [service]
  (format "%s%s"
          (service->base-public-url service)
          (or (get-in service [:relative :root :url])
              (:context service)
              "/")))
