(ns cmr.versioning.config
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.file :as file]
   [taoensso.timbre :as log])
  (:import
    (clojure.lang Keyword)))

(def config-file "config/cmr-api-versioning/config.edn")

(defn data
  ([]
    (data config-file))
  ([filename]
    (file/read-edn-resource filename)))
