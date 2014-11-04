(ns cmr.system-int-test.system
  "Defines a system for system integration tests. The system will maintain anything stateful during
  system integration tests."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.system-int-test.utils.test-environment :as test-env]
            [cmr.metadata-db.config :as mdb-config]
            [cmr.oracle.connection :as oracle]
            [clj-http.conn-mgr :as conn-mgr]
            [cmr.transmit.config :as transmit-config]))

(def DEFAULT_PORT 3008)

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :bootstrap-db])

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger)
             :bootstrap-db (when (test-env/real-database?)
                             (oracle/create-db (mdb-config/db-spec "bootstrap-test-pool")))
             ;; the HTTP connection manager to use. This allows system integration tests to use persistent
             ;; HTTP connections
             :conn-mgr (conn-mgr/make-reusable-conn-manager {})
             ;; An atom containing an integer that gets incremented to make unique numbers for items
             :unique-num-atom (atom 0)}]
    (transmit-config/system-with-connections sys [:echo-rest])))

(def ^:private saved-system
  "Saves the last started system so it's available to integration test code which has no way of
  receiving context information"
  (atom nil))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  ([]
   (start (create-system)))
  ([system]
   (info "system-int-test System starting")
   (let [started-system (reduce (fn [system component-name]
                                  (update-in system [component-name]
                                             #(when % (lifecycle/start % system))))
                                system
                                component-order)]
     (reset! saved-system started-system))))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  ([]
   (stop @saved-system))
  ([system]
   (info "system-int-test System shutting down")
   (when system
     (let [stopped-system (reduce (fn [system component-name]
                                    (update-in system [component-name]
                                               #(when % (lifecycle/stop % system))))
                                  system
                                  (reverse component-order))]
       (reset! saved-system nil)
       stopped-system))))

(defn system
  "Returns the last saved system or starts one if necessary"
  []
  (if-let [system @saved-system]
    system
    (start)))
