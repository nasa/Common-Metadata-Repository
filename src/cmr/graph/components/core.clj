(ns cmr.graph.components.core
  (:require
    [cmr.graph.components.config :as config]
    [cmr.graph.components.elastic :as elastic]
    [cmr.graph.components.httpd :as httpd]
    [cmr.graph.components.logging :as logging]
    [cmr.graph.components.neo4j :as neo4j]
    [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Common Configuration Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cfg
  {:config (config/create-component)})

(def log
  {:logging (component/using
             (logging/create-component)
             [:config])})

(def neo4j
  {:neo4j (component/using
           (neo4j/create-component)
           [:config :logging])})

(def elastic
  {:elastic (component/using
             (elastic/create-component)
             [:config :logging])})

(def httpd
  {:httpd (component/using
           (httpd/create-component)
           [:config :logging :neo4j :elastic])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Initializations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize-bare-bones
  []
  (component/map->SystemMap
    (merge cfg
           log
           neo4j)))

(defn initialize-with-web
  []
  (component/map->SystemMap
    (merge cfg
           log
           neo4j
           elastic
           httpd)))

(def init-lookup
  {:basic #'initialize-bare-bones
   :web #'initialize-with-web})

(defn init
  ([]
    (init :web))
  ([mode]
    ((mode init-lookup))))
