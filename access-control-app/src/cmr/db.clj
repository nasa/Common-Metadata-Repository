(ns cmr.db
  "Entry point for the db (elasticsearch) related operations. Defines a main method that accepts arguments."
  (:require
   [cmr.common.log :refer (info error)]
   [cmr.elastic-utils.search.access-control-index :as ac-index]
   [cmr.elastic-utils.search.es-index :as search-index]
   [cmr.common.lifecycle :as l])
  (:gen-class))

(defn migrate
  []
  (let [non-gran-elastic-store (l/start (search-index/create-elastic-search-index cmr.elastic-utils.config/non-gran-elastic-config) nil)
        gran-elastic-store (l/start (search-index/create-elastic-search-index cmr.elastic-utils.config/gran-elastic-config) nil)]
    (ac-index/create-index-or-update-mappings non-gran-elastic-store)
    (ac-index/create-index-or-update-mappings gran-elastic-store)))

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
