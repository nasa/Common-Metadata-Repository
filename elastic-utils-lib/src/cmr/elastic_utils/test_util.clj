(ns cmr.elastic-utils.test-util
  "Common utility functions for tests."
  (:require [cmr.elastic-utils.config :as es-config]
            [cmr.elastic-utils.embedded-elastic-server :as ees]
            [cmr.elastic-utils.connect :as conn]
            [cmr.common.lifecycle :as l]))

(defn elastic-running?
  "Checks if all elastic clusters are running."
  []
  (let [gran-elastic-conn (conn/try-connect (es-config/gran-elastic-config))
        elastic-conn (conn/try-connect (es-config/elastic-config))]
    (and (:ok? (conn/health {:system {:db {:conn gran-elastic-conn}}} :db))
         (:ok? (conn/health {:system {:db {:conn elastic-conn}}} :db)))))

(defn run-elastic-fixture
  "Test fixture that will automatically run elasticsearch if it is not detected as currently
   running."
  [f]
  (if (elastic-running?)
    (f)
    (let [gran-elastic-server (l/start (ees/create-server (es-config/gran-elastic-port)) nil)]
      (try
        (let [elastic-server (l/start (ees/create-server (es-config/elastic-port)) nil)]
          (try
            (f)
            (finally
              (l/stop elastic-server nil))))
        (finally
          (l/stop gran-elastic-server nil))))))
