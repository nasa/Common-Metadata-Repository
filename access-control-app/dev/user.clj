(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require
   [clojure.pprint :refer (pprint pp)]
   [clojure.repl :refer :all]
   [clojure.test :refer (run-all-tests)]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [cmr.access-control.int-test.fixtures :as int-test-util]
   [cmr.access-control.system :as system]
   [cmr.elastic-utils.config :as elastic-config]
   [cmr.elastic-utils.embedded-elastic-server :as es]
   [cmr.common-app.test.side-api :as side-api]
   [cmr.common.dev.util :as d]
   [cmr.common.jobs :as jobs]
   [cmr.common.lifecycle :as l]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.message-queue.test.queue-broker-side-api :as queue-broker-side-api]
   [cmr.message-queue.test.queue-broker-wrapper :as queue-broker-wrapper]
   [cmr.metadata-db.system :as mdb]
   [cmr.mock-echo.system :as mock-echo]
   [cmr.transmit.config :as transmit-config]
   [compojure.core :as compojure]
   proto-repl.saved-values))

(def system nil)

(def elastic-server nil)

(def side-api-server nil)

(def mdb-system nil)

(def mock-echo-system nil)

(defn disable-access-log
  "Disables use of the access log in the given system"
  [system]
  (assoc-in system [:web :use-access-log?] false))

(def use-external-db?
  "Set to true to use the Oracle DB"
  ; true
  false)

(def use-external-mq?
  "Set to true to use Rabbit MQ"
  ; true
  false)

(defn- create-elastic-server
  "Creates an instance of an elasticsearch server in memory."
  []
  (elastic-config/set-elastic-port! 9306)
  (es/create-server 9306 {:log-level (system/log-level)}))

(defn start
  "Starts the current development system."
  []
  (jobs/set-default-job-start-delay! (* 3 3600))

  ;; Configure ports so that it won't conflict with another REPL containing the same applications.
  (transmit-config/set-access-control-port! 4011)
  (system/set-access-control-public-port! 4011)
  (transmit-config/set-metadata-db-port! 4001)
  (transmit-config/set-echo-rest-port! 4008)
  (transmit-config/set-mock-echo-port! 4008)
  (transmit-config/set-urs-port! 4008)
  (side-api/set-side-api-port! 3999)

  (let [queue-broker (queue-broker-wrapper/create-queue-broker-wrapper
                      (if use-external-mq?
                        (queue-broker/create-queue-broker (int-test-util/queue-config))
                        (int-test-util/create-broker)))]
    ;; Start side api server
    (alter-var-root
     #'side-api-server
     (constantly (-> (side-api/create-side-server
                      (fn [_]
                        (compojure/routes
                         side-api/eval-routes
                         (queue-broker-side-api/build-routes queue-broker))))
                     (l/start nil))))

    ;; Start mock echo
    (alter-var-root
     #'mock-echo-system
     (constantly (-> (mock-echo/create-system) disable-access-log mock-echo/start)))

    ;; Start metadata db
    (alter-var-root
     #'mdb-system
     (constantly (-> (int-test-util/create-mdb-system use-external-db?)
                     (assoc :queue-broker queue-broker)
                     disable-access-log
                     mdb/start)))

    ;; Start elastic search
    (alter-var-root
     #'elastic-server
     (constantly (l/start (create-elastic-server) nil)))

    ;; Start access control
    (alter-var-root
     #'system
     (constantly (-> (system/create-system)
                     (assoc :queue-broker queue-broker)
                     disable-access-log
                     system/dev-start))))

  (d/touch-user-clj))

(defn when-not-nil
  "Applies f to value when not nil"
  [f]
  (fn [v]
    (when v (f v))))

(defn stop
  "Shuts down and destroys the current development system."
  []
  ;; Stop the side api server
  (alter-var-root #'side-api-server (when-not-nil #(l/stop % nil)))
  ;; Stop mock echo
  (alter-var-root #'mock-echo-system (when-not-nil mock-echo/stop))
  ;; Stop metadata db
  (alter-var-root #'mdb-system (when-not-nil mdb/stop))
  ;; Stop elastic search
  (alter-var-root #'elastic-server #(when % (l/stop % system)))
  ;; Stop access control
  (alter-var-root #'system (when-not-nil system/stop)))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(info "Custom access-control user.clj loaded.")
