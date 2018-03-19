(ns cmr.graph.system
  "CMR Graph system management."
  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State & Transition Vars   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *state*
  (atom {:status :stopped
         :system nil
         :ns ""}))

(def valid-stop-transitions #{:started :running})
(def invalid-init-transitions #{:initialized :started :running})
(def invalid-deinit-transitions #{:started :running})
(def invalid-start-transitions #{:started :running})
(def invalid-stop-transitions #{:stopped})
(def invalid-startup-transitions #{:running})
(def invalid-shutdown-transitions #{:uninitialized :shutdown})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn resolve-by-name
  [an-ns a-fun]
  (resolve (symbol (str an-ns "/" a-fun))))

(defn call-by-name
  [an-ns a-fun & args]
  (apply (resolve-by-name an-ns a-fun) args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   System State API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-state
  []
  @*state*)

(defn set-state
  [new-state]
  (reset! *state* new-state))

(defn get-status
  []
  (:status (get-state)))

(defn set-status
  [value]
  (set-state (assoc (get-state) :status value)))

(defn get-system
  []
  (:system (get-state)))

(defn set-system
  [value]
  (set-state (assoc (get-state) :system value)))

(defn get-system-ns
  []
  (:ns (get-state)))

(defn set-system-ns
  [an-ns]
  (set-state (assoc (get-state) :ns an-ns)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init
  ([]
    (init :default))
  ([mode]
    (if (contains? invalid-init-transitions (get-status))
      (log/warn "System has aready been initialized.")
      (do
        (set-system (call-by-name (get-system-ns) "init"))
        (set-status :initialized)))
    (get-status)))

(defn deinit
  []
  (if (contains? invalid-deinit-transitions (get-status))
    (log/error "System is not stopped; please stop before deinitializing.")
    (do
      (set-system nil)
      (set-status :uninitialized)))
  (get-status))

(defn start
  ([]
    (start :default))
  ([mode]
    (when (nil? (get-status))
      (init mode))
    (if (contains? invalid-start-transitions (get-status))
      (log/warn "System has already been started.")
      (do
        (set-system (component/start (get-system)))
        (set-status :started)))
    (get-status)))

(defn stop
  []
  (if (contains? invalid-stop-transitions (get-status))
    (log/warn "System already stopped.")
    (do
      (set-system (component/stop (get-system)))
      (set-status :stopped)))
  (get-status))

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
    (if (contains? invalid-startup-transitions (get-status))
      (log/warn "System is already running.")
      (do
        (when-not (contains? invalid-init-transitions (get-status))
          (init mode))
        (when-not (contains? invalid-start-transitions (get-status))
          (start mode))
        (set-status :running)
        (get-status)))))

(defn shutdown
  []
  "Stop a running system and de-initialize it.

  This is essentially a convenience wrapper for `stop` + `deinit`."
  (if (contains? invalid-shutdown-transitions (get-status))
    (log/warn "System is already shutdown.")
    (do
      (when-not (contains? invalid-stop-transitions (get-status))
        (stop))
      (when-not (contains? invalid-deinit-transitions (get-status))
        (deinit))
      (set-status :shutdown)
      (get-status))))
