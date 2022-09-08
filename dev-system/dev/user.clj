(ns user
  (:require
   [alex-and-georges.debug-repl]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.main]
   [clojure.pprint :refer [pp pprint]]
   [clojure.repl :refer :all]
   [clojure.string :as string]
   [clojure.test :refer [*test-out* run-all-tests run-tests]]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [cmr.access-control.system :as access-control-system]
   [cmr.common.config :as config]
   [cmr.common.dev.capture-reveal]
   [cmr.common.dev.util :as d]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.test.runners.default :as test-runner]
   [cmr.common.test.runners.ltest :as ltest]
   [cmr.common.test.runners.util :as runner-util]
   [cmr.common.util :as u]
   [cmr.dev-system.config :as dev-config]
   [cmr.dev-system.system :as system]
   [cmr.dev-system.tests :as tests]
   [cmr.ingest.system :as ingest-system]
   [cmr.message-queue.config :as q-config]
   [cmr.search.services.humanizers.humanizer-report-service :as humanizer-report-service]
   [cmr.search.system :as search-system]
   [cmr.system-int-test.system :as sit-sys]
   [cmr.transmit.config :as transmit-config]
   [debugger.core]
   [proto-repl.saved-values] ;; For Proto REPL lib capabilities
   [refresh-persistent-settings :as settings]
   [selmer.parser :as selmer]))

;; In the development environment, we want to see changes made to templates,
;; so we turn Selmer caching off by default. In order to test template page
;; caching in the REPL, simply call `(selmer/cache-on!)`.
(selmer/cache-off!)

(defonce system nil)

