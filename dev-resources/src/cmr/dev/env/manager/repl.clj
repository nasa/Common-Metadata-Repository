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
   [cmr.dev.env.manager.repl.transitions :as transitions]
   [com.stuartsierra.component :as component]
   [me.raynes.conch.low-level :as shell]
   [taoensso.timbre :as log]
   [trifl.java :refer [show-methods]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State & Transition Vars   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic state (atom {:system :stopped}))
(def ^:dynamic system nil)


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

(defn get-process-id
  [service-key]
  (process/get-pid (get-process service-key)))

(defn get-process-descendants
  [service-key]
  (process/get-descendants (get-process service-key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   System State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-state!
  ([new-state]
    (set-state! :system new-state))
  ([state-type new-state]
    (swap! state assoc state-type new-state)))

(defn get-state
  ([]
    (get-state :system))
  ([state-type]
    (state-type @state)))

(defn init
  "Initialize the system in preparation for all-component start-up."
  ([]
    (init :default))
  ([mode]
    (if (contains? transitions/invalid-init state)
      (log/warn "System has aready been initialized.")
      (do
        (alter-var-root #'system
          (constantly ((components/init mode))))
        (:system (set-state! :system :initialized))))))

(defn deinit
  "Reset the system state to pre-initialization value (`nil`)."
  []
  (if (contains? transitions/invalid-deinit state)
    (log/error "System is not stopped; please stop before deinitializing.")
    (do
      (alter-var-root #'system (fn [_] nil))
      (:system (set-state! :system :uninitialized)))))

(defn start
  "Start an initialized system."
  ([]
    (start :default))
  ([mode]
    (when (nil? system)
      (init mode))
    (if (contains? transitions/invalid-start state)
      (log/warn "System has already been started.")
      (do
        (alter-var-root #'system component/start)
        (:system (set-state! :system :started))))))

(defn stop
  []
  "Stop a started system."
  (if (contains? transitions/invalid-stop state)
    (log/warn "System already stopped.")
    (do
      (alter-var-root #'system
        (fn [s] (when s (component/stop s))))
      (:system (set-state! :system :stopped)))))

(defn restart
  []
  "Restart a running system."
  (stop)
  (start))

(defn run
  []
  "Initialize a system and start all of its components.

  This is essentially a convenience wrapper for `init` + `start`."
  (if (contains? transitions/invalid-run state)
    (log/warn "System is already running.")
    (do
      (when (not (contains? transitions/invalid-init state))
        (init))
      (when (not (contains? transitions/invalid-start state))
        (start))
      (:system (set-state! :system :running)))))

(defn shutdown
  []
  "Stop a running system and de-initialize it.

  This is essentially a convenience wrapper for `stop` + `deinit`."
  (if (contains? transitions/invalid-shutdown state)
    (log/warn "System is already shutdown.")
    (do
      (when (not (contains? transitions/invalid-stop state))
        (stop))
      (when (not (contains? transitions/invalid-deinit state))
        (stop)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Reloading Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- -refresh
  ([]
    (repl/refresh))
  ([& args]
    (apply #'repl/refresh args)))

(defn refresh
  "This is essentially an alias for `clojure.tools.namespace.repl/refresh`,
  the main difference being that it stops the system first (it it's running).
  This helps to prevent orphaned services (processes) in the event of an error
  when reloading code."
  [& args]
  (if (contains? transitions/valid-stop state)
    (stop))
  (apply -refresh args))

(defn reset
  "Simiar to `refresh`, the `reset` function goes one more step in safeguaring
  against side effects: it does a complete `shutdown` on the system before
  attempting a code reload.

  Upon successful code reload, this function will attmpt to restart the
  system."
  []
  (shutdown)
  (refresh :after 'cmr.dev.env.manager.repl/run))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Aliases   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:doc "This is an alias for `reset`."}
  reload #'reset)
