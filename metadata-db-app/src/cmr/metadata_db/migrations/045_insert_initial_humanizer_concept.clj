(ns cmr.metadata-db.migrations.045-insert-initial-humanizer-concept
  (:require [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.data.oracle.concepts]
            [cmr.metadata-db.data.concepts :as c]
            [cheshire.core :as json]
            [cmr.common-app.test.sample-humanizer :as sh]))

(defn- insert-humanizer
  []
  (let [provider {:provider-id "CMR"
                  :short-name "CMR"
                  :system-level? true
                  :cmr-only true
                  :small false}
        concept {:concept-type :humanizer
                 :native-id "humanizer"
                 :metadata (json/generate-string sh/sample-humanizers)
                 :user-id "migration"
                 :format "application/json"
                 :provider-id "CMR"
                 :concept-id "H12345-CMR"
                 :revision-id 1
                 :deleted false}]
    (c/save-concept (config/db) provider concept)))

(defn up
  "Migrates the database up to version 45."
  []
  (println "cmr.metadata-db.migrations.045-insert-initial-humanizer-concept up...")
  (h/sql "DELETE cmr_humanizers")
  (insert-humanizer))

(defn down
  "Migrates the database down from version 45."
  []
  (println "cmr.metadata-db.migrations.045-insert-initial-humanizer-concept down...")
  (h/sql "DELETE cmr_humanizers"))
