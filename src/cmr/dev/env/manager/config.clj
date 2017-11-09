(ns cmr.dev.env.manager.config
  (:require
    [clojure.string :as string]
    [cmr.dev.env.manager.components.core :as components]
    [cmr.dev.env.manager.util :as util]
    [leiningen.core.project :as project]
    [taoensso.timbre :as log]
    [trifl.fs :as fs]))

(def config-key :dem)
(def default-config
  {config-key {
    :logging {
      :level :info
      :nss '[cmr]}}})

(defn build
  ""
  []
  (util/deep-merge
   default-config
   (config-key (project/read))))

(defn logging
  [system]
  (components/get-config system config-key :logging))

(defn log-level
  [system]
  (components/get-config system config-key :logging :level))

(defn log-nss
  [system]
  (components/get-config system config-key :logging :nss))
