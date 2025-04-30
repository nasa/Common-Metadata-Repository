(ns cmr.system-int-test.system
  "Defines a system for system integration tests. The system will maintain anything stateful during
  system integration tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clj-http.conn-mgr :as conn-mgr]
   [cmr.bootstrap.config :as bootstrap-config]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.system :as common-sys]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kl]
   [cmr.metadata-db.services.util :as mdb-util]
   [cmr.oracle.connection :as oracle]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit-config]))

(def ^:private component-order
  "Defines the order to start the components."
  [:log :bootstrap-db])

(def ^:private saved-system
  "Saves the last started system so it's available to integration test code which has no way of
  receiving context information"
  (atom nil))

(def logging-level-atom
  (atom :debug))

(defn set-logging-level
  [level]
  (reset! logging-level-atom level))

(def connected-sevices
  "Services to configure transmit lib to setup in the system object"
  [:kms :echo-rest :search :access-control :urs :ingest :indexer :metadata-db])

(def application-caches
  "Redis caches"
  {kf/kms-cache-key (kf/create-kms-cache)
   kl/kms-short-name-cache-key (kl/create-kms-short-name-cache)
   kl/kms-umm-c-cache-key (kl/create-kms-umm-c-cache)
   kl/kms-location-cache-key (kl/create-kms-location-cache)
   kl/kms-measurement-cache-key (kl/create-kms-measurement-cache)})

(defn create-system
  "Returns a new instance of the whole application."
  [component-type-map]
  (let [sys {:instance-name (common-sys/instance-name "system-int-test")
             :log (log/create-logger {:level @logging-level-atom})
             :bootstrap-db (when (= :external (:db component-type-map))
                             (mdb-util/create-db (bootstrap-config/db-spec "bootstrap-test-pool")))
             ;; the HTTP connection manager to use. This allows system integration tests to use persistent
             ;; HTTP connections
             :conn-mgr (conn-mgr/make-reusable-conn-manager {})
             ;; An atom containing an integer that gets incremented to make unique numbers for items
             :unique-num-atom (atom 0)
             :caches application-caches
             ;; A map of the components (echo, elastic, db, and message queue) to whether they are
             ;; in-memory or external
             :component-type-map component-type-map}
        system (transmit-config/system-with-connections sys connected-sevices)
        context {:system system}]
    (kf/refresh-kms-cache context)
    system))

(defn get-component-type-map
  "Returns the component-type-map from dev-system."
  []
  (let [component-type-map (-> (client/get (url/dev-system-get-component-types-url))
                               :body
                               json/decode)]
    (into {}
          (for [[k v] component-type-map]
            [(keyword k) (keyword v)]))))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  ([]
   (transmit-config/set-urs-relative-root-url! "/urs")
   (start (create-system (get-component-type-map))))
  ([system]
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

(defn context
  "Returns a context containing the system"
  []
  {:system (system)})

(defn conn-mgr
  "Returns the HTTP connection manager to use. This allows system integration tests to use
  persistent HTTP connections"
  []
  (:conn-mgr (system)))

(defn in-memory-database?
  "Returns true if running with a in-memory database"
  []
  (= :in-memory (get-in (system) [:component-type-map :db])))

(defn real-database?
  "Returns true if running with a in-memory database"
  []
  (= :external (get-in (system) [:component-type-map :db])))

(defn real-message-queue?
  "Returns true if running with a real message-queue"
  []
  (= :external (get-in (system) [:component-type-map :message-queue])))

(defmacro only-with-real-database
  "Executes the body of the call if the test environment is running with the real Oracle DB."
  [& body]
  `(when (real-database?)
     ~@body))

(defmacro only-with-in-memory-database
  "Executes the body of the call if the test environment is running with the in memory database"
  [& body]
  `(when (in-memory-database?)
     ~@body))

(defmacro only-with-real-message-queue
  "Executes the body of the call if the test environment is running with the real RabbitMQ."
  [& body]
  `(when (real-message-queue?)
     ~@body))

(comment
  (real-database?)
  (real-message-queue?))
