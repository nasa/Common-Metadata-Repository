(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.dev-system.system :as system]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.dev.util :as d]
            [cmr.system-int-test.system :as sit-sys]
            [cmr.common.config :as config]
            [earth.driver :as earth-viz]
            [common-viz.util :as common-viz]
            [vdd-core.core :as vdd])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]
        [cmr.common.dev.capture-reveal]))

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

(defn start
  "Starts the current development system."
  []
  (config/reset-config-values)

  ;; Set the default job start delay to avoid jobs kicking off with tests etc.
  (config/set-config-value! :default-job-start-delay (str (* 3 3600)))

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
