(ns cmr.bootstrap.data.util
  "Functions for working with CMR data used by various bootstrap
  processes."
  (:require [cmr.bootstrap.embedded-system-helper :as helper]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.indexer.services.index-service :as index-service]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.provider-service :as provider-service]))

(defn save-and-index-concept
  "Saves the concept to the Metadata DB and indexes it using the indexer"
  [system concept]
  ;; This is going to copy the item to metadata db. If it was never added to MDB in the first place
  ;; and was deleted in Catalog REST in the mean time Ingest would return a 404 from the delete
  ;; and Catalog REST would ignore it. This would end up saving the item in Metadata DB making them
  ;; out of sync. The delete processing should happen after this step and put it back in sync.
  (let [mdb-context {:system (helper/get-metadata-db system)}
        indexer-context {:system (helper/get-indexer system)}
        {:keys [concept-id revision-id]} concept]
    (try
      (concept-service/save-concept mdb-context concept)
      (index-service/index-concept indexer-context concept-id revision-id
                                   {:ignore-conflict? true :all-revisions-index? false})
      (index-service/index-concept indexer-context concept-id revision-id
                                   {:ignore-conflict? true :all-revisions-index? true})
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (= (:type data) :conflict)
            (warn (format "Ignoring conflict saving revision for %s %s: %s"
                          concept-id revision-id (pr-str (:errors data))))
            (error e (format "Error saving or indexing concept %s with revision %s. Message: %s"
                             concept-id revision-id (.getMessage e))))))
      (catch Throwable e
        (error e (format "Error saving or indexing concept %s with revision %s. Message: %s"
                         concept-id revision-id (.getMessage e)))))))

(defn create-tombstone-and-unindex-concept
  "Creates a tombstone with the given concept id and revision id and unindexes the concept."
  [system concept-id revision-id]
  (try
    (let [mdb-context {:system (helper/get-metadata-db system)}
          indexer-context {:system (helper/get-indexer system)}]
      (concept-service/delete-concept mdb-context concept-id revision-id nil)
      (index-service/delete-concept indexer-context concept-id revision-id true))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (if (= (:type data) :conflict)
          (warn (format "Ignoring conflict creating tombstone for %s %s: %s"
                        concept-id revision-id (pr-str (:errors data))))
          (error e (format "Error deleting or unindexing concept %s with revision %s. Message: %s"
                           concept-id revision-id (.getMessage e))))))
    (catch Throwable e
      (error e (format "Error deleting or unindexing concept %s with revision %s. Message: %s"
                       concept-id revision-id (.getMessage e))))))
