(ns cmr.graph.components.neo4j
  (:require
   [clojurewerkz.neocons.rest :as nr]
   [cmr.graph.components.config :as config]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]))

(def neo4j-db-endpoint "http://localhost:7474/db/data/")
(def max-connection-attempts 20)
(def connection-attempt-delay 3000) ; in milliseconds

(defn attempt-connection
  ([]
    (attempt-connection max-connection-attempts 1))
  ([max-tries current-try]
    (if (= (inc max-tries) current-try)
      (log/error "Maximum tries exceeded.")
      (try
        (nr/connect neo4j-db-endpoint)
        (log/debugf "Connection %s succeeded!" current-try)
        (catch Exception e
          (log/warnf "Connection %s failed; trying again ..." current-try)
          (Thread/sleep connection-attempt-delay)
          (attempt-connection max-tries (inc current-try)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-conn
  [system]
  (get-in system [:neo4j :conn]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Neo4j [conn])

(defn start
  [this]
  (log/info "Starting Neo4j component ...")
  (attempt-connection)
  (let [conn (nr/connect (format "http://%s:%s%s"
                                 (config/neo4j-host this)
                                 (config/neo4j-port this)
                                 (config/neo4j-db-path this)))]
    (log/debug "Started Neo4j component.")
    (assoc this :conn conn)))

(defn stop
  [this]
  (log/info "Stopping Neo4j component ...")
  (log/debug "Stopped Neo4j component.")
  (assoc this :conn nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Neo4j
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->Neo4j {}))
