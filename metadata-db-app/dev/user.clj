(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.metadata-db.system :as system]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.metadata-db.data.memory-db :as memory]
            [cmr.common.dev.util :as d]
            [cmr.mock-echo.system :as mock-echo])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

(def system nil)
(def mock-echo nil)

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'mock-echo
                  (constantly
                    (mock-echo/start (mock-echo/create-system))))


  (let [s (system/create-system)
        ;; uncomment to test the memory db
         ; s (-> s
         ;       (assoc :db (memory/create-db))
         ;       (dissoc :scheduler))
        ]
    (alter-var-root #'system
                    (constantly
                      (system/start s))))
  (d/touch-user-clj)
  (d/touch-files-in-dir "src/cmr/metadata_db/services"))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'mock-echo
                  (fn [s] (when s (mock-echo/stop s))))
  (alter-var-root #'system
                  (fn [s] (when s (system/stop s)))))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(info "Custom user.clj loaded.")
