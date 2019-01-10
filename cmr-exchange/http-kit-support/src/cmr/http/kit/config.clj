(ns cmr.http.kit.config
  (:require
   [cmr.exchange.common.file :as file]))

(def config-file "config/cmr-http-kit/config.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (file/read-edn-resource filename)))
