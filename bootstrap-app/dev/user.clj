(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [clojure.test :refer (run-all-tests)]
            [clojure.repl :refer :all]
            [alex-and-georges.debug-repl :refer (debug-repl)]
            [cmr.bootstrap.system :as system]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.bootstrap.api.routes :as routes]
            [cmr.common.dev.repeat-last-request :as repeat-last-request :refer (repeat-last-request)]
            [cmr.common.dev.util :as d]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.config :as cfg]))

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [web-server (web/create-web-server (transmit-config/bootstrap-port)
                                          (repeat-last-request/wrap-api routes/make-api))
        s (assoc (system/create-system) :web web-server)]
    (alter-var-root #'system
                    (constantly
                      (system/start s))))
  (d/touch-user-clj))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (system/stop s)))))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(info "Custom bootstrap user.clj loaded.")
