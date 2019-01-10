(ns cmr.dev.env.manager.repl
  (:require
   [clj-http.client :as httpc]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.twig :as logger]
   [cmr.dev.env.manager.components.checks.health :as health-check]
   [cmr.dev.env.manager.components.config :as config]
   [cmr.dev.env.manager.components.messaging :as messaging]
   [cmr.dev.env.manager.components.system :as components]
   [cmr.dev.env.manager.health :as health]
   [cmr.dev.env.manager.repl.transitions :as transitions]
   [cmr.process.manager.components.common.docker :as docker]
   [cmr.process.manager.components.common.process :as process]
   [cmr.transmit.config :as transmit]
   [com.stuartsierra.component :as component]
   [me.raynes.conch.low-level :as shell]
   [taoensso.timbre :as log]
   [trifl.fs :as fs]
   [trifl.java :refer [add-shutdown-handler show-methods]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State & Transition Vars   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic state
  (atom {:system {
           :state :stopped
           :transition {
             :begin 0
             :end 0}}}))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-process
  [service-key]
  (process/get-process system service-key))

(defn get-process-cpu
  [service-key]
  (process/get-process-cpu system service-key))

(defn get-process-descendants
  [service-key]
  (process/get-process-descendants system service-key))

(defn get-process-id
  [service-key]
  (process/get-process-id system service-key))

(defn get-process-mem
  [service-key]
  (process/get-process-mem system service-key))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Messaging Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publish-message
  [topic content]
  (messaging/publish system topic content)
  :ok)

(defn subscribe-message
  [topic func]
  (messaging/subscribe system topic func)
  :ok)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Docker Service Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-docker-opts
  [service-key]
  (docker/get-opts system service-key))

(defn get-docker-container-id
  [service-key]
  (docker/get-container-id system service-key))

(defn get-docker-container-data
  [service-key]
  (docker/get-container-data system service-key))

(defn get-docker-container-state
  [service-key]
  (docker/get-container-state system service-key))

(defn get-docker-container-pid
  [service-key]
  (docker/get-container-pid system service-key))

(defn get-docker-container-cpu
  [service-key]
  (docker/get-container-cpu system service-key))

(defn get-docker-container-mem
  [service-key]
  (docker/get-container-mem system service-key))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Health checks   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn check-health
  [service-key]
  (health-check/get-summary (service-key system)))

(defn check-health-details
  [service-key]
  (health-check/get-status (service-key system)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State data getters/setters   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-state!
  ([new-state]
    (set-state! :system new-state))
  ([state-type new-state]
    (swap! state assoc-in [state-type :state] new-state)))

(defn get-state
  ([]
    (get-state :system))
  ([state-type]
    (get-in @state [state-type :state])))

(defn set-time!
  ([time-point]
    (set-time! :system time-point))
  ([state-type time-point]
    (swap! state assoc-in
                 [state-type :transition time-point]
                 (System/currentTimeMillis))))

(defn get-time
  ([]
    (get-time :system))
  ([state-type]
    (let [transition (get-in @state [state-type :transition])]
      (/ (- (:end transition) (:begin transition)) 1000.0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   System State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
        (set-state! :system :initialized)
        (get-state :system)))))

(defn deinit
  "Reset the system state to pre-initialization value (`nil`)."
  []
  (if (contains? transitions/invalid-deinit state)
    (log/error "System is not stopped; please stop before deinitializing.")
    (do
      (alter-var-root #'system (fn [_] nil))
      (set-state! :system :uninitialized)
      (get-state :system))))

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
        (set-state! :system :started)
        (get-state :system)))))

(defn stop
  []
  "Stop a started system."
  (if (contains? transitions/invalid-stop state)
    (log/warn "System already stopped.")
    (do
      (alter-var-root #'system
        (fn [s] (when s (component/stop s))))
      (set-state! :system :stopped)
      (get-state :system))))

(defn restart
  []
  "Restart a running system."
  (set-time! :system :begin)
  (stop)
  (start)
  (set-time! :system :end)
  (log/infof "Time taken to restart system: %s seconds" (get-time))
  (get-state :system))

(defn startup
  []
  "Initialize a system and start all of its components.

  This is essentially a convenience wrapper for `init` + `start`."
  (if (contains? transitions/invalid-run state)
    (log/warn "System is already running.")
    (do
      (set-time! :system :begin)
      (when (not (contains? transitions/invalid-init state))
        (init))
      (when (not (contains? transitions/invalid-start state))
        (start))
      (set-state! :system :running)
      (set-time! :system :end)
      (log/infof "Time taken to start up system: %s seconds" (get-time))
      (get-state :system))))

(defn shutdown
  []
  "Stop a running system and de-initialize it.

  This is essentially a convenience wrapper for `stop` + `deinit`."
  (if (contains? transitions/invalid-shutdown state)
    (log/warn "System is already shutdown.")
    (do
      (set-time! :system :begin)
      (when (not (contains? transitions/invalid-stop state))
        (stop))
      (when (not (contains? transitions/invalid-deinit state))
        (deinit))
      (set-state! :system :shutdown)
      (set-time! :system :end)
      (log/infof "Time taken to shutdown system: %s seconds" (get-time))
      (get-state :system))))

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

(add-shutdown-handler #'cmr.dev.env.manager.repl/shutdown)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Aliases   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:doc "This is an alias for `reset`."}
  reload #'reset)

(def ^{:doc (str "This is an alias for `startup`; supports usage from earliest "
                 "versions of this project.")}
  run #'startup)
