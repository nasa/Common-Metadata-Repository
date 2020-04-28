(ns cmr.elastic-utils.test-util
  (:require [cmr.elastic-utils.config :as config]
            [cmr.elastic-utils.embedded-elastic-server :as ees]
            [cmr.elastic-utils.connect :as conn]
            [cmr.common.lifecycle :as l]))

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
    (let [port (config/elastic-port)
          server (l/start (ees/create-server port) nil)]
      (try
        (f)
        (finally
          (l/stop server nil))))))
