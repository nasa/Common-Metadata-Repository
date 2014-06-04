(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.search.system :as system]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.search.api.routes :as routes]
            [cmr.common.dev.repeat-last-request :as repeat-last-request :refer (repeat-last-request)]
            [cmr.common.dev.util :as d]
            [cmr.transmit.config :as transmit-config])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [web-server (web/create-web-server (transmit-config/search-port)
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

(info "Custom user.clj loaded.")
