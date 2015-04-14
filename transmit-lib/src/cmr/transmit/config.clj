(ns cmr.transmit.config
  "Contains functions for retrieving application connection information from environment variables"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.transmit.connection :as conn]
            [camel-snake-kebab.core :as csk]))

(def token-header
  "echo-token")

(defmacro def-app-conn-config
  "Defines three configuration entries for an application for the host, port and relative root URL"
  [app-name port]
  (let [host-config (symbol (str (name app-name) "-host"))
        port-config (symbol (str (name app-name) "-port"))
        relative-root-url-config (symbol (str (name app-name) "-relative-root-url"))]
    `(do
       (defconfig ~host-config
         ~(str "The host name to use for connections to the " (name app-name) " application.")
         {:default "localhost"})

       (defconfig ~port-config
         ~(str "The port number to use for connections to the " (name app-name) " application.")
         {:default ~port :type Long})

       (defconfig ~relative-root-url-config
         ~(str "Defines a root path that will appear on all requests sent to this application. For "
               "example if the relative-root-url is '/cmr-app' and the path for a URL is '/foo' then "
               "the full url would be http://host:port/cmr-app/foo. This should be set when this "
               "application is deployed in an environment where it is accessed through a VIP.")
         {:default ""}))))

(def-app-conn-config metadata-db 3001)
(def-app-conn-config ingest 3002)
(def-app-conn-config search 3003)
(def-app-conn-config indexer 3004)
(def-app-conn-config index-set 3005)
(def-app-conn-config bootstrap 3006)
(def-app-conn-config cubby 3007)

(defconfig echo-rest-protocol
  "The protocol to use when contructing ECHO Rest URLs."
  {:default "http"})

(defconfig echo-rest-host
  "The host name to use for connections to ECHO Rest."
  {:default "localhost"})

(defconfig echo-rest-port
  "The port to use for connections to ECHO Rest"
  {:default 3008 :type Long})

(defconfig echo-rest-context
  "The root context for connections to ECHO Rest."
  {:default ""})

(defconfig echo-system-token
  "The ECHO system token to use for request to ECHO."
  {:default "mock-echo-system-token"})

(def default-conn-info
  "The default values for connections."
  {:protocol "http"
   :context ""})

(defn app-conn-info
  "Returns the current application connection information as a map by application name"
  []
  {:metadata-db {:host (metadata-db-host)
                 :port (metadata-db-port)
                 :context (metadata-db-relative-root-url)}
   :ingest {:host (ingest-host)
            :port (ingest-port)
            :context (ingest-relative-root-url)}
   :search {:host (search-host)
            :port (search-port)
            :context (search-relative-root-url)}
   :indexer {:host (indexer-host)
             :port (indexer-port)
             :context (indexer-relative-root-url)}
   :index-set {:host (index-set-host)
               :port (index-set-port)
               :context (index-set-relative-root-url)}
   :bootstrap {:host (bootstrap-host)
               :port (bootstrap-port)
               :context (bootstrap-relative-root-url)}
   :cubby {:host (cubby-host)
           :port (cubby-port)
           :context (cubby-relative-root-url)}
   :echo-rest {:protocol (echo-rest-protocol)
               :host (echo-rest-host)
               :port (echo-rest-port)
               :context (echo-rest-context)}})

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


