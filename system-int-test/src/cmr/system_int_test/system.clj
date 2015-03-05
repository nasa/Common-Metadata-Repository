(ns cmr.system-int-test.system
  "Defines a system for system integration tests. The system will maintain anything stateful during
  system integration tests."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.metadata-db.config :as mdb-config]
            [cmr.oracle.connection :as oracle]
            [clj-http.conn-mgr :as conn-mgr]
            [cmr.transmit.config :as transmit-config]))

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :bootstrap-db])

;; TODO remove this when create-system takes a map
(defn real-database?
  "Returns true if running with a real database"
  []
  false)
; (= (:db (get-component-type-map)) :external))

(defn create-system
  "Returns a new instance of the whole application.
  TODO Should take the component-map as an argument."
  []
  (let [sys {:log (log/create-logger)
             :bootstrap-db (when (real-database?)
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

;;;;;;;;;;;;;;;;;;;; Came from url-helper namespace
(defn conn-mgr
  "Returns the HTTP connection manager to use. This allows system integration tests to use persistent
  HTTP connections"
  []
  (:conn-mgr (system)))

;;;;;;;;;;;;;;;;;;;;; Came from test-env namespace

; (defn- get-component-type-map
;   "Returns the message queue history."
;   []
;   (let [component-map (-> (client/get (url/dev-system-get-component-types-url)
;                                       {:connection-manager (conn-mgr)})
;                           :body
;                           json/decode)]
;     (into {}
;           (for [[k v] component-map]
;             [(keyword k) (keyword v)]))))



(defn in-memory-database?
  "Returns true if running with a in-memory database"
  []
  true)
; (= (:db (get-component-type-map)) :in-memory))


(defn real-message-queue?
  "Returns true if running with a real message-queue"
  []
  true)
; (= (:message-queue (get-component-type-map)) :external))


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
