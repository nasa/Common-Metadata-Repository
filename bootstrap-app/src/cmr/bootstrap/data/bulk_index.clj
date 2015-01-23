(ns cmr.bootstrap.data.bulk-index
  "Functions to support concurrent bulk indexing."
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
            [clojure.core.async :as ca :refer [go go-loop alts!! <!! >!]]
            [cmr.oracle.connection :as oc]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.transmit.config :as transmit-config]
            [cmr.bootstrap.data.bulk-migration :as bm]))

(def ^:private elastic-http-try-count->wait-before-retry-time
  "A map of of the previous number of tries to communicate with Elasticsearch over http to the amount
  of time to wait before retrying an http request. Will stop retrying if the number of requests
  exceeds what is configured here. Bulk indexing is expected to succeed everytime so this is fairly
  aggressive about retrying and waiting a "
  {1 100
   2 1000
   ;; 10 seconds
   3 (* 10 1000)
   ;; 5 minutes
   4 (* 5 60 1000)})

(defn elastic-retry-handler
  "A custom http retry handler for use with elastic connections"
  [ex try-count http-context]
  (when-let [sleep-time (elastic-http-try-count->wait-before-retry-time try-count)]
    (warn (format "Elasticsearch HTTP Request failed due to %s. %s try. Waiting %s ms before retrying."
                  (.getMessage ex) try-count sleep-time))
    (Thread/sleep sleep-time)
    true))

(defn get-provider-collection-list
  "Get the list of collecitons belonging to the given provider."
  [system provider-id]
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :collection
                :provider-id provider-id}
        collections (db/find-concepts db params)]
    (map :concept-id collections)))

(defn index-granules-for-collection
  "Index the granules for the given collection."
  [system provider-id collection-id]
  (info "Indexing granule data for collection" collection-id)
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :granule
                :provider-id provider-id
                :parent-collection-id collection-id}
        concept-batches (db/find-concepts-in-batches db params (:db-batch-size system))
        num-granules (index/bulk-index {:system (:indexer system)} concept-batches)]
    (info "Indexed" num-granules "granule(s) for provider" provider-id "collection" collection-id)
    num-granules))

(defn- index-granules-for-provider
  "Index the granule data for every collection for a given provider."
  [system provider-id start-index]
  (info "Indexing granule data for provider" provider-id)
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :granule
                :provider-id provider-id}
        concept-batches (db/find-concepts-in-batches db params (:db-batch-size system) start-index)
        num-granules (index/bulk-index {:system (:indexer system)} concept-batches)]
    (info "Indexed" num-granules "granule(s) for provider" provider-id)
    num-granules))

(defn- index-provider-collections
  "Index all the collections concepts for a given provider."
  [system provider-id]
  (let [db (get-in system [:metadata-db :db])
        params {:concept-type :collection
                :provider-id provider-id}
        concept-batches (db/find-concepts-in-batches db params (:db-batch-size system))
        num-collections (index/bulk-index {:system (:indexer system)} concept-batches)]
    (info "Indexed" num-collections "collection(s) for provider" provider-id)
    num-collections))

(defn index-provider
  "Bulk index a provider."
  [system provider-id start-index]
  (info "Indexing provider" provider-id)
  (let [col-count (index-provider-collections system provider-id)
        gran-count (index-granules-for-provider system provider-id start-index)]
    (info "Indexing of provider" provider-id "completed.")
    (format "Indexed %d collections containing %d granules for provider %s"
            col-count
            gran-count
            provider-id)))

;; Background task to handle provider bulk index requests
(defn handle-bulk-index-requests
  "Handle any requests for indexing providers."
  [system]
  (info "Starting background task for monitoring bulk provider indexing channels.")
  (let [channel (:provider-index-channel system)]
    (ca/thread (while true
                 (try ; catch any errors and log them, but don't let the thread die
                   (let [{:keys [provider-id start-index]} (<!! channel)]
                     (index-provider system provider-id start-index))
                   (catch Throwable e
                     (error e (.getMessage e)))))))
  (let [channel (:collection-index-channel system)]
    (ca/thread (while true
                 (try ; catch any errors and log them, but don't let the thread die
                   (let [[provider-id collection-id] (<!! channel)]
                     (index-granules-for-collection system provider-id collection-id))
                   (catch Throwable e
                     (error e (.getMessage e))))))))