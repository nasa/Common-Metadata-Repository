(ns cmr.opendap.core
  (:require
   [cmr.opendap.components.core :as components]
   [com.stuartsierra.component :as component]
   [trifl.java :as trifl])
  (:gen-class))

(defn -main
  [& args]
  (let [system (components/init)]
    (component/start system)
    (trifl/add-shutdown-handler #(component/stop system))))
