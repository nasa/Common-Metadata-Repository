(ns cmr.transmit.connection
  "Contains functions that allow creating application connections. We'll eventually use this for
  implementing CMR-538"
  (:require [camel-snake-kebab :as csk]
            [cmr.common.api.web-server :as web-server]
            [clj-http.conn-mgr :as conn-mgr]))

(defn create-app-connection
  "Creates a 'connection' to an application"
  [conn-info]
  ;; pooling connection manager for persistent http connections.
  (assoc conn-info
         :conn-mgr (conn-mgr/make-reusable-conn-manager
                     {;; Maximum number of threads that will be used for connecting.
                      ;; Very important that this matches the maximum number of threads that will be running
                      :threads web-server/MAX_THREADS
                      ;; Maximum number of simultaneous connections per host
                      :default-per-route 10
                      ;; This is the length of time in _seconds_ that a connection will
                      ;; be left open for reuse. The default is 5 seconds which is way
                      ;; too short.
                      :timeout 120})))

(defn root-url
  "Returns the root url for a connection"
  [connection]
  (let [{:keys [protocol host port context]} connection]
    (format "%s://%s:%s%s" protocol host port context)))

(defn conn-mgr
  "Returns the connection manager from a connection"
  [connection]
  (:conn-mgr connection))