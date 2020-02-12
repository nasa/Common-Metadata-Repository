(ns cmr.db
  "Entry point for the db (elasticsearch) related operations. Defines a main method that accepts arguments."
  (:require
   [cmr.access-control.data.access-control-index :as ac-index]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.services.search.elastic-search-index :as search-index]
   [cmr.common.lifecycle :as l]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.elastic-utils.config :as es-config])
  (:gen-class))

(defn- migrate-es
  "Create indexes and update mappings for the given ES config"
  [config]
  (let [elastic-store (l/start (search-index/create-elastic-search-index config) nil)]
    (ac-index/create-index-or-update-mappings elastic-store)))

(defn migrate
  []
  (when (= :both (common-config/index-es-engine-key))
    (migrate-es (es-config/elastic-config))
    (migrate-es (es-config/new-elastic-config)))
  (when (= :old (common-config/index-es-engine-key))
    (migrate-es (es-config/elastic-config)))
  (when (= :new (common-config/index-es-engine-key))
    (migrate-es (es-config/new-elastic-config))))

(defn -main
  "Execute the given database operation specified by input arguments."
  [& args]
  (info "Running " args)
  (let [op (first args)]
    (try
      (cond
        (= "migrate" op)
        (migrate)

        :else
        (info "Unsupported operation: " op))

      (catch Throwable e
        (error e (.getMessage e))
        (System/exit 1))))

  (System/exit 0))
