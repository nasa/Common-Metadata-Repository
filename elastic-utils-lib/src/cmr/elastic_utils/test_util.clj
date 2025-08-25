(ns cmr.elastic-utils.test-util
  "Common utility functions for tests."
  (:require [cmr.elastic-utils.config :as config]
            [cmr.elastic-utils.embedded-elastic-server :as ees]
            [cmr.elastic-utils.connect :as conn]
            [cmr.common.lifecycle :as l]))

(defn elastic-running?
  "Checks if all elastic clusters running."
  []
  (let [gran-elastic-conn (conn/try-connect (config/gran-elastic-config))
        elastic-conn (conn/try-connect (config/elastic-config))]
    (:ok? (conn/health {:system {:db {:conn gran-elastic-conn}}} :db))
    (:ok? (conn/health {:system {:db {:conn elastic-conn}}} :db))))


(defn run-elastic-fixture
  "Test fixture that will automatically run elasticsearch if it is not detected as currently
   running."
  [f]
  (if (elastic-running?)
    (f)
    (let [gran-elastic-server (l/start (ees/create-server (config/gran-elastic-port)) nil)
          elastic-server (l/start (ees/create-server (config/elastic-port)) nil)]
      (try
        (f)
        (finally
          (do
            (l/stop gran-elastic-server nil)
            (l/stop elastic-server nil)))))))
