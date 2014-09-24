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

(defn tunnel-system-wl
  "Allows tunneling the search to a indexer and elasticsearch running on a different system"
  []
  ;; workload can be tunnelled by running these
  ;; ssh -L4005:localhost:3005 cmr-wl-app1.dev.echo.nasa.gov
  ;; ssh -L9211:localhost:9200 cmr-wl-elastic1.dev.echo.nasa.gov
  ;; ssh -L1557:dbrac1node1.dev.echo.nasa.gov:1521 wlapp3.dev.echo.nasa.gov
  (cfg/set-config-value! :elastic-port 9211)
  (cfg/set-config-value! :index-set-port 4005)

  (cfg/set-config-value! :echo-rest-host "api-wkld.echo.nasa.gov")
  (cfg/set-config-value! :echo-rest-port 80)
  (cfg/set-config-value! :echo-rest-context "/echo-rest")

  (cfg/set-config-value! :db-url "thin:@localhost:1557:OPSDB1")

  ;; Set the following but do not commit them
  (cfg/set-config-value! :echo-system-token "XXXXX")
  (cfg/set-config-value! :metadata-db-password "XXXXX"))

(defn tunnel-system-uat
  "Allows tunneling the search to a indexer and elasticsearch running on a different system"
  []
  ;; UAT can be tunnelled by running these
  ;; ssh -L4005:localhost:3005 cmr-uat-app1.dev.echo.nasa.gov
  ;; ssh -L9213:localhost:9200 cmr-uat-elastic1.dev.echo.nasa.gov
  ;; ssh -L1559:dbrac1node1.dev.echo.nasa.gov:1521 ptkernel4.dev.echo.nasa.gov
  (cfg/set-config-value! :elastic-port 9213)
  (cfg/set-config-value! :index-set-port 4005)

  (cfg/set-config-value! :echo-rest-protocol "https")
  (cfg/set-config-value! :echo-rest-host "api-test.echo.nasa.gov")
  (cfg/set-config-value! :echo-rest-port 443)
  (cfg/set-config-value! :echo-rest-context "/echo-rest")

  (cfg/set-config-value! :db-url "thin:@localhost:1559/ptdb.dev.echo.nasa.gov")

  ;; Set the following but do not commit them
  (cfg/set-config-value! :echo-system-token "XXXXX")
  (cfg/set-config-value! :metadata-db-password "XXXXX"))

(defn create-system
  []
  ;; Uncomment this to tunnel to another system for testing.
  ; (tunnel-system-uat)
  ; (tunnel-system-wl)

  ;; Set the default job start delay to avoid jobs kicking off with tests etc.
  (cfg/set-config-value! :default-job-start-delay (str (* 3 3600)))

  ; (tunnel-system)
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



(info "Custom user.clj loaded.")
