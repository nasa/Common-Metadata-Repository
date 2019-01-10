(ns cmr.opendap.core
  (:require
   [clojusc.twig :as logger]
   [cmr.opendap.components.core :as components]
   [com.stuartsierra.component :as component]
   [trifl.java :as trifl])
  (:gen-class))

(logger/set-level! '[cmr.opendap] :info logger/no-color-log-formatter)

(defn -main
  [& args]
  (let [system (components/init)]
    (component/start system)
    (trifl/add-shutdown-handler #(component/stop system))))
