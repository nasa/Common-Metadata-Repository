(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.search.system :as system]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.search.api.routes :as routes]
            [cmr.common.dev.repeat-last-request :as repeat-last-request :refer (repeat-last-request)]
            [cmr.common.dev.util :as d]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.config :as cfg])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

(def system nil)

(defn create-system
  []
  (let [web-server (web/create-web-server (transmit-config/search-port)
                                          (repeat-last-request/wrap-api routes/make-api))]
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


(defn tunnel-system
  "Allows tunneling the search to a indexer and elasticsearch running on a different system"
  []
  (cfg/set-config-value! :index-set-port 4005)
  ;; Stop the current system
  (stop)

  (let [updated-sys (-> (create-system)
                        (assoc-in [:search-index :config :port] 9211)
                        system/start)]
    (alter-var-root #'system (constantly updated-sys))))

(comment
  ;; workload can be tunnelled by running these
  ;; ssh -L4005:localhost:3005 cmr-wl-app1.dev.echo.nasa.gov
  ;; ssh -L9211:localhost:9200 cmr-wl-elastic1.dev.echo.nasa.gov
  (tunnel-system)

)

(info "Custom user.clj loaded.")
