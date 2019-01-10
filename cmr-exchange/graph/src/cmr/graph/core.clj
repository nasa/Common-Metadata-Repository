(ns cmr.graph.core
  (:require
   [clojusc.twig :as logger]
   [cmr.graph.components.core :as componemts]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]
   [trifl.java :as java])
  (:gen-class))

(def startup-message
  (str "Component startup complete."
       \newline \newline
       "The CMR Graph system is now ready to use. "
       "To get started,"
       \newline
       "you can visit the following:"
       \newline
       "* Neo4j: http://localhost:7474/browser/"
       \newline
       "* Kibana: http://localhost:5601/"
       \newline \newline
       "Additionally, the CMR Graph REST API is available at"
       \newline
       "http://localhost:3012. To try it out, call the following:"
       \newline
       "* curl --silent \"http://localhost:3012/ping\""
       \newline
       "* curl --silent \"http://localhost:3012/health\""
       \newline))

(defn shutdown
  [system]
  (component/stop system))

(defn -main
  [& args]
  (logger/set-level! ['cmr.graph] :info)
  (log/info "Starting the CMR Graph components ...")
  (let [system (componemts/init)]
    (component/start system)
    (java/add-shutdown-handler #(shutdown system)))
  (log/info startup-message))
