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
;; Defines a root path that will appear on all requests sent to this application. For example if
;; the relative-root-url is '/cmr-app' and the path for a URL is '/foo' then the full url would be
;; http://host:port/cmr-app/foo. This should be set when this application is deployed in an
;; environment where it is accessed through a VIP.
(def metadata-db-relative-root-url (cfg/config-value-fn :metadata-db-relative-root-url ""))

(def ingest-port (cfg/config-value-fn :ingest-port 3002 parse-port))
(def ingest-relative-root-url (cfg/config-value-fn :ingest-relative-root-url ""))

(def search-port (cfg/config-value-fn :search-port 3003 parse-port))
(def search-relative-root-url (cfg/config-value-fn :search-relative-root-url ""))

(def indexer-port (cfg/config-value-fn :indexer-port 3004 parse-port))
(def indexer-relative-root-url (cfg/config-value-fn :indexer-relative-root-url ""))

(def index-set-port (cfg/config-value-fn :index-set-port 3005 parse-port))
(def index-set-relative-root-url (cfg/config-value-fn :index-set-relative-root-url ""))

(def bootstrap-port (cfg/config-value-fn :bootstrap-port 3006 parse-port))
(def bootstrap-relative-root-url (cfg/config-value-fn :bootstrap-relative-root-url ""))

(def echo-rest-port (cfg/config-value-fn :echo-rest-port 3008 parse-port))

(def echo-system-token (cfg/config-value-fn :echo-system-token "mock-echo-system-token"))

(def default-conn-info
  "The default values for connections."
  {:protocol "http"
   :context ""})

(defn app-conn-info
  "Returns the current application connection information as a map by application name"
  []
  {:metadata-db {:host (cfg/config-value :metadata-db-host "localhost")
                 :port (metadata-db-port)
                 :context (metadata-db-relative-root-url)}
   :ingest {:host (cfg/config-value :ingest-host "localhost")
            :port (ingest-port)
            :context (ingest-relative-root-url)}
   :search {:host (cfg/config-value :search-host "localhost")
            :port (search-port)
            :context (search-relative-root-url)}
   :indexer {:host (cfg/config-value :indexer-host "localhost")
             :port (indexer-port)
             :context (indexer-relative-root-url)}
   :index-set {:host (cfg/config-value :index-set-host "localhost")
               :port (index-set-port)
               :context (index-set-relative-root-url)}
   :bootstrap {:host (cfg/config-value :bootstrap-host "localhost")
               :port (bootstrap-port)
               :context (bootstrap-relative-root-url)}
   :echo-rest {:protocol (cfg/config-value :echo-rest-protocol "http")
               :host (cfg/config-value :echo-rest-host "localhost")
               :port (echo-rest-port)
               :context (cfg/config-value :echo-rest-context "")}})

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
  (let [conn-info-map (app-conn-info)]
    (reduce (fn [sys app-name]
              (let [conn-info (merge default-conn-info (conn-info-map app-name))]
                (assoc sys
                       (app-connection-system-key-name app-name)
                       (conn/create-app-connection conn-info))))
            system
            app-names)))


