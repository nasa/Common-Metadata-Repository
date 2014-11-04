(ns cmr.bootstrap.data.db-synchronization
  "Contains functions for finding differences between Metadata DB and Catalog REST and putting them
  back in sync."
  (:require [clojure.core.async :as async :refer [go >! go-loop <! <!!]]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [clojure.set :as set]
            [sqlingvo.core :as sql :refer [sql select insert from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]
            [cmr.bootstrap.data.migration-utils :as mu]
            [cmr.common.util :as util]
            [cmr.common.concepts :as concepts]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.metadata-db.data.oracle.sql-utils :as sql-utils]
            [cmr.metadata-db.data.oracle.concepts :as mdb-concepts]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.indexer.services.index-service :as index-service]))

(def WORK_ITEMS_BATCH_SIZE
  "The number of work items to fetch at a time from the work items table during processing"
  1000)

(def NUM_PROCESS_INSERT_THREADS
  "The number of concurrent threads that should process items for insert."
  5)

(def NUM_PROCESS_DELETE_THREADS
  "The number of concurrent threads that should process deleting items"
  5)

(comment

  (def system (get-in user/system [:apps :bootstrap]))

  (add-missing-items-to-work-table system "CPROV1" :collection)

  (get-work-items-batch system 1 10)

  (get-latest-concept-id-revision-ids system "CPROV1" :collection (map :concept-id (get-work-items-batch system 1 10)))

  (get-concept-from-catalog-rest system "CPROV1" :collection "C1-CPROV1" 1)


)

(defn- truncate-work-table
  "Removes everything from the sync work table."
  [system]
  (j/execute! (:db system) ["delete from sync_work"]))


(defn- query-for-concept-rev-id-pairs
  "Executes the give statement that should find concept_id and revision_id pairs. Returns the results
  as tuples of concept ids and revision ids."
  [conn stmt]
  (mapv (fn [{:keys [concept_id revision_id]}]
          [concept_id (when revision_id (long revision_id))])
        (sql-utils/query conn stmt)))

(defn- add-missing-items-to-work-table
  "Finds items that are missing in Metadata DB and adds it to the sync_work table."
  [system provider-id concept-type]
  ;; TODO this sql should be updated to find items that were INGEST_UPDATED_AT > start date and
  ;; INGEST_UPDATED_AT < end date
  (truncate-work-table system)
  (let [sql (format "insert into sync_work (id, concept_id)
                    select ROWNUM, %s from %s"
                    (mu/concept-type->catalog-rest-id-field concept-type)
                    (mu/catalog-rest-table system provider-id concept-type))]
      (j/execute! (:db system) [sql])))

(defn get-work-items-batch
  "Selects n work items from the table starting at the given index and retrieving up to n items"
  [system start-index n]
  (let [sql "select concept_id, revision_id from sync_work where id >= ? and id < ?"
        stmt [sql start-index (+ start-index n)]]
    (query-for-concept-rev-id-pairs (:db system) stmt)))

(defn- in-clause
  "Generates a sql in clause string. If there are more than the max values to put in the in clause
  it creates multiple an ANDs them together."
  [field num-values]
  (let [num-full (int (/ num-values 1000))
        num-in-partial (mod num-values 1000)
        make-in #(str field " in (" (str/join "," (repeat % "?")) ")" )
        full-in (make-in 1000)
        full-ins (repeat num-full full-in)
        partial-ins (when (> num-in-partial 0) [(make-in num-in-partial)])]
    (str/join " or " (concat full-ins partial-ins))))

(comment


  (get-latest-concept-id-revision-ids
    system "CPROV1" :collection ["C1-CPROV1"])

  )


(defn get-latest-concept-id-revision-ids
  "Finds the revision ids for the given concept ids in metadata db. Returns tuples of concept id and
  revision id"
  [system provider-id concept-type concept-ids]
  (let [tuples (mdb-concepts/get-latest-concept-id-revision-id-tuples
                 (:db system) concept-type provider-id concept-ids)]
    (concat tuples
            ;; Find concept ids that didn't exist in Metadata DB at all.
            ;; A revision 0 indicates they don't exist yet. This will be incremented to the first
            ;; revision number of 1.
            (for [concept-id (set/difference (set concept-ids) (set (map first tuples)))]
              [concept-id 0]))))

