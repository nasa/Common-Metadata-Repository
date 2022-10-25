(ns cmr.bootstrap.data.bulk-index
  "Functions to support concurrent bulk indexing."
  (:require
    [cheshire.core :as json]
    [clj-http.client :as client]
    [clj-time.coerce :as time-coerce]
    [clojure.core.async :as ca :refer [<!!]]
    [clojure.java.jdbc :as j]
    [clojure.string :as str]
    [cmr.access-control.data.access-control-index :as access-control-index]
    [cmr.access-control.data.bulk-index :as ac-bulk-index]
    [cmr.bootstrap.data.bulk-migration :as bm]
    [cmr.bootstrap.embedded-system-helper :as helper]
    [cmr.common.concepts :as cc]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.util :as util]
    [cmr.indexer.data.elasticsearch :as es]
    [cmr.indexer.data.index-set :as index-set]
    [cmr.indexer.services.index-service :as index]
    [cmr.indexer.services.index-set-service :as index-set-service]
    [cmr.metadata-db.data.concepts :as db]
    [cmr.metadata-db.data.providers :as p]
    [cmr.metadata-db.services.provider-service :as provider-service]
    [cmr.oracle.connection :as oc]
    [cmr.transmit.config :as transmit-config]))

(def ^:private system-concept-provider
  "Provider name for indexing system concepts"
  "CMR")

(def ^:private elastic-http-try-count->wait-before-retry-time
  "A map of of the previous number of tries to communicate with Elasticsearch over http to the amount
  of time to wait before retrying an http request. Will stop retrying if the number of requests
  exceeds what is configured here. Bulk indexing is expected to succeed everytime so this is fairly
  aggressive about retrying and waiting."
  {1 100
   2 1000
   ;; 10 seconds
   3 (* 10 1000)
   ;; 5 minutes
   4 (* 5 60 1000)})

(def ^:private system-concept-types
  "The list of system concept types"
  [:tag :acl :access-group :variable :service :tool :subscription])

(def ^:private misc-concept-types
  "The list of miscellaneous concept types that are provider based but saved in a single system table"
  [:variable :service :tool :subscription])

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

(defn get-collection
  "Get specified collection from cmr."
  [context provider collection-id]
  (db/get-concept (helper/get-metadata-db-db (:system context)) :collection provider collection-id))

