(ns cmr.nlp.config
  (:require
   [cmr.exchange.common.file :as file]))

(def config-file "config/cmr-nlp/config.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (file/read-edn-resource filename)))