(defn configure-systems-logging
  "Configures the systems in the system map to the indicated level"
  [system level]
  (update system :apps
          (fn [app-map]
            (u/map-values #(assoc-in % [:log :level] level) app-map))))

(defn- maybe-set-all-modes
  "A utility function that will set all the keys of `settings/run-modes`
  to the value associated with the passed parameter `all` only if `all` is not
  `nil`. In the event that `all` is `nil`, the run modes passed in will be
  returned as-is.

  This behaviour makes the function conditionally side-effect generating.

  Note that when a non-`nil` value for `all` is provided, a `deref`-ed copy of
  `settings/run-modes` is used as the starting accumulator for usage in a
  reduce. The old run modes values aren't used, however -- just their keys; the
  values are overwritten with what is stored in the `all` variable."
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
  * `:redis VALUE`
  * `:echo VALUE`
  * `:db VALUE`
  * `:messaging VALUE`

  Allowed run mode values are currently:
  * :in-memory
  * :external

  Examples:
  ```
  => (set-modes! :db :external)
  ```
  ```
  => (set-modes! :elastic :in-memory :echo :external)
  ```

  Note that you can also pass these parameters to the `reset` function, which
  will call `set-modes!` as shown above. Examples:
  ```
  => (reset :db :external)
  ```
  ```
  => (reset :elastic :in-memory :echo :external)
  ```"
  ;; Note that the keys are listed below as a means of self-documentation; they
  ;; are not actually used individually, but rather as a whole with the
  ;; `new-modes` hash map.
  [& {:keys [all _elastic _echo _db _messaging _redis] :as new-modes}]
  (->> new-modes
       (maybe-set-all-modes all)
       (remove #(nil? (val %)))
       (into {})
       (merge @settings/run-modes)
       ((fn [x] (println "Resetting with the run modes:" x) x))
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
  (reset! settings/aws? bool)
  (if bool
    (q-config/set-queue-type! "aws")
    (q-config/set-queue-type! "memory")))

;; If the ENV var was set, let's make it a keyword, which is what the config
;; for dev-system expects.
(when (string? (dev-config/dev-system-queue-type))
  (dev-config/set-dev-system-queue-type!
   (keyword (dev-config/dev-system-queue-type))))
;; If the ENV var for the dev queue type was set to use AWS, let's make the
;; `set-aws` call.
(if (= :aws (dev-config/dev-system-queue-type))
  (set-aws true)
  (set-aws false))

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

  ;; Change embedd elastic logging level to the new level
  (reset! system/in-memory-elastic-log-level-atom level)

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

(defn banner
  "Who doesn't like a banner?"
  []
  (println (slurp (io/resource "text/banner.txt")))
  :ok)

(defn network-purge
  "Tell docker to purge the network"
  []
  (println "Purging the docker networks, this may take a minute...")
  (shell/sh "docker" "network" "prune" "-f"))

(defn start
  "Starts the current development system, taking optional keyword arguments for
  various run modes (see the docstring for `set-modes!` for more details).

  Note that when one or more explicit modes are passed as arguments, the global
  `run-modes` data structure is updated.

  If a run mode for a particular component is not passed, its value is taken
  from what is already in the `run-modes` global data structure. If no mode has
  been set, the default from initialization will be used.

  Examples:
  ```
  => (start :db :external)
  ```
  ```
  => (start :elastic :in-memory :echo :external)
  ```"
  ;; Note that even through the named args are not used, they are provided as
  ;; a means of self-documentation.
  [& {:keys [_elastic _echo _db _messaging _redis] :as new-modes}]

  (config/reset-config-values)

  (jobs/set-default-job-start-delay! (* 3 3600))

  ;; Prevent jobs from blocking calls to reset
  (humanizer-report-service/set-retry-count! 0)
  (humanizer-report-service/set-humanizer-report-generator-job-wait! 0)

  (let [run-modes @settings/run-modes]
    (when-not (empty? new-modes)
      (apply set-modes! (mapcat seq new-modes)))

    (dev-config/set-dev-system-elastic-type! (:elastic run-modes))

    (dev-config/set-dev-system-redis-type! (:redis run-modes))

    (dev-config/set-dev-system-echo-type! (:echo run-modes))
    ;; IMPORTANT: MAKE SURE YOU DISABLE SYMANTEC ANTIVIRUS BEFORE STARTING THE
    ;; TESTS WITH EXTERNAL DB (re-enable them when you're done)
    (dev-config/set-dev-system-db-type! (:db run-modes))
    ;; If you would like to run CMR with :aws instead of :in-memory or :external,
    ;; be sure to call `(set-aws true)` in the REPL.
    (if (or @settings/aws?
            (= "aws" (q-config/queue-type)))
      (dev-config/set-dev-system-queue-type! :aws)
      (dev-config/set-dev-system-queue-type! (:messaging run-modes))))

  (sit-sys/set-logging-level @settings/logging-level)
  (reset! system/in-memory-elastic-log-level-atom @settings/logging-level)

  ;; If you would like to run CMR with legacy support, then be sure to call
  ;; `(set-legacy true)` in the REPL. There is currently no lein profile or
  ;; command line option for this.
  (when @settings/legacy?
    (configure-for-legacy-services))

  (let [s (-> (system/create-system)
              (configure-systems-logging @settings/logging-level)
              ;; The following inclusion of public-conf data is done in order
              ;; to support search and access-control web pages and their use
              ;; of templates which (indirectly) make use of/require app
              ;; public-conf data.
              (assoc-in [:apps :search :public-conf]
                        (search-system/public-conf))
              (assoc-in [:apps :access-control :public-conf]
                        (access-control-system/public-conf))
              (assoc-in [:apps :ingest :public-conf]
                        (ingest-system/public-conf)))]
    (alter-var-root #'system
                    (constantly
                     (system/start s))))

  (d/touch-user-clj)
  (banner))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system (fn [s] (when s (system/stop s))))

  ;; remove all the old connections from docker for the humans
  (network-purge))

(defn reset
  "Resets the development environment, taking optional keyword arguments for
  various run modes, e.g.:
  ```
  => (reset :db :external)
  ```
  ```
  => (reset :elastic :in-memory :echo :external)
  ```

  See the docstring for `set-modes!` for more details.

  Environment resetting includes the reloading of any changed namespaces and
  the restarting the CMR services.

  Note that when one or more explicit modes are passed as arguments, the global
  `run-modes` data structure is updated.

  If a run mode for a particular component is not passed, its value taken from
  what is already in the `run-modes` global data structure. If no mode has been
  set, the default from initialization will be used."
  ;; Note that even through the named args are not used, they are provided as
  ;; a means of self-documentation.
  [& {:keys [_elastic _echo _db _messaging _redis] :as new-modes}]
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

(defn run-suites
  "Runs the suites defined by the ltest runner (unit and integration), first
  setting the log level to `:fatal` to keep the terminal output cleaner. To
  run with the usual error messages to STDOUT, simply use `ltest/run-suites`
  directly.

  To run just a single suite:
  ```
  (ltest/run-unit-tests)
  ```
  or
  ```
  (ltest/run-integration-tests)
  ```

  Furthermore, to run a test namespace (or collection of namespaces) using
  this runner:
  ```
  (ltest/run-tests ['cmr.system-int-test.health-test])
  ```

  You can also run a single test function using this runner by passing a test
  function var:
  ```
  (ltest/run-test #'cmr.system-int-test.health-test/ingest-health-test)
  ```

  Note that none of the `(ltest/*)` functions silence logging; only the
  `run-suites` function defined in this `user` namespace provides that
  convenience."
  ([]
   (let [orig-log-level @settings/logging-level]
     (set-logging-level! :fatal)
     (ltest/run-suites)
     (set-logging-level! orig-log-level))))


(info "Custom dev-system user.clj loaded.")
