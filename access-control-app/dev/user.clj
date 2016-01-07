(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [clojure.test :refer (run-all-tests)]
            [clojure.repl :refer :all]
            [cmr.access-control.system :as system]
            [cmr.common.jobs :as jobs]
            [cmr.metadata-db.system :as mdb]
            [cmr.metadata-db.config :as mdb-config]
            [cmr.metadata-db.data.memory-db :as memory]
            [cmr.message-queue.queue.memory-queue :as mem-queue]
            [cmr.mock-echo.system :as mock-echo]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.dev.util :as d]))

(def system nil)

(def mdb-system nil)

(def mock-echo-system nil)

(defn create-mdb-system
  []
  (let [mq (mem-queue/create-memory-queue-broker (mdb-config/rabbit-mq-config))
        db (memory/create-db)
        mdb-sys (mdb/create-system)]
    (assoc mdb-sys
           :queue-broker mq
           :db db
           :scheduler (jobs/create-non-running-scheduler))))

(defn start
  "Starts the current development system."
  []
  ;; Starts mock echo
  (alter-var-root
   #'mock-echo-system
   (constantly (mock-echo/start (mock-echo/create-system))))
  ;; Start metadata db
  (alter-var-root
   #'mdb-system
   (constantly (mdb/start (create-mdb-system))))
  ;; Start access control
  (alter-var-root
   #'system
   (constantly (system/start (system/create-system))))

  (d/touch-user-clj))

(defn when-not-nil
  "Applies f to value when not nil"
  [f]
  (fn [v]
    (when v (f v))))

(defn stop
  "Shuts down and destroys the current development system."
  []
  ;; Starts mock echo
  (alter-var-root #'mock-echo-system (when-not-nil mock-echo/stop))
  ;; Start metadata db
  (alter-var-root #'mdb-system (when-not-nil mdb/stop))
  ;; Start access control
  (alter-var-root #'system (when-not-nil system/stop)))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(info "Custom access-control user.clj loaded.")
