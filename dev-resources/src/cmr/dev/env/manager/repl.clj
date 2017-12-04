(ns cmr.dev.env.manager.repl
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.twig :as logger]
   [cmr.dev.env.manager.components.system :as components]
   [cmr.dev.env.manager.config :as config]
   [cmr.dev.env.manager.process.core :as process]
   [com.stuartsierra.component :as component]
   [me.raynes.conch.low-level :as shell]
   [taoensso.timbre :as log]
   [trifl.java :refer [show-methods]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State & Transition Vars   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic state (atom {:system :stopped}))
(def ^:dynamic system nil)
(def valid-stop-transitions #{:started :running})
(def invalid-init-transitions #{:initialized :started :running})
(def invalid-deinit-transitions #{:started :running})
(def invalid-start-transitions #{:started :running})
(def invalid-stop-transitions #{:stopped})
(def invalid-run-transitions #{:running})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The next line is used to set a default log-level before the logging
;; component has been started (which only happens when the system is started).
(logger/set-level! '[cmr] :info)

(defn set-log-level!
  [log-level]
  (logger/set-level! '[cmr] log-level))

(defn get-process
  [service-key]
  (get-in system [service-key :process-data]))

(defn get-pid
  [service-key]
  (process/get-pid (get-process service-key)))

(defn get-descendants
  [service-key]
  (process/get-descendants (get-process service-key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   System State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-state!
  [state-type new-state]
  (swap! state assoc state-type new-state))

(defn get-state
  [state-type]
  (state-type @state))

(defn init
  ([]
    (init :default))
  ([mode]
    (if (contains? invalid-init-transitions state)
      (log/warn "System has aready been initialized.")
      (do
        (alter-var-root #'system
          (constantly ((components/init mode))))
        (:system (set-state! :system :initialized))))))

(defn deinit
  []
  (if (contains? invalid-deinit-transitions state)
    (log/error "System is not stopped; please stop before deinitializing.")
    (do
      (alter-var-root #'system (fn [_] nil))
      (:system (set-state! :system :uninitialized)))))

(defn start
  ([]
    (start :default))
  ([mode]
    (when (nil? system)
      (init mode))
    (if (contains? invalid-start-transitions state)
      (log/warn "System has already been started.")
      (do
        (alter-var-root #'system component/start)
        (:system (set-state! :system :started))))))

(defn stop
  []
  (if (contains? invalid-stop-transitions state)
    (log/warn "System already stopped.")
    (do
      (alter-var-root #'system
        (fn [s] (when s (component/stop s))))
      (:system (set-state! :system :stopped)))))

(defn restart
  []
  (stop)
  (start))

(defn run
  []
  (if (contains? invalid-run-transitions state)
    (log/warn "System is already running.")
    (do
      (if (not (contains? invalid-init-transitions state))
        (init))
      (if (not (contains? invalid-start-transitions state))
        (start))
      (:system (set-state! :system :running)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Reloading Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -refresh
  ([]
    (repl/refresh))
  ([& args]
    (apply #'repl/refresh args)))

(defn refresh
  "This is essentially an alias for clojure.tools.namespace.repl/refresh."
  [& args]
  (if (contains? valid-stop-transitions state)
    (stop))
  (apply -refresh args))

(defn reset
  []
  (stop)
  (deinit)
  (refresh :after 'cmr.dev.env.manager.repl/run))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Aliases   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def reload #'reset)
