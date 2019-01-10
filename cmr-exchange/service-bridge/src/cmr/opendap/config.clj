(ns cmr.opendap.config
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.file :as file]
   [cmr.exchange.common.util :as util]
   [cmr.metadata.proxy.config :as config]
   [environ.core :as environ]
   [taoensso.timbre :as log])
  (:import
    (clojure.lang Keyword)))

(def config-file "config/cmr-opendap/config.edn")

(defn base-data
  ([]
    (base-data config-file))
  ([filename]
    (file/read-edn-resource filename)))

(defn data
  []
  (util/deep-merge (base-data)
                   (config/props-data)
                   (config/env-data)))
