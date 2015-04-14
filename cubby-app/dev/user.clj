(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [clojure.test :refer (run-all-tests)]
            [clojure.repl :refer :all]
            [alex-and-georges.debug-repl :refer (debug-repl)]
            [cmr.cubby.system :as system]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.dev.util :as d]
            [cmr.common.lifecycle :as l]
            [cmr.elastic-utils.embedded-elastic-server :as es]
            [cmr.elastic-utils.config :as elastic-config]
            [cmr.mock-echo.system :as mock-echo]))

(def in-memory-elastic-port 9206)

(def system nil)

(def elastic-server
  "Local in memory elasticserver to make available when running the cubby app by itself."
  nil)

(def mock-echo
  "Local in memory mocke echo to make available when running the cubby app by itself."
  nil)

(defn- create-elastic-server
  "Creates an instance of an elasticsearch server in memory."
  []
  (elastic-config/set-elastic-port! in-memory-elastic-port)
  (es/create-server
    in-memory-elastic-port
    (+ in-memory-elastic-port 10)
    "es_data/cubby"))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'elastic-server
                  (constantly
                    (l/start (create-elastic-server) nil)))

  (alter-var-root #'mock-echo
                  (constantly
                    (mock-echo/start (mock-echo/create-system))))

  (alter-var-root #'system
                  (constantly
                    (system/start (system/create-system))))

    (d/touch-user-clj))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system #(when % (system/stop %)))
  (alter-var-root #'mock-echo
                  (fn [s] (when s (mock-echo/stop s))))
  (alter-var-root #'elastic-server #(when % (l/stop % system))))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(info "Custom cubby user.clj loaded.")
