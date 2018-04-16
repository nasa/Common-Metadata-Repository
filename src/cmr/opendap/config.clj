(ns cmr.opendap.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [environ.core :as environ]))

(def config-file "config/cmr-opendap/config.edn")

(defn cfg-data
  ([]
    (cfg-data config-file))
  ([filename]
    (with-open [rdr (io/reader (io/resource filename))]
      (edn/read (new java.io.PushbackReader rdr)))))

(defn cmr-only
  [[k v]]
  (let [key-name (name k)]
    (when (string/starts-with? key-name "cmr-")
      [(mapv keyword (string/split key-name #"-"))
       (try
        (Integer/parseInt v)
        (catch Exception _e
          v))])))

(defn nest-vars
  [acc [ks v]]
  (assoc-in acc ks v))

(defn env-props-data
  []
  (->> (#'environ/read-system-props)
       (merge (#'environ/read-system-env))
       (map cmr-only)
       (remove nil?)
       (reduce nest-vars {})))

(defn data
  []
  (merge (cfg-data)
         (env-props-data)))
