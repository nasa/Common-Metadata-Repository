(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [clojure.test :refer (run-all-tests)]
            [clojure.repl :refer :all]
            [cmr.access-control.system :as system]
            [cmr.access-control.int-test.access-control-test-util :as int-test-util]
            [cmr.metadata-db.system :as mdb]
            [cmr.mock-echo.system :as mock-echo]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.dev.util :as d]))

(def system nil)

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

(defn start
  "Starts the current development system."
  []

  (transmit-config/set-access-control-port! 4011)
  (transmit-config/set-metadata-db-port! 4001)
  (transmit-config/set-echo-rest-port! 4008)
  (transmit-config/set-urs-port! 4008)

  ;; Start mock echo
  (alter-var-root
   #'mock-echo-system
   (constantly (-> (mock-echo/create-system) disable-access-log mock-echo/start)))
  ;; Start metadata db
  (alter-var-root
   #'mdb-system
   (constantly (-> (int-test-util/create-mdb-system use-external-db?) disable-access-log mdb/start)))
  ;; Start access control
  (alter-var-root
   #'system
   (constantly (-> (system/create-system) disable-access-log system/start)))

  (d/touch-user-clj))

(defn when-not-nil
  "Applies f to value when not nil"
  [f]
  (fn [v]
    (when v (f v))))

(defn stop
  "Shuts down and destroys the current development system."
  []
  ;; Stop mock echo
  (alter-var-root #'mock-echo-system (when-not-nil mock-echo/stop))
  ;; Stop metadata db
  (alter-var-root #'mdb-system (when-not-nil mdb/stop))
  ;; Stop access control
  (alter-var-root #'system (when-not-nil system/stop)))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(info "Custom access-control user.clj loaded.")
