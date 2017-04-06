(ns user
  (:require
   [alex-and-georges.debug-repl]
   [clojure.main]
   [clojure.pprint :refer [pp pprint]]
   [clojure.repl :refer :all]
   [clojure.test :refer [run-all-tests run-tests]]
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
   [cmr.transmit.config :as transmit-config]
   [debugger.core]
   [proto-repl.saved-values] ;; For Proto REPL lib capabilities
   [refresh-persistent-settings :as settings]
   [selmer.parser :as selmer]))

;; In the development environment, we want to see changes made to templates;
;; in order to test template page caching in the REPL, simply call
;; `(selmer/cache-on!)`.
(selmer/cache-off!)

(defonce system nil)

(defn configure-systems-logging
  "Configures the systems in the system map to the indicated level"
  [system level]
  (update system :apps
          (fn [app-map]
            (u/map-values #(assoc-in % [:log :level] level) app-map))))

(defn- maybe-set-all-modes
  "A utility function for (conditionally) setting all the values of a map to
  the same value."
  [all new-modes]
  (let [old-modes @settings/run-modes]
    (if (nil? all)
      new-modes
      (-> (fn [acc k v] (assoc acc k all))
          (reduce-kv old-modes old-modes)
          (dissoc :all)))))

(defn set-modes!
  "Set the mode to use when starting/resetting. One or more modes may
  be set at a time by passing one or more of the following keyword
  arguments, where `VALUE` is any alowed run mode:

  * `:all VALUE` - in this case, all other keys will be set to this
    VALUE
  * `:elastic VALUE`
  * `:echo VALUE`
  * `:db VALUE`
  * `:messaging VALUE`

  Allowed run mode values are currently:
  * :in-memory
  * :external

  Examples:
  ```
  => (set-mode! :db :external)
  => (set-modes! :elastic :in-memory :echo :external)
  ```"
  ;; Note that the keys are listed below as a means of self-documentation; they
  ;; are not actually used individuall, but rather as a whole with the
  ;; `new-modes` hash map.
  [& {:keys [all elastic echo db messaging] :as new-modes}]
  (->> new-modes
       (maybe-set-all-modes all)
       (remove #(nil? (val %)))
       (into {})
       (merge @settings/run-modes)
       (reset! settings/run-modes)))

(defn reset-modes!
  "A convenience function for returning all run modes to the default state."
  []
  (set-modes! :all settings/default-run-mode))

(defn set-legacy
  "Passing `true` to this function will cause legacy configuration to be used
  during starts/resets."
  [bool]
  (reset! settings/legacy? bool))

(defn set-aws
  "Passing `true` to this function will cause AWS queues to be used during
  starts/resets."
  [bool]
  (reset! settings/aws? bool))

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

(defn configure-for-legacy-services
  []
  (config/set-config-value! :echo-rest-host "localhost")
  (config/set-config-value! :echo-rest-port 3012)
  (config/set-config-value! :echo-rest-context "/legacy-services/rest")
  (config/set-config-value! :dev-system-db-type :in-memory)
  ;; Hard coded here and here:
  ;; * legacy-services/echo/echo-env/support/db/bootstrap/business/system_acls_changeLog.xml
  ;; so that ACLs that are bootstrapped in kernel reference same Administrators group in
  ;; bootstrapped CMR.
  (transmit-config/set-administrators-group-legacy-guid! "316520E041894014E050007F010038C4"))

(defn start
  "Starts the current development system, taking optional keyword arguments for
  various run modes (see the docstring for `set-modes!` for more details).

  Note that when one or more explicit modes are passed as arguments, the global
  `run-modes` data structure is updated.

  If a run mode for a particular component is not passed, its value taken from
  what is already in the `run-modes` global data structure. If no mode has been
  set, the default from initialization will be used."
  ;; Note that even through the named args are not used, they are provided as
  ;; a means of self-documentation.
  [& {:keys [elastic echo db messaging] :as new-modes}]

  (config/reset-config-values)

  (jobs/set-default-job-start-delay! (* 3 3600))

  (system/set-gorilla-repl-port! 8090)

  (let [run-modes @settings/run-modes]
    (when-not (empty? new-modes)
      (apply set-modes! (mapcat seq new-modes)))

    (system/set-dev-system-elastic-type! (:elastic run-modes))

    (system/set-dev-system-echo-type! (:echo run-modes))
    ;; IMPORTANT: MAKE SURE YOU DISABLE SYMANTEC ANTIVIRUS BEFORE STARTING THE
    ;; TESTS WITH EXTERNAL DB (re-enable them when you're done)
    (system/set-dev-system-db-type! (:db run-modes))
    ;; If you would like to run CMR with :aws instead of :in-memory or :external,
    ;; be sure to call `(set-aws true)` in the REPL.
    (if @settings/aws?
      (system/set-dev-system-message-queue-type! :aws)
      (system/set-dev-system-message-queue-type! (:messaging run-modes))))

  (sit-sys/set-logging-level @settings/logging-level)

  ;; If you would like to run CMR with legacy support, then be sure to call
  ;; `(set-legacy true)` in the REPL. There is currently no lein profile or
  ;; command line option for this.
  (when @settings/legacy?
    (configure-for-legacy-services))

  (let [s (-> (system/create-system)
              (configure-systems-logging @settings/logging-level)
              ;; The following inclusion of public-conf data is done in order
              ;; to support search directory pages and their use of templates
              ;; which (indirectly) make use of/require app public-conf data.
              (assoc-in [:apps :search :public-conf]
                        {:protocol "http"
                         :host "localhost"
                         :port (transmit-config/search-port)
                         :relative-root-url ""}))]
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

(defn reset
  "Resets the development environment, taking optional keyword arguments for
  various run modes (see the docstring for `set-modes!` for more details).
  Environment resetting includes the reloading of any changed namespaces and
  the restarting the CMR services.

  Note that when one or more explicit modes are passed as arguments, the global
  `run-modes` data structure is updated.

  If a run mode for a particular component is not passed, its value taken from
  what is already in the `run-modes` global data structure. If no mode has been
  set, the default from initialization will be used."
  ;; Note that even through the named args are not used, they are provided as
  ;; a means of self-documentation.
  [& {:keys [elastic echo db messaging] :as new-modes}]
  (when-not (empty? new-modes)
    (apply set-modes! (mapcat seq new-modes)))
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
