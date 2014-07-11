(ns cmr.transmit.config
  "Contains functions for retrieving application connection information from environment variables"
  (:require [cmr.common.config :as cfg]
            [cmr.transmit.connection :as conn]
            [camel-snake-kebab :as csk]))

(defn parse-port
  "Parses a port into a long."
  [s]
  (Long. s))

(def metadata-db-port (cfg/config-value-fn :metadata-db-port 3001 parse-port))

(def ingest-port (cfg/config-value-fn :ingest-port 3002 parse-port))

(def search-port (cfg/config-value-fn :search-port 3003 parse-port))

(def indexer-port (cfg/config-value-fn :indexer-port 3004 parse-port))

(def index-set-port (cfg/config-value-fn :index-set-port 3005 parse-port))

(def bootstrap-port (cfg/config-value-fn :bootstrap-port 3006 parse-port))


(defn app-conn-info
  "Returns the current application connection information as a map by application name"
  []
  {:metadata-db {:host (cfg/config-value :metadata-db-host "localhost")
                 :port (metadata-db-port)}
   :ingest {:host (cfg/config-value :ingest-host "localhost")
            :port (ingest-port)}
   :search {:host (cfg/config-value :search-host "localhost")
            :port (search-port)}
   :indexer {:host (cfg/config-value :indexer-host "localhost")
             :port (indexer-port)}
   :index-set {:host (cfg/config-value :index-set-host "localhost")
               :port (index-set-port)}
   :bootstrap {:host (cfg/config-value :bootstrap-host "localhost")
               :port (bootstrap-port)}})

(defn app-connection-system-key-name
  "The name of the app connection in the system"
  [app-name]
  (keyword (str (csk/->kebab-case-string app-name) "-connection")))

(defn context->app-connection
  "Retrieves the connection from the context for the given app."
  [context app-name]
  (get-in context [:system (app-connection-system-key-name app-name)]))

(defn system-with-connections
  "Adds connection keys to the system for the given applications. They will be added in a way
  that can be retrieved with the context->app-connection function."
  [system app-names]
  (let [conn-info (app-conn-info)]
    (reduce (fn [sys app-name]
              (let [{:keys [host port]} (conn-info app-name)]
                (assoc sys
                       (app-connection-system-key-name app-name)
                       (conn/create-app-connection host port))))
            system
            app-names)))


