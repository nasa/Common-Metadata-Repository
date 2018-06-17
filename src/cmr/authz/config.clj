(ns cmr.authz.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io])
  (:import
    (clojure.lang Keyword)))

(def config-file "config/cmr-authz/config.edn")

(defn cfg-data
  ([]
    (cfg-data config-file))
  ([filename]
    (with-open [rdr (io/reader (io/resource filename))]
      (edn/read (new java.io.PushbackReader rdr)))))

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
