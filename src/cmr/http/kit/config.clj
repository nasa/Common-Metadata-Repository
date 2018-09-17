(ns cmr.http.kit.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io])
  (:import
   (clojure.lang Keyword)))

(def config-file "config/cmr-http-kit/config.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (with-open [rdr (io/reader (io/resource filename))]
      (edn/read (new java.io.PushbackReader rdr)))))
