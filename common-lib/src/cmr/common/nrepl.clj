(ns cmr.common.nrepl
  "Common nREPL component for CMR apps."
  (:require
   [clojure.tools.nrepl.server :as nrepl]
   [cmr.common.config :as config]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer [info]]))

(defrecord nREPLComponent [port server]
  lifecycle/Lifecycle
  (start [component system]
    (let [server (nrepl/start-server :port port)
          ;; if the component was specified with a port of 0, a port
          ;; will be automatically chosen by the nREPL
          new-port (:port server)]
      (info "Started nREPL on port" new-port)
      (->nREPLComponent new-port server)))
  (stop [component system]
    (when server
      (nrepl/stop-server server)
      (info "Stopped nREPL on port" port))
    (->nREPLComponent port nil)))

(defn create-nrepl
  "Returns a new nREPL lifecycle component configured to listen on the
  given port. The component must be started before it will accept
  connections."
  [port]
  {:pre [(number? port)]}
  (->nREPLComponent port nil))

(defn create-nrepl-if-configured
  "Returns a new nREPL if port is specified, otherwise nil."
  [port]
  (when port
    (create-nrepl port)))