(defn index-granules-for-collection
  "Index the granules for the given collection."
  [system provider-id collection-id {:keys [start-index target-index-key completion-message rebalancing-collection?]}]
  (info "Indexing granule data for collection" collection-id)
  (let [db (helper/get-metadata-db-db system)
        provider (p/get-provider db provider-id)
        params {:concept-type :granule
                :provider-id provider-id
                :parent-collection-id collection-id}
        start-index (or start-index 0)
        concept-batches (db/find-concepts-in-batches db provider params (:db-batch-size system) start-index)
        num-granules (index/bulk-index {:system (helper/get-indexer system)}
                                       concept-batches
                                       {:target-index-key target-index-key})]
    (info "Indexed" num-granules "granule(s) for provider" provider-id "collection" collection-id)
    (when completion-message
      (info completion-message))
    (when rebalancing-collection?
      (index-set-service/update-collection-rebalancing-status
       {:system (helper/get-indexer system)}
       index-set/index-set-id
       collection-id
       "COMPLETE"))
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

(defn- bulk-index-concept-batches
  "Bulk index the given concept batches in both regular index and all revisions index."
  [system concept-batches]
  (let [indexer-context {:system (helper/get-indexer system)}]
    (index/bulk-index indexer-context concept-batches {:all-revisions-index? true})
    (index/bulk-index indexer-context concept-batches {})))

(defn- index-concepts-by-provider
  "Bulk index concepts for the given provider and concept-type."
  [system concept-type provider]
  (info (format "Indexing %ss for provider %s"
                (name concept-type)
                (:provider-id provider)))
  (let [db (helper/get-metadata-db-db system)
        params (merge {:concept-type concept-type 
                       :provider-id (:provider-id provider)}
                      (when (cc/generic-concept? concept-type)
                        {:schema (name concept-type)}))
        concept-batches (db/find-concepts-in-batches
                         db provider
                         params
                         (:db-batch-size system))
        num-concepts (bulk-index-concept-batches system concept-batches)
        msg (format "Indexing of %s %s revisions for provider %s completed."
                    num-concepts
                    (name concept-type)
                    (:provider-id provider))]
    (info msg)))

(defn index-provider-concepts
  "Bulk index concepts for the given provider-id and concept-type."
  [system concept-type provider-id]
  (->> provider-id
       (helper/get-provider system)
       (index-concepts-by-provider system concept-type)))

(defn index-all-concepts
  "Bulk index all CMR concepts for the concept-type."
  [system concept-type]
  (info (format "Indexing all %ss" (name concept-type)))
  (doseq [provider (helper/get-providers system)]
    (index-concepts-by-provider system concept-type provider))
  (info (format "Indexing of all %ss completed." (name concept-type))))

(defn- index-access-control-concepts
  "Bulk index ACLs or access groups"
  [system concept-batches]
  (info "Indexing concepts")
  (ac-bulk-index/bulk-index-with-revision-date {:system (helper/get-indexer system)} concept-batches))

(defn- index-concepts
  "Bulk index the given concepts using the indexer-app"
  [system concept-batches]
  (info "Indexing concepts")
  (index/bulk-index-with-revision-date {:system (helper/get-indexer system)} concept-batches))

(defn- fetch-and-index-new-concepts
  "Get batches of concepts for a given provider/concept type that have a revision-date
  newer than the given date time and then index them."
  [system provider concept-type date-time]
  (let [db (helper/get-metadata-db-db system)
        provider-id (:provider-id provider)
        params {:concept-type concept-type
                :provider-id provider-id
                :revision-date {:comparator `> :value (time-coerce/to-sql-time date-time)}}
        params (if (some #{concept-type} misc-concept-types)
                 (dissoc params :provider-id)
                 params)
        concept-batches (db/find-concepts-in-batches
                          db provider params (:db-batch-size system))
        {:keys [max-revision-date num-indexed]} (if (contains? #{:acl :access-group} concept-type)
                                                 (index-access-control-concepts system concept-batches)
                                                 (index-concepts system concept-batches))]

    (info (format (str "Indexed %d %s(s) for provider %s with revision-date later than %s and max "
                       "revision date was %s.")
                  num-indexed
                  (name concept-type)
                  provider-id
                  date-time
                  max-revision-date))
    {:max-revision-date max-revision-date
     :num-indexed num-indexed}))

(defn index-system-concepts
  "Bulk index tags, acls, and access-groups."
  [system start-index]
  (let [db (helper/get-metadata-db-db system)
        provider {:provider-id system-concept-provider}
        total (apply + (for [concept-type system-concept-types
                             :let [params {:concept-type concept-type
                                           :provider-id (:provider-id provider)}
                                   concept-batches (db/find-concepts-in-batches db
                                                                                provider
                                                                                params
                                                                                (:db-batch-size system)
                                                                                start-index)]]
                         (:num-indexed (if (= concept-type :tag)
                                         (index-concepts system concept-batches)
                                         (index-access-control-concepts system concept-batches)))))]
    (info "Indexed" total "system concepts.")
    total))

(defn index-concepts-by-id
  "Index concepts of the given type for the given provider with the given concept-ids."
  [system provider-id concept-type concept-ids]
  (let [db (helper/get-metadata-db-db system)
        provider (helper/get-provider system provider-id)
        ;; Oracle only allows 1000 values in an 'in' clause, so we partition here
        ;; to prevent exceeding that. This should probably be done in the db namespace,
        ;; but I want to avoid making changes beyond bootstrap-app for this functionality.
        concept-id-batches (partition-all 1000 concept-ids)
        concept-batches (for [batch concept-id-batches
                              concept-batch (db/find-concepts-in-batches db
                                                                         provider
                                                                         {:concept-type concept-type :concept-id batch}
                                                                         (:db-batch-size system))]
                          concept-batch)
        total (index/bulk-index {:system (helper/get-indexer system)} concept-batches)]

    ;; for concept types that have all revisions index, also index the all revisions index
    (when-not (#{:tag :granule} concept-type)
      (index/bulk-index
       {:system (helper/get-indexer system)} concept-batches {:all-revisions-index? true}))

    (info "Indexed " total " concepts.")
    total))

(defmulti delete-concepts-by-id
  "Delete the concepts of the given type for the given provder with the given concept-ids."
  (fn [system provider-id concept-type concept-ids] concept-type))

(defmethod delete-concepts-by-id :granule
  [system _ _ concept-ids]
  (let [query {:terms {:concept-id concept-ids}}
        indexer-context {:system (helper/get-indexer system)}]
    (es/delete-by-query indexer-context "_all" "granule" query)))

(defmethod delete-concepts-by-id :default
  [system provider-id concept-type concept-ids]
  (let [db (helper/get-metadata-db-db system)
        provider (helper/get-provider system provider-id)
        ;; Oracle only allows 1000 values in an 'in' clause, so we partition here
        ;; to prevent exceeding that. This should probably be done in the db namespace,
        ;; but I want to avoid making changes beyond bootstrap-app for this functionality.
        concept-id-batches (partition-all 1000 concept-ids)
        concept-batches (for [batch concept-id-batches
                              concept-batch (db/find-concepts-in-batches db
                                                                         provider
                                                                         {:concept-type concept-type :concept-id batch}
                                                                         (:db-batch-size system))]
                          (map #(assoc % :deleted true) concept-batch))
        total (index/bulk-index {:system (helper/get-indexer system)} concept-batches)]
    (info "Deleted " total " concepts")
    total))

(defn- index-system-misc-concepts-after-datetime
  "Index all system and miscellaneous concepts created later than or equal to the given date-time.
  Returns a map of :max-revision-date and :num-indexed."
  [system date-time]
  (let [system-concept-response-map (for [concept-type (concat system-concept-types misc-concept-types)]
                                      (fetch-and-index-new-concepts
                                       system {:provider-id system-concept-provider} concept-type date-time))
        max-revision-date (apply util/max-compare
                                 (map :max-revision-date system-concept-response-map))
        system-concept-count (reduce + (map :num-indexed system-concept-response-map))]
    {:max-revision-date max-revision-date
     :num-indexed system-concept-count}))

(defn index-provider-data-later-than-date-time
  "Index all concept revisions created later than or equal to the given date-time for a given provider."
  [system provider-id date-time]
  (info (format "Indexing concepts with revision-date later than [%s] for provider [%s] started."
                date-time
                provider-id))
  (if (= system-concept-provider provider-id)
    (let [{:keys [num-indexed]} (index-system-misc-concepts-after-datetime system date-time)]
      (info (format "Indexed %d system concepts." num-indexed)))

    (let [provider (helper/get-provider system provider-id)
          provider-response-map (for [concept-type [:collection :granule]]
                                  (fetch-and-index-new-concepts
                                   system provider concept-type date-time))
          provider-concept-count (reduce + (map :num-indexed provider-response-map))]
      (info (format "Indexed %d provider concepts." provider-concept-count))))
  (info (format "Indexing concepts with revision-date later than [%s] for provider [%s] completed."
                date-time
                provider-id)))

(defn index-data-later-than-date-time
  "Index all concept revisions created later than or equal to the given date-time
  for the given providers. If provider-ids is empty, then all providers are indexed."
  [system provider-ids date-time]
  (info (format "Indexing concepts with revision-date later than %s for providers %s started."
                date-time
                provider-ids))
  (let [has-cmr-provider? (or (empty? provider-ids)
                              (some #{system-concept-provider} provider-ids))
        providers (if (seq provider-ids)
                    (map #(helper/get-provider system %) (remove #(= system-concept-provider %) provider-ids))
                    ;; all providers
                    (helper/get-providers system))
        provider-response-map (for [provider providers
                                    concept-type [:collection :granule]]
                                (fetch-and-index-new-concepts
                                 system provider concept-type date-time))
        provider-concept-count (reduce + (map :num-indexed provider-response-map))
        system-concept-response-map (if has-cmr-provider?
                                      (index-system-misc-concepts-after-datetime system date-time)
                                      {:num-indexed 0})
        system-concept-count (:num-indexed system-concept-response-map)
        indexing-complete-message (format "Indexed %d provider concepts and %d system concepts."
                                          provider-concept-count
                                          system-concept-count)]
    (info (format "Indexing concepts with revision-date later than %s for providers %s completed."
                  date-time
                  provider-ids))
    (info indexing-complete-message)
    {:message indexing-complete-message
     :max-revision-date (apply util/max-compare
                               (map :max-revision-date
                                    (apply conj provider-response-map system-concept-response-map)))}))

;; Background task to handle bulk index requests
(defn handle-bulk-index-requests
  "Handle any requests for bulk indexing. We use separate channels for each type of
  indexing request instead of a single channel to simplify the dispatch logic.
  Since we know at the time a request is made what function should be used, there
  is no point in implementing separate code to determine what function to call
  when an item comes off the channel."
  [system]
  (info "Starting background task for monitoring bulk provider indexing channels.")
  (let [core-async-dispatcher (:core-async-dispatcher system)]
    (let [channel (:provider-index-channel core-async-dispatcher)]
      (ca/thread (while true
                   (try ; catch any errors and log them, but don't let the thread die
                     (let [{:keys [provider-id start-index]} (<!! channel)]
                       (index-provider system provider-id start-index))
                     (catch Throwable e
                       (error e (.getMessage e)))))))
    (let [channel (:collection-index-channel core-async-dispatcher)]
      (ca/thread (while true
                   (try ; catch any errors and log them, but don't let the thread die
                     (let [{:keys [provider-id collection-id] :as options} (<!! channel)]
                       (index-granules-for-collection system provider-id collection-id options))
                     (catch Throwable e
                       (error e (.getMessage e)))))))
    (let [channel (:system-concept-channel core-async-dispatcher)]
      (ca/thread (while true
                   (try ; catch any errors and log them, but don't let the thread die
                     (let [{:keys [start-index]} (<!! channel)]
                       (index-system-concepts system start-index))
                     (catch Throwable e
                       (error e (.getMessage e)))))))
    (let [channel (:concept-id-channel core-async-dispatcher)]
      (ca/thread (while true
                  (try ; log errors but keep the thread alive)
                    (let [{:keys [provider-id concept-type concept-ids request]} (<!! channel)]
                      (if (= request :delete)
                        (delete-concepts-by-id system provider-id concept-type concept-ids)
                        (index-concepts-by-id system provider-id concept-type concept-ids)))
                    (catch Throwable e
                      (error e (.getMessage e)))))))))
