(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.dev-system.system :as system]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.dev.util :as d]
            [cmr.system-int-test.system :as sit-sys]
            [cmr.common.jobs :as jobs]
            [cmr.common.config :as config]
            [earth.driver :as earth-viz]
            [common-viz.util :as common-viz]
            [vdd-core.core :as vdd]
            [cmr.indexer.config :as indexer-config]
            [cmr.transmit.config :as transmit-config]
            [cmr.elastic-utils.config :as elastic-config])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]
        [cmr.common.dev.capture-reveal]))

(def external-elastic-port 9210)

(def external-echo-port 10000)

(defn external-echo-system-token
  "Returns the ECHO system token based on the value for ECHO_SYSTEM_READ_TOKEN in the ECHO
  configuration file.  The WORKSPACE_HOME environment variable must be set in order to find the
  file.  Returns nil if it cannot extract the value."
  []
  (try
    (->> (slurp (str (System/getenv "WORKSPACE_HOME") "/deployment/primary/config.properties"))
         (re-find #"\n@ECHO_SYSTEM_READ_TOKEN@=(.*)\n")
         peek)
    (catch Exception e
      (warn "Unable to extract the ECHO system read token from configuration.")
      nil)))

(def system nil)

(def system-type
  "A map of whether the components use in-memory versions or external versions.
  The components are elastic, db, message-queue, and echo."
  {
   :elastic :in-memory
   ; :elastic :external
   :echo :in-memory
   ;; Note external ECHO does not work with the automated tests. The automated tests expect they
   ;; can interact with the Mock ECHO to setup users, acls, and other ECHO objects.
   ; :echo :external
   :db :in-memory
   ; :db :external
   :message-queue :in-memory
   ; :message-queue :external
   })

(defn set-config-by-system-type
  "Overrides environment variables to setup communication parameters."
  [system-type]

  ;; Set the default job start delay to avoid jobs kicking off with tests etc.
  (jobs/set-default-job-start-delay! (* 3 3600))

  (when (= :external (:echo system-type))
    (transmit-config/set-echo-rest-port! external-echo-port)
    (transmit-config/set-echo-system-token! (external-echo-system-token))
    (transmit-config/set-echo-rest-context! "/echo-rest"))

  (if (= :in-memory (:elastic system-type))
    (elastic-config/set-elastic-port! system/in-memory-elastic-port)
    (elastic-config/set-elastic-port! external-elastic-port))

  (if (= :in-memory (:message-queue system-type))
    (indexer-config/set-indexing-communication-method! "http")
    (indexer-config/set-indexing-communication-method! "queue")))

(defn start
  "Starts the current development system."
  []
  (config/reset-config-values)

  (set-config-by-system-type system-type)

  (let [s (system/create-system system-type)]
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


(defn reload-coffeescript []
  (do
    (println "Compiling coffeescript")
    (println (common-viz/compile-coffeescript (get-in system [:components :vdd-server :config])))
    (vdd/data->viz {:cmd :reload})))

(info "Custom dev-system user.clj loaded.")
