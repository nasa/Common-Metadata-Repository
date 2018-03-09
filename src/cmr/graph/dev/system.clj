(ns cmr.graph.dev.system
  "CMR Graph development namespace

  This namespace is particularly useful when doing active development on the
  CMR Graph application."
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.tools.namespace.repl :as repl]
    [clojusc.twig :as logger]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State & Transition Vars   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic state :stopped)
(def ^:dynamic system nil)
(def ^:dynamic system-ns "")
(def valid-stop-transitions #{:started :running})
(def invalid-init-transitions #{:initialized :started :running})
(def invalid-deinit-transitions #{:started :running})
(def invalid-start-transitions #{:started :running})
(def invalid-stop-transitions #{:stopped})
(def invalid-startup-transitions #{:running})
(def invalid-shutdown-transitions #{:uninitialized :shutdown})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(logger/set-level! ['cmr.graph] :info)

(defn resolve-by-name
  [an-ns a-fun]
  (resolve (symbol (str an-ns "/" a-fun))))

(defn call-by-name
  [an-ns a-fun & args]
  (apply (resolve-by-name an-ns a-fun) args))

(defn set-system-ns
  [an-ns]
  (alter-var-root #'system-ns (constantly an-ns)))

(defn set-system
  [value]
  (alter-var-root #'system (constantly value)))

(defn set-state
  [value]
  (alter-var-root #'state (constantly value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init
  ([]
    (init :default))
  ([mode]
    (if (contains? invalid-init-transitions state)
      (log/warn "System has aready been initialized.")
      (do
        (set-system (call-by-name system-ns "init"))
        (set-state :initialized)))
    state))

(defn deinit
  []
  (if (contains? invalid-deinit-transitions state)
    (log/error "System is not stopped; please stop before deinitializing.")
    (do
      (set-system nil)
      (set-state :uninitialized)))
  state)

(defn start
  ([]
    (start :default))
  ([mode]
    (when (nil? system)
      (init mode))
    (if (contains? invalid-start-transitions state)
      (log/warn "System has already been started.")
      (do
        (set-system (component/start system))
        (set-state :started)))
    state))

(defn stop
  []
  (if (contains? invalid-stop-transitions state)
    (log/warn "System already stopped.")
    (do
      (set-system (component/stop system))
      (set-state :stopped)))
  state)

(defn restart
  ([]
    (restart :default))
  ([mode]
    (stop)
    (start mode)))

(defn startup
  "Initialize a system and start all of its components.

  This is essentially a convenience wrapper for `init` + `start`."
  ([]
    (startup :default))
  ([mode]
    (if (contains? invalid-startup-transitions state)
      (log/warn "System is already running.")
      (do
        (when-not (contains? invalid-init-transitions state)
          (init mode))
        (when-not (contains? invalid-start-transitions state)
          (start mode))
        (set-state :running)
        state))))

(defn shutdown
  []
  "Stop a running system and de-initialize it.

  This is essentially a convenience wrapper for `stop` + `deinit`."
  (if (contains? invalid-shutdown-transitions state)
    (log/warn "System is already shutdown.")
    (do
      (when-not (contains? invalid-stop-transitions state)
        (stop))
      (when-not (contains? invalid-deinit-transitions state)
        (deinit))
      (set-state :shutdown)
      state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Reloading Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset
  []
  (stop)
  (deinit)
  (repl/refresh :after 'cmr.graph.dev.system/startup))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; TBD

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Aliases   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def reload #'repl/refresh)
