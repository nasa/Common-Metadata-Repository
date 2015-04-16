(ns cmr.db
  "Entry point for the db (elasticsearch) related operations. Defines a main method that accepts arguments."
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.cubby.data.elastic-cache-store :as elastic-cache-store]
            [cmr.elastic-utils.config :as es-config]
            [cmr.common.lifecycle :as l])
  (:gen-class))

(defn migrate
  []
  (let [elastic-store (-> (es-config/elastic-config)
                          elastic-cache-store/create-elastic-cache-store
                          (l/start nil))]
    (elastic-cache-store/create-index-or-update-mappings elastic-store)))

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
