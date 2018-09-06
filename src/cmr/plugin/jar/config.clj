(ns cmr.plugin.jar.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io])
  (:import
   (java.io PushbackReader)))

(def config-file "config/cmr-jar-plugin/config.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (with-open [rdr (io/reader (io/resource filename))]
      (edn/read (new PushbackReader rdr)))))
