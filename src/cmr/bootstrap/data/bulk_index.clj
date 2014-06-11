(ns cmr.bootstrap.data.bulk-index
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.indexer.services.index-service :as index]
            [cmr.metadata-db.data.concepts :as db]
            [clojure.java.jdbc :as j]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [sqlingvo.core :as sql :refer [sql select insert from where with order-by desc delete as]]
            [sqlingvo.vendor :as v]
            [cmr.metadata-db.data.oracle.sql-utils :as su]
            [clojure.core.async :as ca :refer [thread alts!! <!!]]
            [cmr.oracle.connection :as oc]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.config :as config]
            [cmr.bootstrap.data.bulk-migration :as bm]))

(def BATCH-SIZE (config/config-value-fn :bulk-index-batch-size 1000 #(Long. %)))

(defn- index-granules-for-collection
  "Index the granules for the given collection."
  [system provider-id collection-id dataset-record-id]
  )

(defn- index-granules-for-provider
  "Index the granule data for every collection for a given provider."
  [system provider-id]
  (info "Indexing granule data for provider" provider-id)
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :granule
                :provider-id provider-id}
        concepts (db/find-concepts db params)]
    (index/bulk-index {:system (:indexer system)} concepts (BATCH-SIZE))))

(defn- index-provider-collections
  "Index all the collections concepts for a given provider."
  [system provider-id]
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :collection
                :provider-id provider-id}
        concepts (db/find-concepts db params)]
    (index/bulk-index {:system (:indexer system)} concepts (BATCH-SIZE))))


(defn- delete-provider
  "Remove all records from elastic related to the given provider including indexes."
  [system provider-id]
  ;; FIXME - Implement this when done with bulk migration
  )

(defn- index-provider
  "Bulk index a provider."
  [system provider-id]
  (delete-provider system provider-id)
  (index-provider-collections system provider-id)
  (index-granules-for-provider system provider-id)
  (info "Indexing of provider" provider-id "completed."))

(comment


  (let [system (get-in user/system [:apps :bootstrap])
        concepts (index-provider system "FIX_PROV1")

        ]
    (println concepts))


  )


;; Background task to handle requests
(defn handle-bulk-index-requests
  "Handle any requests for copying data from echo catalog rest to metadata db."
  [system]
  (info "Starting background task for monitoring bulk migration channels.")
  (let [channels ((juxt :provider-index-channel) system)] ; add other channels as needed
    (thread (while true
              (try ; catch any errors and log them, but don't let the thread die
                (let [[v ch] (alts!! channels)]
                  (cond
                    ;; add other channels as needed
                    (= (:provider-index-channel system) ch)
                    (do
                      (info "Processing provider" v)
                      (index-provider system v))

                    :else
                    (error (format "Received message [%s] on channel [%s] that is unrecognized"
                                   v ch))))
                (catch Throwable e
                  (error e (.getMessage e))))))))

