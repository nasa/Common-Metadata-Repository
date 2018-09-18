(ns cmr.mission-control.config
  (:require
   [cmr.exchange.common.file :as file]))

(def config-file "config/cmr-mission-control/config.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (file/read-edn-resource filename)))
