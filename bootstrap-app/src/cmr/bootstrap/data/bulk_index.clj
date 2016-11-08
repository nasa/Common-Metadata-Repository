(ns cmr.bootstrap.data.bulk-index
  "Functions to support concurrent bulk indexing."
  (:require
    [cheshire.core :as json]
    [clj-http.client :as client]
    [clj-time.coerce :as time-coerce]
    [clojure.core.async :as ca :refer [go go-loop alts!! <!! >!]]
    [clojure.java.jdbc :as j]
    [clojure.string :as str]
    [cmr.access-control.data.access-control-index :as access-control-index]
    [cmr.access-control.data.bulk-index :as ac-bulk-index]
    [cmr.bootstrap.data.bulk-migration :as bm]
    [cmr.bootstrap.embedded-system-helper :as helper]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.indexer.services.index-service :as index]
    [cmr.metadata-db.data.concepts :as db]
    [cmr.metadata-db.data.providers :as p]
    [cmr.metadata-db.services.provider-service :as provider-service]
    [cmr.oracle.connection :as oc]
    [cmr.transmit.config :as transmit-config]))


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
  (let [db (helper/get-metadata-db-db system)
        provider (p/get-provider db provider-id)
        params {:concept-type :collection}
        collections (db/find-concepts db [provider] params)]
    (map :concept-id collections)))

(defn get-provider-by-id
  "Returns the metadata db provider that matches the given provider id."
  [context provider-id]
  (let [db (helper/get-metadata-db-db (:system context))]
    (p/get-provider db provider-id)))

(defn get-collection
  "Get specified collection from cmr."
  [context provider collection-id]
  (db/get-concept (helper/get-metadata-db-db (:system context)) :collection provider collection-id))

(defn index-granules-for-collection
  "Index the granules for the given collection."
  [system provider-id collection-id {:keys [target-index-key completion-message]}]
  (info "Indexing granule data for collection" collection-id)
  (let [db (helper/get-metadata-db-db system)
        provider (p/get-provider db provider-id)
        params {:concept-type :granule
                :provider-id provider-id
                :parent-collection-id collection-id}
        concept-batches (db/find-concepts-in-batches db provider params (:db-batch-size system))
        num-granules (index/bulk-index {:system (helper/get-indexer system)}
                                       concept-batches
                                       {:target-index-key target-index-key})]
    (info "Indexed" num-granules "granule(s) for provider" provider-id "collection" collection-id)
    (when completion-message
      (info completion-message))
    num-granules))

(defn- index-granules-for-provider
  "Index the granule data for every collection for a given provider."
  [system provider start-index]
  (info "Indexing granule data for provider" (:provider-id provider))
  (let [db (helper/get-metadata-db-db system)
        {:keys [provider-id]} provider
        params {:concept-type :granule
                :provider-id provider-id}
        concept-batches (db/find-concepts-in-batches db provider params (:db-batch-size system) start-index)
        num-granules (index/bulk-index {:system (helper/get-indexer system)} concept-batches {})]
    (info "Indexed" num-granules "granule(s) for provider" provider-id)
    num-granules))

(defn- index-provider-collections
  "Index all the collections concepts for a given provider."
  [system provider]
  (let [db (helper/get-metadata-db-db system)
        {:keys [provider-id]} provider
        params {:concept-type :collection
                :provider-id provider-id}
        concept-batches (db/find-concepts-in-batches db provider params (:db-batch-size system))
        num-collections (index/bulk-index {:system (helper/get-indexer system)} concept-batches {})]
    (info "Indexed" num-collections "collection(s) for provider" provider-id)
    num-collections))

(defn index-provider
  "Bulk index a provider."
  [system provider-id start-index]
  (info "Indexing provider" provider-id)
  (let [db (helper/get-metadata-db-db system)
        {:keys [provider-id] :as provider} (p/get-provider db provider-id)
        col-count (index-provider-collections system provider)
        gran-count (index-granules-for-provider system provider start-index)]
    (info "Indexing of provider" provider-id "completed.")
    (format "Indexed %d collections containing %d granules for provider %s"
            col-count
            gran-count
            provider-id)))

(defn- index-access-control-concepts
  "Bulk index ACLs or acces groups"
  [system concept-batches]
  (info "Indexing concepts")
  (let [count (ac-bulk-index/bulk-index {:system (helper/get-indexer system)} concept-batches)]
    (info "Indexed" count "concepts")
    count))

(defn- index-concepts
  "Bulk index the given concepts using the indexer-app"
  [system concept-batches]
  (info "Indexing concepts")
  (let [count (index/bulk-index {:system (helper/get-indexer system)} concept-batches {})]
    (info "Indexed" count "concepts")
    count))

(defn- fetch-and-index-new-concepts
  "Get batches of concepts for a given provider/concept type that have a revision-date
  newer than the given date time and then index them."
  [system provider concept-type date-time]
  (let [db (helper/get-metadata-db-db system)
        provider-id (:provider-id provider)
        params {:concept-type concept-type
                :provider-id provider-id
                :revision-date {:comparator `> :value (time-coerce/to-sql-time date-time)}}
        concept-batches (db/find-concepts-in-batches
                          db provider params (:db-batch-size system))
        num-concepts (if (contains? #{:acl :access-group} concept-type)
                       (index-access-control-concepts system concept-batches)
                       (index-concepts system concept-batches))]
    (info "Indexed" num-concepts (str (name concept-type) "(s) for provider") provider-id
     "with revision-date later than " date-time)
    num-concepts))

(defn index-data-later-than-date-time
  "Index all concept revisions created later than the given date-time."
  [system date-time]
  (info "Indexing concepts.")
  (let [db (helper/get-metadata-db-db system)
        providers (p/get-providers db)
        non-system-concept-count (reduce + (for [provider providers
                                                 concept-type [:collection :granule :service]]
                                             (fetch-and-index-new-concepts
                                                 system provider concept-type date-time)))
        system-concept-count (reduce + (for [concept-type [:tag :acl :access-group]]
                                         (fetch-and-index-new-concepts
                                           system {:provider-id "CMR"} concept-type date-time)))]
    (info "Indexing concepts with revision-date later than" date-time "completed.")
    (format "Indexed %d provider concepts and %d system concepts."
            non-system-concept-count
            system-concept-count)))

;; Background task to handle bulk index requests
(defn handle-bulk-index-requests
  "Handle any requests for bulk indexing. We use separate channels for each type of
  indexing request instead of a single channel to simplify the dispatch logic.
  Since we know at the time a request is made what function should be used, there
  is no point in implementing separate code to determine what funciton to call
  when an item comes off the channel."
  [system]
  (info "Starting background task for monitoring bulk provider indexing channels.")
  (let [channel (:data-index-channel system)]
    (ca/thread (while true
                 (try ; catch any errors and log them, but don't let the thread die
                   (let [{:keys [date-time]} (<!! channel)]
                     (index-data-later-than-date-time system date-time))
                   (catch Throwable e
                     (error e (.getMessage e)))))))
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
                   (let [{:keys [provider-id collection-id] :as options} (<!! channel)]
                     (index-granules-for-collection system provider-id collection-id options))
                   (catch Throwable e
                     (error e (.getMessage e))))))))