(defmulti get-concept-from-catalog-rest
  "Retrieves a concept from the Catalog REST. Provider id and concept type are redundant given that
  the concept id is provided. They're included because they're available and would avoid having to
  parse the concept id."
  (fn [system provider-id concept-type concept-id revision-id]
    concept-type))

(defmethod get-concept-from-catalog-rest :collection
  [system provider-id concept-type concept-id revision-id]
  (j/with-db-transaction
    [conn (:db system)]
    (let [sql (format "select dataset_id, compressed_xml, ingest_updated_at, short_name, version_id,
                      xml_mime_type, delete_time from %s where echo_collection_id = ?"
                      (mu/catalog-rest-table system provider-id concept-type))
          stmt [sql concept-id]
          [{:keys [dataset_id compressed_xml ingest_updated_at short_name version_id xml_mime_type
                   delete_time]}] (sql-utils/query conn stmt)]
      {:concept-type concept-type
       :format (mdb-concepts/db-format->mime-type xml_mime_type)
       :metadata (mdb-concepts/blob->string compressed_xml)
       :concept-id concept-id
       :revision-id revision-id
       :deleted false
       :extra-fields {:short-name short_name
                      :entry-title dataset_id
                      :version-id version_id
                      :delete-time (when delete_time
                                     (mdb-concepts/oracle-timestamp-tz->clj-time conn delete_time))}
       :provider-id provider-id
       :native-id dataset_id})))

(defmethod get-concept-from-catalog-rest :granule
  [system provider-id concept-type concept-id revision-id]
  (j/with-db-transaction
    [conn (:db system)]
    (let [sql (format "select granule_ur, compressed_xml, ingest_updated_at, dataset_record_id,
                      xml_mime_type, delete_time from %s where echo_granule_id = ?"
                      (mu/catalog-rest-table system provider-id concept-type))
          stmt [sql concept-id]
          [{:keys [granule_ur compressed_xml ingest_updated_at dataset_record_id xml_mime_type
                   delete_time]}] (sql-utils/query conn stmt)]
      {:concept-type concept-type
       :format (mdb-concepts/db-format->mime-type xml_mime_type)
       :metadata (mdb-concepts/blob->string compressed_xml)
       :concept-id concept-id
       :revision-id revision-id
       :deleted false
       :extra-fields {:granule-ur granule_ur
                      :parent-collection-id (concepts/build-concept-id
                                              {:concept-type :collection
                                               :sequence-number (long dataset_record_id)
                                               :provider-id provider-id})
                      :delete-time (when delete_time
                                     (mdb-concepts/oracle-timestamp-tz->clj-time conn delete_time))}
       :provider-id provider-id
       :native-id granule_ur})))

(defn process-items-from-work-table
  "Starts a process that will retrieve items in batches from the work table and writes them to a
  channel. The channel is returned. Each message on the channel is a sequence of batched items.
  Items are tuples of concept id and revision id."
  [system]
  (let [item-batch-chan (async/chan 1)]
    (go
      (debug "process-items-from-work-table starting")
      (try
        (loop [start-index 1]
          (when-let [items (seq (get-work-items-batch system start-index WORK_ITEMS_BATCH_SIZE))]
            (debug "process-items-from-work-table: Found" (count items) "items")
            (>! item-batch-chan items)
            (recur (+ start-index (count items)))))
        (finally
          (async/close! item-batch-chan)
          (debug "process-items-from-work-table completed"))))
    item-batch-chan))

