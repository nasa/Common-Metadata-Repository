(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [clojure.test :refer (run-all-tests)]
            [clojure.repl :refer :all]
            [alex-and-georges.debug-repl :refer (debug-repl)]
            [cmr.index-set.system :as system]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.index-set.api.routes :as routes]
            [cmr.elastic-utils.config :as elastic-config]
            [cmr.elastic-utils.embedded-elastic-server :as es]
            [cmr.transmit.config :as transmit-config]
            [cmr.mock-echo.system :as mock-echo]
            [cmr.common.lifecycle :as l]
            [cmr.common.dev.repeat-last-request :as repeat-last-request :refer (repeat-last-request)]
            [cmr.common.dev.util :as d]))

(def system nil)

(def elastic-server nil)

(def mock-echo-system nil)

(defn disable-access-log
  "Disables use of the access log in the given system"
  [system]
  (assoc-in system [:web :use-access-log?] false))

(defn- create-elastic-server
  "Creates an instance of an elasticsearch server in memory."
  []
  (elastic-config/set-elastic-port! 9406)
  (es/create-server 9406 9316 "es_data/index_set"))

(defn start
  "Starts the current development system."
  []

  ;; Configure ports so that it won't conflict with another REPL containing the same applications.
  (transmit-config/set-index-set-port! 4011)
  (transmit-config/set-echo-rest-port! 4008)

  ;; Start mock echo
  (alter-var-root
   #'mock-echo-system
   (constantly (-> (mock-echo/create-system) disable-access-log mock-echo/start)))

  ;; Start elastic search
  (alter-var-root
   #'elastic-server
   (constantly (l/start (create-elastic-server) nil)))

  (let [s (disable-access-log (system/create-system))]
    (alter-var-root #'system
                    (constantly
                      (system/start s))))
  (d/touch-user-clj))

(defn stop
  "Shuts down and destroys the current development system."
  []
  ;; Stop mock echo
  (alter-var-root #'mock-echo-system #(when % (mock-echo/stop %)))
  ;; Stop elastic search
  (alter-var-root #'elastic-server #(when % (l/stop % system)))
  (alter-var-root #'system
                  (fn [s] (when s (system/stop s)))))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(info "Custom index-set user.clj loaded.")
