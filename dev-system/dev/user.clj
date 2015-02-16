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

(def system-type :in-memory)
; (def system-type :external-dbs)

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
