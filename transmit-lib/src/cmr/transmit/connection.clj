(ns cmr.transmit.connection
  "Contains functions that allow creating application connections. We'll eventually use this for
  implementing CMR-538"
  (:require
   [camel-snake-kebab.core :as csk]
   [clj-http.conn-mgr :as conn-mgr]
   [cmr.common.api.web-server :as web-server]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer [debug info error]]))

(defn create-app-connection
  "Creates a 'connection' to an application"
  [conn-info]
  ;; pooling connection manager for persistent http connections.
  (assoc conn-info
         :conn-mgr (conn-mgr/make-reusable-conn-manager
                     {;; Maximum number of threads that will be used for connecting.
                      ;; Very important that this matches the maximum number of threads that will be running
                      :threads (web-server/MAX_THREADS)
                      ;; Maximum number of simultaneous connections per host
                      :default-per-route (web-server/MAX_THREADS)
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


(defconfig default-connection-reset-retries
  "Sets the default number of retries on connection reset"
  {:default 1
   :type Long})

(defconfig default-connection-reset-pause-before-reset
  "Sets the default number of milliseconds to wait before retrying on connection reset."
  {:default 500
   :type Long})

(defmacro handle-socket-exception-retries
  "Handles a SocketException by pausing and then retrying a certain number of times. Can optionally
   be passed a map of options as the first argument. Options are:
   * :num-retries - The number of times to retry before failing.
   * :pause-ms - number of milliseconds to pause before retrying."
  [& args]
  (let [[options body] (if (map? (first args))
                         [(first args) (rest args)]
                         [nil args])
        num-retries (get options :num-retries (default-connection-reset-retries))
        pause-ms (get options :pause-ms (default-connection-reset-pause-before-reset))]
    `(loop [num-tries-left# ~num-retries]
       (let [retry-flag# (atom false)
             result# (try
                       ~@body
                       (catch java.net.SocketException se#
                         (if (> num-tries-left# 0)
                           (do
                             (error se# (format "A Socket Exception error occurred. Retrying after pausing %s ms" ~pause-ms))
                             (reset! retry-flag# true))
                           (throw se#))))]
         (if @retry-flag#
           (do
             (Thread/sleep ~pause-ms)
             (recur (dec num-tries-left#)))
           result#)))))
