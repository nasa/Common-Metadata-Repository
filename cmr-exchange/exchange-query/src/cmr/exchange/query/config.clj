(ns cmr.exchange.query.config
  (:require
   [cmr.exchange.common.file :as file]))

(def config-file "config/cmr-exchange-query/config.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (file/read-edn-resource filename)))

