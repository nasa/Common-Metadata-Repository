(ns cmr.db
  "Entry point for the db (elasticsearch) related operations. Defines a main method that accepts arguments."
  (:require
   [cmr.common.log :refer (info error)]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.search.access-control-index :as ac-index]
   [cmr.elastic-utils.search.es-index :as search-index]
   [cmr.common.lifecycle :as l])
  (:gen-class))

(defn migrate
  []
  (let [elastic-store (l/start (search-index/create-elastic-search-index es-config/elastic-config) nil)]
    (ac-index/create-index-or-update-mappings elastic-store)))

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
