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
            [clojure.core.async :as ca :refer [merge go go-loop thread alts!! <!! >!]]
            [cmr.oracle.connection :as oc]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.transmit.config :as transmit-config]
            [cmr.bootstrap.data.bulk-migration :as bm]))

(defn- get-provider-collection-list
  "Get the list of collecitons belonging to the given provider."
  [system provider-id]
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :collection
                :provider-id provider-id}
        collections (db/find-concepts db params)]
    (map :concept-id collections)))

(defn- index-granules-for-collection
  "Index the granules for the given collection."
  [system provider-id collection-id]
  (info (str "Indexing granule data for collection " collection-id))
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :granule
                :provider-id provider-id
                :parent-collection-id collection-id}
        concepts (db/find-concepts-in-batches db params (:db-batch-size system))]
    (index/bulk-index {:system (:indexer system)} concepts (:bulk-index-batch-size system))))

(defn- index-granules-for-provider
  "Index the granule data for every collection for a given provider."
  [system provider-id]
  (info "Indexing granule data for provider" provider-id)
  (let[channel (:collection-index-channel system)
       go-channels (merge (doall (for [collection-id (get-provider-collection-list system provider-id)]
                                   (go (>! channel {:provider-id provider-id
                                                    :collection-id collection-id})))))]
    (<!! go-channels))
  (info "Indexing of granule data for provider" provider-id "completed."))

(defn- index-provider-collections
  "Index all the collections concepts for a given provider."
  [system provider-id]
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :collection
                :provider-id provider-id}
        concepts (db/find-concepts db params)]
    (index/bulk-index {:system (:indexer system)} concepts (:bulk-index-batch-size system))))


(defn- unindex-provider
  "Remove all records from elastic related to the given provider including indexes."
  [system provider-id]
  ;; FIXME - Implement this when done with bulk migration
  )

(defn- index-provider
  "Bulk index a provider."
  [system provider-id]
  (unindex-provider system provider-id)
  (index-provider-collections system provider-id)
  (index-granules-for-provider system provider-id)
  (info "Indexing of provider" provider-id "completed."))

;; Background task to handle requests
(defn handle-bulk-index-requests
  "Handle any requests for copying data from echo catalog rest to metadata db."
  [system]
  (info "Starting background task for monitoring bulk migration channels.")
  (let [channels ((juxt :provider-index-channel :collection-index-channel) system)] ; add other channels as needed
    (thread (while true
              (try ; catch any errors and log them, but don't let the thread die
                (let [[v ch] (alts!! channels)]
                  (cond
                    ;; add other channels as needed
                    (= (:provider-index-channel system) ch)
                    (do
                      (info "Processing provider" v)
                      (index-provider system v))

                    (= (:collection-index-channel system) ch)
                    (do
                      (info "Processing collection" v)
                      (let [{:keys [provider-id collection-id]} v]
                        (index-granules-for-collection system provider-id collection-id)))

                    :else
                    (error (format "Received message [%s] on channel [%s] that is unrecognized"
                                   v ch))))
                (catch Throwable e
                  (error e (.getMessage e))))))))