(defmacro while-let
  "A macro that's similar to when let. It will continually evaluate the bindings and execute the body
  until the binding results in a nil value."
  [bindings & body]
  `(loop []
     (when-let ~bindings
       ~@body
       (recur))))

(defn map-missing-items-to-concepts
  "Starts a process that will map batches of items to individual concepts from Catalog REST that
  should be saved in the Metadata DB. Returns the channel that will contain the concepts to save."
  [system provider-id concept-type item-batch-chan]
  (let [concepts-chan (async/chan 10)]
    (go
      (debug "map-missing-items-to-concepts starting")
      (try
        (while-let [items (<! item-batch-chan)]
          (debug "map-missing-items-to-concepts: Received" (count items) "items")
          (doseq [[concept-id revision-id] (get-latest-concept-id-revision-ids
                                             system provider-id concept-type (map first items))]
            (debug "map-missing-items-to-concepts: Processing" concept-id "-" revision-id)
            (>! concepts-chan (get-concept-from-catalog-rest
                                system provider-id concept-type concept-id (inc revision-id)))))
        (finally
          (async/close! concepts-chan)
          (async/close! item-batch-chan)
          (debug "map-missing-items-to-concepts completed"))))
    concepts-chan))

(defn save-and-index-concept
  "Saves the concept to the Metadata DB and indexes it using the indexer"
  [system concept]
  ;; This is going to copy the item to metadata db. If it was never added to MDB in the first place
  ;; and was deleted in Catalog REST in the mean time Ingest would return a 404 from the delete
  ;; and Catalog REST would ignore it. This would end up saving the item in Metadata DB making them
  ;; out of sync. The delete processing should happen after this step and put it back in sync.
  (let [mdb-context {:system (:metadata-db system)}
        indexer-context {:system (:indexer system)}
        {:keys [concept-id revision-id]} concept]
    (concept-service/save-concept mdb-context concept)
    (index-service/index-concept indexer-context concept-id revision-id true))

  ;; TODO catch conflict failures and log them. They're ok.

  )

(defn process-missing-concepts
  "Starts a series of threads that read concepts one at a time off the channel, save, and index them.
  Returns when all concepts have been processed or an error has occured."
  [system concepts-chan]
  (let [thread-chans (for [n (range 0 NUM_PROCESS_INSERT_THREADS)
                           :let [log (fn [& args]
                                       (debug "process-missing-concepts" n ":" (str/join " " args)))]]
                       (async/thread
                         (log "starting")
                         (try
                           (while-let [concept (<!! concepts-chan)]
                             (log "Inserting" (:concept-id concept) (:revision-id concept))
                             (save-and-index-concept system concept))
                           (finally
                             (async/close! concepts-chan)
                             (log "completed")))))]
    ;; Force iteration over all thread chans
    (doseq [thread-chan (doall thread-chans)]
      ;; Wait for the thread to close the channel indicating it's done.
      (<!! thread-chan))))


(defn synchronize-missing-items
  "Finds items missing from Metadata DB, loads the concepts from Catalog REST, saves them in the
  Metadata DB and indexes them."
  [system provider-id concept-type]
  (info "Synchronizing" concept-type "inserts for" provider-id)
  (add-missing-items-to-work-table system provider-id concept-type)
  (->> (process-items-from-work-table system)
       (map-missing-items-to-concepts system provider-id concept-type)
       (process-missing-concepts system)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Delete code

(defn- truncate-delete-work-table
  "Removes everything from the sync delete work table."
  [system]
  (j/execute! (:db system) ["delete from sync_delete_work"]))

(defn- add-deleted-items-to-delete-work-table
  "Find items that exist in Metadata DB but do not exist in Catalog REST. This includes all
  revisions and tombstones. These items are inserted into sync_delete_work."
  [system provider-id concept-type]
  (truncate-delete-work-table system)
  (let [sql (format "insert into sync_delete_work (concept_id, revision_id, deleted)
                    select concept_id, revision_id, deleted from %s
                    where concept_id not in (select %s from %s)"
                    (tables/get-table-name provider-id concept-type)
                    (mu/concept-type->catalog-rest-id-field concept-type)
                    (mu/catalog-rest-table system provider-id concept-type))]
    (j/execute! (:db system) [sql])))

(defn- get-max-of-multi-revisions
  "Finds the concept id, and maximum revision id of items in the delete work table that have more
  than one revision."
  [system]
  (query-for-concept-rev-id-pairs
    (:db system)
    ["select concept_id, max(revision_id) revision_id from sync_delete_work
     group by concept_id
     having count(concept_id) > 1"]))

(defn- delete-smaller-revisions
  "Deletes items from the delete work table that have revision ids smaller than given in the
  concept id revision id tuples."
  [system tuples]
  (when (seq tuples)
    (apply j/db-do-prepared (:db system)
           "delete from sync_delete_work where concept_id = ? and revision_id < ?"
           tuples)))

(defn- transfer-delete-work-items-to-work-table
  "Copies the items from the sync_delete_work table to the normal work table so that deletes can be
  processed. Skips items that are tombstones in the delete work table since these will not need
  deleting in the metadata db."
  [system]
  (truncate-work-table system)
  (j/execute! (:db system)
              ["insert into sync_work (id, concept_id, revision_id)
               select rownum, concept_id, revision_id from sync_delete_work
               where deleted = 0"]))

(defn add-deleted-items-to-work-table
  "Populates the work table with deleted items. This is a several step process to find the items
  that are missing and require a tombstone in metadata db."
  [system provider-id concept-type]
  ;; Find items that exist in Metadata DB but do not exist in Catalog REST. This includes
  ;; all revisions and tombstones. These items are inserted into sync_delete_work.
  (add-deleted-items-to-delete-work-table system provider-id concept-type)
  ;; Keep the max revision of each concept in sync_delete_work. We do this by getting the maximum
  ;; revision for each concept having multiple revisions and deleting any less than the max revision
  (delete-smaller-revisions system (get-max-of-multi-revisions system))
  ;; Copy the items from sync_delete_work to sync_work. This allows us to add a rownum id for
  ;; batch processing. We also skip any revisions that are a tombstone.
  (transfer-delete-work-items-to-work-table system))

(defn map-extra-items-batches-to-deletes
  "Starts a process that maps batches of items to delete to individual tuples of concept ids and
  revision ids of a tombstone that should be created. Returns that channel that will receive the
  individual tombstones."
  [system provider-id concept-type item-batch-chan]
  (let [tuples-chan (async/chan 10)]
    (go
      (debug "map-extra-items-batches-to-deletes starting")
      (try
        (while-let [items (<! item-batch-chan)]
          (debug "map-extra-items-batches-to-deletes: Received" (count items) "items")
          (doseq [[concept-id revision-id] items]
            (>! tuples-chan [concept-id (inc revision-id)])))
        (finally
          (async/close! tuples-chan)
          (async/close! item-batch-chan)
          (debug "map-extra-items-batches-to-deletes completed"))))
    tuples-chan))

(defn create-tombstone-and-unindex-concept
  "Creates a tombstone with the given concept id and revision id and unindexes the concept."
  [system concept-id revision-id]
  (let [mdb-context {:system (:metadata-db system)}
        indexer-context {:system (:indexer system)}]
    (concept-service/delete-concept mdb-context concept-id revision-id)
    (index-service/delete-concept indexer-context concept-id revision-id true))
  ;; TODO catch conflict failures and log them. They're ok.
  )

(defn process-deletes
  "Starts a series of threads that read items one at a time off the channel then creates a tombstone
  and unindexes them. Returns when all items have been processed or an error has occured."
  [system tuples-chan]
  (let [thread-chans (for [n (range 0 NUM_PROCESS_DELETE_THREADS)
                           :let [log (fn [& args]
                                       (debug "process-deletes" n ":" (str/join " " args)))]]
                       (async/thread
                         (log "starting")
                         (try
                           (while-let [[concept-id revision-id] (<!! tuples-chan)]
                             (log "Deleting" concept-id "-" revision-id)
                             (create-tombstone-and-unindex-concept system concept-id revision-id))
                           (finally
                             (async/close! tuples-chan)
                             (log "completed")))))]
    ;; Force iteration over all thread chans
    (doseq [thread-chan (doall thread-chans)]
      ;; Wait for the thread to close the channel/return a result indicating it's done.
      (<!! thread-chan))))

(defn synchronize-deletes
  "Finds items that exist in Metadata DB but do not exist in Catalog REST. It creates tombstones
  for these items and unindexes them."
  [system provider-id concept-type]
  (add-deleted-items-to-work-table system provider-id concept-type)
  (->> (process-items-from-work-table system)
       (map-extra-items-batches-to-deletes system provider-id concept-type)
       (process-deletes system)))

(defn synchronize-databases
  "Finds differences in Metadata DB and Catalog REST and brings Metadata DB along with the indexer
  back in sync with Catalog REST."
  [system]
  ;; TODO pass the start date and end data around to make the select performance better
  (doseq [provider (provider-service/get-providers {:system system})]
    (synchronize-missing-items system provider :collection)
    (synchronize-deletes system provider :collection)
    (synchronize-missing-items system provider :granule)
    (synchronize-deletes system provider :granule)))