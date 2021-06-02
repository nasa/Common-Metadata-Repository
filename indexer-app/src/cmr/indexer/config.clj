(ns cmr.indexer.config
  (:require
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.message-queue.config :as rmq-conf]))

;; index name and config for storing index-set requests
;; index the request after creating all of the requested indices successfully
;; foot print of this index will remain small
(def idx-cfg-for-index-sets
  {:index-name "index-sets"
   :settings {"index" {"number_of_shards" 1
                       "number_of_replicas"  1
                       "refresh_interval" "30s"}}
   :mapping {"dynamic"  "strict"
             "_source"  {"enabled" true}
             :properties {:index-set-id  {:type "keyword" :norms false :index_options "docs"}
                          :index-set-name {:type "keyword" :norms false :index_options "docs"}
                          :index-set-name-lowercase {:type "keyword" :norms false :index_options "docs"}
                          :index-set-request {:type "keyword" :norms false :index_options "docs"}}}})

(defconfig index-queue-name
  "The queue containing ingest events for the indexer"
  {:default "cmr_index.queue"})

(defconfig index-queue-listener-count
  "Number of worker threads to use for the queue listener"
  {:default 5
   :type Long})

(defconfig all-revisions-index-queue-name
  "The queue containing ingest events for the indexer all revisions index. We use a
  separate index here because it allows independent retries of indexing failures. If
  we used a single queue then if either indexing operation (primary or all revisions)
  failed then we would have to retry both. Also, having separate queues facilitates
  bootstrapping the all revisions index."
  {:default "cmr_index.all_revisions_queue"})

(defconfig all-revisions-index-queue-listener-count
  "Number of worker threads to use for the queue listener for the all revisions queue"
  {:default 2
   :type Long})

(defconfig deleted-granule-index-queue-name
  "The queue containing ingest events for the indexer deleted-granules index. We use a
  separate index here because it allows independent retries of indexing failures. If
  we used a single queue then if either indexing operation (primary or all revisions)
  failed then we would have to retry both."
  {:default "cmr_index.deleted_granules_queue"})

(defconfig deleted-granules-index-queue-listener-count
  "Number of worker threads to use for the queue listener for the deleted-granules queue"
  {:default 2
   :type Long})

(defconfig provider-queue-name
  "The queue containing provider events like 'index provider collections'."
  {:default "cmr_provider.queue"})

(defconfig provider-queue-listener-count
  "Number of worker threads to use for the queue listener for the provider queue"
  {:default 2
   :type Long})

(defconfig ingest-exchange-name
  "The ingest exchange to which ingest event messages are published."
  {:default "cmr_ingest.exchange"})

(defconfig provider-exchange-name
  "The ingest exchange to which provider change and non-ingest messages are published."
  {:default "cmr_ingest_provider.exchange"})

(defconfig deleted-concept-revision-exchange-name
  "An exchange that will have messages passed to it whenever a concept revision is removed from
  metadata db. This was originally only intended for collections and it is messy to change the
  exchange name after it is in use, so we keep the old name even though it is no longer correct."
  {:default "cmr_deleted_collection_revision.exchange"})

(defconfig deleted-granule-exchange-name
  "An exchange that will have messages passed to it whenever a granule revision is removed
  from metadata db."
  {:default "cmr_deleted_granule.exchange"})

(defn queue-config
  "Returns the rabbit mq configuration for the indexer application."
  []
  (assoc (rmq-conf/default-config)
         :queues [(index-queue-name)
                  (all-revisions-index-queue-name)
                  (deleted-granule-index-queue-name)
                  (provider-queue-name)]
         :exchanges [(ingest-exchange-name)
                     (deleted-concept-revision-exchange-name)
                     (deleted-granule-exchange-name)
                     (provider-exchange-name)]
         :queues-to-exchanges
         {(index-queue-name) [(ingest-exchange-name)]
          (provider-queue-name) [(provider-exchange-name)]
          (deleted-granule-index-queue-name) [(deleted-granule-exchange-name)]
          ;; The all revisions index  queue will be bound to both the ingest exchange and the
          ;; deleted concept revision exchange
          (all-revisions-index-queue-name) [(ingest-exchange-name)
                                            (deleted-concept-revision-exchange-name)]}))

(defconfig indexer-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig ongoing-days
  "Number of days where collection end-date counts as ongoing"
  {:default 30
   :type Long})
