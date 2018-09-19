(ns cmr.plugin.jar.config
  (:require
   [cmr.exchange.common.file :as file])
  (:import
   (java.io PushbackReader)))

(def config-file "config/cmr-jar-plugin/config.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (file/read-edn-resource filename)))
