(ns cmr.elastic-utils.connect
  "Provide functions to invoke elasticsearch"
  (:require [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.admin :as admin]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.health-helper :as hh]
            [clj-http.conn-mgr :as conn-mgr]
            [cmr.common.api.web-server :as web-server]
            [clojurewerkz.elastisch.rest.admin :as admin]))

(def ELASTIC_CONNECTION_TIMOUT
  "The number of milliseconds to wait before timeing out a connection attempt to elasticsearch.
  Currently set to 5 minutes."
  (* 5 60 1000))

(defn- connect-with-config
  "Connects to ES with the given config"
  [config]
  (let [{:keys [host port retry-handler]} config
        http-options {:conn-mgr (conn-mgr/make-reusable-conn-manager
                                  {;; Maximum number of threads that will be used for connecting.
                                   ;; Very important that this matches the maximum number of threads that will be running
                                   :threads (web-server/MAX_THREADS)
                                   ;; Maximum number of simultaneous connections per host
                                   ;; There's usually one elasticsearch hostname and we always connect
                                   ;; to the same host so it makes sense to make this larger.
                                   :default-per-route (web-server/MAX_THREADS)
                                   ;; This is the length of time in _seconds_ that a connection will
                                   ;; be left open for reuse. The default is 5 seconds which is way
                                   ;; too short.
                                   :timeout 120})
                      :retry-handler retry-handler
                      :socket-timeout ELASTIC_CONNECTION_TIMOUT
                      :conn-timeout ELASTIC_CONNECTION_TIMOUT}]

    (info (format "Connecting to single ES on %s %d using retry-handler %s" host port retry-handler))
    (esr/connect (str "http://" host ":" port) http-options)))

(defn try-connect
  [config]
  (try
    (connect-with-config config)
    (catch Exception e
      (errors/internal-error!
        (format "Unable to connect to elasticsearch at: %s. with %s" config e)))))

(defn wait-for-healthy-elastic
  "Waits for the elasticsearch cluster health to reach yellow. Pass in a elasticsearch store that
  has a :conn key with the elastisch connection"
  [elastic-store]
  (when (:timed_out (admin/cluster-health
                     (:conn elastic-store) {:wait_for_status "yellow" :timeout "3s"}))
    (errors/internal-error! "Timed out waiting for elasticsearch to reach a healthy state")))

(defn- get-elastic-health
  "Returns the elastic health by calling elasticsearch cluster health api"
  [conn]
  (try
    (admin/cluster-health conn {:wait_for_status "yellow"
                                :timeout (str (hh/health-check-timeout-seconds) "s")})
    (catch Exception e
      (format "Unable to get elasticsearch cluster health, caught exception: %s"
              (.getMessage e)))))

(defn health-fn
  "Returns the health state of elasticsearch."
  [context elastic-key-in-context]
  (let [conn (get-in context [:system elastic-key-in-context :conn])
        health-detail (get-elastic-health conn)
        status (:status health-detail)]
    (if (some #{status} ["green" "yellow"])
      {:ok? true}
      {:ok? false
       :problem health-detail})))

(defn health
  "Returns the elasticsearch health with timeout handling."
  [context elastic-key-in-context]
  (let [;; We add 1 second to allow get-elastic-health operation to timeout first
        timeout-ms (* 1000 (inc (hh/health-check-timeout-seconds)))]
    (hh/get-health #(health-fn context elastic-key-in-context) timeout-ms)))
