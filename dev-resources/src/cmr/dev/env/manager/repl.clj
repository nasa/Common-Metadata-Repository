(ns cmr.dev.env.manager.repl
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.twig :as logger]
   [cmr.dev.env.manager.components.system :as components]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]
   [trifl.java :refer [show-methods]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State & Transition Vars   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def state :stopped)
(def system nil)
(def valid-stop-transitions #{:started :running})
(def invalid-init-transitions #{:initialized :started :running})
(def invalid-deinit-transitions #{:started :running})
(def invalid-start-transitions #{:started :running})
(def invalid-stop-transitions #{:stopped})
(def invalid-run-transitions #{:running})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(logger/set-level! '[cmr] :info)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init
  ([]
    (init :default))
  ([mode]
    (if (contains? invalid-init-transitions state)
      (log/error "System has aready been initialized.")
      (do
        (alter-var-root #'system
          (constantly ((components/init mode))))
        (alter-var-root #'state (fn [_] :initialized))))
    state))

(defn deinit
  []
  (if (contains? invalid-deinit-transitions state)
    (log/error "System is not stopped; please stop before deinitializing.")
    (do
      (alter-var-root #'system (fn [_] nil))
      (alter-var-root #'state (fn [_] :uninitialized))))
  state)

(defn start
  ([]
    (start :default))
  ([mode]
    (when (nil? system)
      (init mode))
    (if (contains? invalid-start-transitions state)
      (log/error "System has already been started.")
      (do
        (alter-var-root #'system component/start)
        (alter-var-root #'state (fn [_] :started))))
    state))

(defn stop
  []
  (if (contains? invalid-stop-transitions state)
    (log/error "System already stopped.")
    (do
      (alter-var-root #'system
        (fn [s] (when s (component/stop s))))
      (alter-var-root #'state (fn [_] :stopped))))
  state)

(defn restart
  []
  (stop)
  (start))

(defn run
  []
  (if (contains? invalid-run-transitions state)
    (log/error "System is already running.")
    (do
      (if (not (contains? invalid-init-transitions state))
        (init))
      (if (not (contains? invalid-start-transitions state))
        (start))
      (alter-var-root #'state (fn [_] :running))))
  state)

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
