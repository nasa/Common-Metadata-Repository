(ns user
  (:require
   [alex-and-georges.debug-repl]
   [clojure.main]
   [clojure.pprint :refer [pp pprint]]
   [clojure.repl]
   [clojure.test :only [run-all-tests]]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [cmr.common.config :as config]
   [cmr.common.dev.capture-reveal]
   [cmr.common.dev.util :as d]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as u]
   [cmr.dev-system.system :as system]
   [cmr.dev-system.tests :as tests]
   [cmr.system-int-test.system :as sit-sys]
   [debugger.core]
   [proto-repl.saved-values] ;; For Proto REPL lib capabilities
   [refresh-persistent-settings :as settings]))

(defonce system nil)

(defn configure-systems-logging
  "Configures the systems in the system map to the indicated level"
  [system level]
  (update system :apps
          (fn [app-map]
            (u/map-values #(assoc-in % [:log :level] level) app-map))))

(defn set-logging-level!
  "Sets the logging level to the given setting. Puts the level in refresh-persistent-settings
   so that the level will be kept through refreshes. "
  [level]
  ;; Store it in persistent settings to keep the level through refreshes
  (reset! settings/logging-level level)
  ;; Change the currently configured systems to all use the new level
  (alter-var-root #'system
                  #(when % (configure-systems-logging % level)))
  ;; Change system integration tests to the new level
  (sit-sys/set-logging-level level)

  ;; Set timbre.logging to the level
  (taoensso.timbre/set-level! level)
  (println "Logging level set to" level)
  nil)

(defn configure-for-soap-services
  []
  (config/set-config-value! :echo-rest-host "localhost")
  (config/set-config-value! :echo-rest-port 3012)
  (config/set-config-value! :echo-rest-context "/soap-services/rest")
  (config/set-config-value! :dev-system-echo-type "external")
  (config/set-config-value! :dev-system-db-type "in-memory"))

(defn start
  "Starts the current development system."
  []
  (config/reset-config-values)

  ;; Uncomment this to force CMR to use SOAP Services
  (configure-for-soap-services)

  (jobs/set-default-job-start-delay! (* 3 3600))

  (system/set-gorilla-repl-port! 8090)

  ;; Comment/uncomment these lines to switch between external and internal settings.

  (system/set-dev-system-elastic-type! :in-memory)
  ; (system/set-dev-system-elastic-type! :external)

  ;; Note external ECHO does not work with the automated tests. The automated tests expect they
  ;; can interact with the Mock ECHO to setup users, acls, and other ECHO objects.
  (system/set-dev-system-echo-type! :in-memory)
  ; (system/set-dev-system-echo-type! :external)


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; MAKE SURE YOU DISABLE SYMANTEC ANTIVIRUS BEFORE STARTING THE TESTS WITH EXTERNAL DB
  ;; Renable them when you're done

  (system/set-dev-system-db-type! :in-memory)
  ; (system/set-dev-system-db-type! :external)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (system/set-dev-system-message-queue-type! :in-memory)
  ; (system/set-dev-system-message-queue-type! :external)
  ; (system/set-dev-system-message-queue-type! :aws)

  (sit-sys/set-logging-level @settings/logging-level)

  (let [s (system/create-system)
        s (configure-systems-logging s @settings/logging-level)]
    (alter-var-root #'system
                    (constantly
                      (system/start s))))

  (d/touch-user-clj))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s]
                    (when s (system/stop s)))))

(defn reset []
  ;; Stop the system integration test system
  (sit-sys/stop)
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))


(defn run-all-tests-future
  "Runs all tests asynchronously, with :fail-fast? and :speak? enabled."
  []
  (future
    (tests/run-all-tests {:fail-fast? true :speak? true})))

(info "Custom dev-system user.clj loaded.")
