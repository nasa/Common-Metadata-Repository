(ns user
  (:require
   [clojure.pprint :refer (pprint pp)]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [cmr.common.api.web-server :as web]
   [cmr.common.dev.repeat-last-request :as repeat-last-request
                                       :refer (repeat-last-request)]
   [cmr.common.dev.util :as d]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.search.routes :as routes]
   [cmr.search.system :as system]
   [cmr.transmit.config :as transmit-config])
  (:use
    [alex-and-georges.debug-repl]
    [clojure.test :only [run-all-tests]]
    [clojure.repl]
    [cmr.common.dev.capture-reveal]))

(def system nil)


(defn create-system
  []
  ;; Set the default job start delay to avoid jobs kicking off with tests etc.
  (jobs/set-default-job-start-delay! (* 3 3600))

  ; (tunnel-system)
  (let [web-server (web/create-web-server (transmit-config/search-port)
                                          (repeat-last-request/wrap-api routes/handlers))]
    (assoc (system/create-system) :web web-server)))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system
                  (constantly
                    (system/start (create-system))))
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
