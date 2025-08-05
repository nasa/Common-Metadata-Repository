(ns cmr.elastic-utils.test-util
  "Common utility functions for tests."
  (:require [cmr.elastic-utils.config :as config]
            [cmr.elastic-utils.embedded-elastic-server :as ees]
            [cmr.elastic-utils.connect :as conn]
            [cmr.common.lifecycle :as l]))

;; TODO 10636 need to change this to check both elastic clusters
(defn elastic-running?
  "Checks if elastic is running."
  []
  (let [c (conn/try-connect (config/gran-elastic-config))]
    (:ok? (conn/health {:system {:db {:conn c}}} :db))))

;; TODO 10636 need to change this
(defn run-elastic-fixture
  "Test fixture that will automatically run elasticsearch if it is not detected as currently
   running."
  [f]
  (if (elastic-running?)
    (f)
    (let [port (config/elastic-port)
          server (l/start (ees/create-server port) nil)]
      (try
        (f)
        (finally
          (l/stop server nil))))))
