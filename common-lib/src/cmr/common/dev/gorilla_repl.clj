(ns cmr.common.dev.gorilla-repl
  "Wraps gorilla repl as a component so it will work without our typical refresh cycle.
  See http://gorilla-repl.org/"
  (:require [gorilla-repl.core :as gc]
            [cmr.common.lifecycle :as lifecycle]
            [org.httpkit.server :as server]
            [gorilla-repl.nrepl :as nrepl]
            [clojure.tools.nrepl.server :as clj-nrepl]))


(defrecord GorillaRepl
  [
   ;; The port Jetty will be running on
   port

   ;; Gorilla REPL server function that will stop it.
   server-stop-fn
   ]

  lifecycle/Lifecycle

  (start
    [this system]
    ;; Code based on gorilla-repl.core.

    ;; get configuration information from parameters
    (let [project "no project"
          ;; We could include a keymap in the future
          ; keymap (or (:keymap (:gorilla-options conf)) {})
          keymap {}
          ;; Use this ff we want to exclude scanning certain directories
          ; _ (swap! gc/excludes (fn [x] (set/union x (:load-scan-exclude (:gorilla-options conf)))))
          ]
      ;; build config information for client
      (gc/set-config :project project)
      (gc/set-config :keymap keymap)
      ;; first startup nREPL.  0 indicates it should pick a port.
      (nrepl/start-and-connect 0)
      ;; and then the webserver
      (let [s (server/run-server #'gc/app-routes {:port port :join? false :ip "127.0.0.1" :max-body 500000000})
            webapp-port (:local-port (meta s))]
        (println (str "Gorilla REPL Running at http://127.0.0.1:" webapp-port "/worksheet.html ."))

        (assoc this :server-stop-fn s))))

  (stop
    [this system]
    (when server-stop-fn
      ;; We have to reach into gorilla repl to find the nrepl instance to stop it.
      (clj-nrepl/stop-server (deref nrepl/nrepl))
      ;; Stop the HTTP kit server
      (server-stop-fn)
      (assoc this :server-stop-fn nil))))


(defn create-gorilla-repl-server
  "Creates an instance of the Gorilla REPL server."
  [port]
  (map->GorillaRepl {:port port}))


