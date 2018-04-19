(ns hxgm30.event.config
  (:require
   [hxgm30.common.file :as common]))

(def config-file "hexagram30-config/event.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (common/read-edn-resource filename)))
