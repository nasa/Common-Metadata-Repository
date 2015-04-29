(ns cmr.elastic-utils.test-util
  (:require [cmr.elastic-utils.config :as config]
            [cmr.elastic-utils.embedded-elastic-server :as ees]
            [cmr.elastic-utils.connect :as conn]
            [cmr.common.lifecycle :as l]
            [clojurewerkz.elastisch.rest.admin :as admin]
            [cmr.common.services.errors :as errors]))

(def IN_MEMORY_ELASTIC_PORT
  "A constant defining the common in memory elastic port to use for local testing. This avoids having
  to repeat the same information in multiple places and keeps the local port we use consistent."
  9206)

(defn elastic-running?
  "Checks if elastic is running."
  []
  (let [c (conn/try-connect (config/elastic-config))]
    (:ok? (conn/health {:system {:db {:conn c}}} :db))))

(defn run-elastic-fixture
  "Test fixture that will automatically run elasticsearch if it's not detected as currently running."
  [f]
  (if (elastic-running?)
    (f)
    (let [{:keys [port]} (config/elastic-config)
          server (l/start (ees/create-server port (+ port 10) "es_data/fixture") nil)]
      (try
        (f)
        (finally
          (l/stop server nil))))))

(defn wait-for-healthy-elastic
  "Waits for the elasticsearch cluster health to reach yellow. Pass in a elasticsearch store that
  has a :conn key with the elastisch connection"
  [elastic-store]
  (when (:timed_out (admin/cluster-health (:conn elastic-store) :wait_for_status "yellow" :timeout "3s"))
    (errors/internal-error! "Timed out waiting for elasticsearch to reach a healthy state")))