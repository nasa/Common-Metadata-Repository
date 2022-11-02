(ns cmr.process.manager.components.core
  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

(defn init
  ([]
    (init nil))
  ([_mode]
    (component/map->SystemMap {})))
