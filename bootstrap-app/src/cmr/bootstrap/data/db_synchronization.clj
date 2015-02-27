(ns cmr.bootstrap.data.db-synchronization
  "Contains functions for finding differences between Metadata DB and Catalog REST and putting them
  back in sync."
  (:require [clojure.core.async :as async :refer [go >! go-loop <! <!!]]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [clojure.set :as set]
            [cmr.metadata-db.data.oracle.sql-utils :as su :refer [select]]
            [cmr.bootstrap.data.migration-utils :as mu]
            [cmr.common.util :as util]
            [cmr.common.concepts :as concepts]
            [cmr.common.services.errors :as errors]
            [cmr.common.config :as config :refer [defconfig]]
            [clj-time.coerce :as cr]
            [clj-time.core :as t]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.metadata-db.data.oracle.concepts :as mdb-concepts]
            [cmr.oracle.connection :as oracle]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.indexer.services.index-service :as index-service]))

(defconfig db-sync-work-items-batch-size
  "The number of work items to fetch at a time from the work items table during processing"
  {:default 1000 :type Long})

(defconfig db-sync-num-insert-threads
  "The number of concurrent threads that should process items for insert."
  {:default 5 :type Long})

(defconfig db-sync-num-delete-threads
  "The number of concurrent threads that should process deleting items"
  {:default 5 :type Long})

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
        (su/query conn stmt)))

(defmulti concept-matches-dataset-id-clause
  "Returns a sql clause to find items in the given concept type table by dataset id in a Catalog REST
  table."
  (fn [system provider-id concept-type]
    concept-type))

(defmethod concept-matches-dataset-id-clause :collection
  [system provider-id concept-type]
  "dataset_id = ?")

(defmethod concept-matches-dataset-id-clause :granule
  [system provider-id concept-type]
  (format "dataset_record_id = (select id from %s where dataset_id = ?)"
          (mu/catalog-rest-table system provider-id :collection)))

(defmulti concept-matches-entry-title-clause
  "Returns a sql clause to find items in the given concept type table by entry title in a metadata db
  table"
  (fn [system provider-id concept-type]
    concept-type))

(defmethod concept-matches-entry-title-clause :collection
  [system provider-id concept-type]
  "entry_title = ?")

(defmethod concept-matches-entry-title-clause :granule
  [system provider-id concept-type]
  (format "parent_collection_id = (select distinct concept_id from %s where entry_title = ?)"
          (tables/get-table-name provider-id :collection)))

(defmulti add-updates-to-work-table-stmt
  (fn [system provider-id concept-type params]
    (set (keys params))))

(defmethod add-updates-to-work-table-stmt :default
  [system provider-id concept-type params]
  (errors/internal-error! (str "Can not handle parameter combination of: " (pr-str params))))

(defmethod add-updates-to-work-table-stmt #{:start-date :end-date}
  [system provider-id concept-type {:keys [start-date end-date]}]
  [(format "insert into sync_work (id, concept_id)
           select ROWNUM, %s from %s
           where ingest_updated_at >= ? and ingest_updated_at <= ?"
           (mu/concept-type->catalog-rest-id-field concept-type)
           (mu/catalog-rest-table system provider-id concept-type))
   (cr/to-sql-time start-date) (cr/to-sql-time end-date)])

(defmethod add-updates-to-work-table-stmt #{:start-date :end-date :entry-title}
  [system provider-id concept-type {:keys [entry-title start-date end-date]}]
  (let [concept-clause (concept-matches-dataset-id-clause system provider-id concept-type)]
    [(format "insert into sync_work (id, concept_id)
             select ROWNUM, %s from %s
             where ingest_updated_at >= ? and ingest_updated_at <= ?
             and %s"
             (mu/concept-type->catalog-rest-id-field concept-type)
             (mu/catalog-rest-table system provider-id concept-type)
             concept-clause)
     (cr/to-sql-time start-date) (cr/to-sql-time end-date) entry-title]))

(defn- add-updates-to-work-table
  "Finds all the items that were ingested between start-date and end-date in Catalog REST and adds
  them to the sync_work table. These are all not necessarily missing from Metadata DB. We'll treat
  them as if they were since we know ingest into Metadata DB is idempotent."
  [system provider-id concept-type params]
  (truncate-work-table system)
  (let [stmt (add-updates-to-work-table-stmt system provider-id concept-type params)]
    (j/execute! (:db system) stmt)))

(defn get-work-items-batch
  "Selects n work items from the table starting at the given index and retrieving up to n items"
  [system start-index n]
  (let [sql "select concept_id, revision_id from sync_work where id >= ? and id < ?"
        stmt [sql start-index (+ start-index n)]]
    (query-for-concept-rev-id-pairs (:db system) stmt)))

(defn- in-clause
  "Generates a sql in clause string. If there are more than the max values to put in the in clause
  it creates multiple an ORs them together."
  [field num-values]
  (let [num-full (int (/ num-values 1000))
        num-in-partial (mod num-values 1000)
        make-in #(str field " in (" (str/join "," (repeat % "?")) ")" )
        full-in (make-in 1000)
        full-ins (repeat num-full full-in)
        partial-ins (when (> num-in-partial 0) [(make-in num-in-partial)])]
    (str/join " or " (concat full-ins partial-ins))))

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

(defn- get-entry-id
  "Returns the collection entry-id based on the given fields of a collection"
  [mdb-format short-name version-id]
  (if (or (= "application/dif+xml" mdb-format) (empty? version-id))
    short-name
    (str short-name "_" version-id)))

(defmulti get-concept-from-catalog-rest
  "Retrieves a concept from the Catalog REST. Provider id and concept type are redundant given that
  the concept id is provided. They're included because they're available and would avoid having to
  parse the concept id. Will return nil if the XML Mime Type of the concept is one that is not
  supported by Metadata DB."
  (fn [system provider-id concept-type concept-id revision-id]
    concept-type))

(defmethod get-concept-from-catalog-rest :collection
  [system provider-id concept-type concept-id revision-id]
  (j/with-db-transaction
    [conn (:db system)]
    (let [sql (format "select dataset_id, compressed_xml, short_name, version_id,
                      xml_mime_type, delete_time, ingest_updated_at from %s where echo_collection_id = ?"
                      (mu/catalog-rest-table system provider-id concept-type))
          stmt [sql concept-id]
          [{:keys [dataset_id compressed_xml short_name version_id xml_mime_type
                   delete_time ingest_updated_at]}] (su/query conn stmt)
          delete-time (when delete_time
                        (oracle/oracle-timestamp->clj-time conn delete_time))]
      (if (or (nil? delete-time) (t/after? delete-time (t/now)))
        (if-let [mdb-format (mdb-concepts/db-format->mime-type xml_mime_type)]
          {:concept-type concept-type
           :format mdb-format
           :metadata (mdb-concepts/blob->string compressed_xml)
           :concept-id concept-id
           :revision-id revision-id
           :revision-date (mdb-concepts/oracle-timestamp->str-time conn ingest_updated_at)
           :deleted false
           :extra-fields {:short-name short_name
                          :entry-title dataset_id
                          :version-id version_id
                          :entry-id (get-entry-id mdb-format short_name version_id)
                          :delete-time (when delete_time
                                         (mdb-concepts/oracle-timestamp->str-time conn delete_time))}
           :provider-id provider-id
           :native-id dataset_id}
          (warn (format "Skipping Catalog REST Item %s with unsupported xml_mime_type of %s"
                        concept-id xml_mime_type)))
        (warn (format "Skipping Catalog REST Item %s with delete time %s in the past"
                      concept-id delete-time))))))

(defmethod get-concept-from-catalog-rest :granule
  [system provider-id concept-type concept-id revision-id]
  (j/with-db-transaction
    [conn (:db system)]
    (let [sql (format "select granule_ur, compressed_xml, dataset_record_id,
                      xml_mime_type, delete_time, ingest_updated_at from %s where echo_granule_id = ?"
                      (mu/catalog-rest-table system provider-id concept-type))
          stmt [sql concept-id]
          [{:keys [granule_ur compressed_xml dataset_record_id xml_mime_type
                   delete_time ingest_updated_at]}] (su/query conn stmt)

          delete-time (when delete_time
                        (oracle/oracle-timestamp->clj-time conn delete_time))]
      (if (or (nil? delete-time) (t/after? delete-time (t/now)))
        (if-let [mdb-format (mdb-concepts/db-format->mime-type xml_mime_type)]
          {:concept-type concept-type
           :format mdb-format
           :metadata (mdb-concepts/blob->string compressed_xml)
           :concept-id concept-id
           :revision-id revision-id
           :revision-date (mdb-concepts/oracle-timestamp->str-time conn ingest_updated_at)
           :deleted false
           :extra-fields {:granule-ur granule_ur
                          :parent-collection-id (concepts/build-concept-id
                                                  {:concept-type :collection
                                                   :sequence-number (long dataset_record_id)
                                                   :provider-id provider-id})
                          :delete-time (when delete_time
                                         (mdb-concepts/oracle-timestamp->str-time conn delete_time))}
           :provider-id provider-id
           :native-id granule_ur}
          (warn (format "Skipping Catalog REST Item %s with unsupported xml_mime_type of %s"
                        concept-id xml_mime_type)))
        (warn (format "Skipping Catalog REST Item %s with delete time %s in the past"
                      concept-id delete-time))))))

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
          (when-let [items (seq (get-work-items-batch system start-index
                                                      (db-sync-work-items-batch-size)))]
            (debug "process-items-from-work-table: Found" (count items) "items")
            (>! item-batch-chan items)
            (recur (+ start-index (count items)))))
        (finally
          (async/close! item-batch-chan)
          (debug "process-items-from-work-table completed"))))
    item-batch-chan))

(defn map-missing-items-to-concepts
  "Starts a process that will map batches of items to individual concepts from Catalog REST that
  should be saved in the Metadata DB. Returns the channel that will contain the concepts to save."
  [system provider-id concept-type item-batch-chan]
  (let [concepts-chan (async/chan 10)]
    (go
      (debug "map-missing-items-to-concepts starting")
      (try
        (util/while-let [items (<! item-batch-chan)]
                        (debug "map-missing-items-to-concepts: Received" (count items) "items")
                        (doseq [[concept-id revision-id] (get-latest-concept-id-revision-ids
                                                           system provider-id concept-type (map first items))]
                          (debug "map-missing-items-to-concepts: Processing" concept-id "-" revision-id)
                          ;; get-concept-from-catalog-rest can return nil if the item is an unsupported XML mime
                          ;; type or we should skip it for other reasons
                          (when-let [concept (get-concept-from-catalog-rest
                                               system provider-id concept-type concept-id (inc revision-id))]
                            (>! concepts-chan concept))))
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
    (try
      (concept-service/save-concept mdb-context concept)
      (index-service/index-concept indexer-context concept-id revision-id true)
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

(defn process-missing-concepts
  "Starts a series of threads that read concepts one at a time off the channel, save, and index them.
  Returns when all concepts have been processed or an error has occured."
  [system concepts-chan]
  (let [thread-chans (for [n (range 0 (db-sync-num-insert-threads))
                           :let [log (fn [& args]
                                       (debug "process-missing-concepts" n ":" (str/join " " args)))]]
                       (async/thread
                         (log "starting")
                         (try
                           (util/while-let [concept (<!! concepts-chan)]
                                           (log "Inserting" (:concept-id concept) (:revision-id concept))
                                           (save-and-index-concept system concept))
                           (finally
                             (async/close! concepts-chan)
                             (log "completed")))))]
    ;; Force iteration over all thread chans
    (doseq [thread-chan (doall thread-chans)]
      ;; Wait for the thread to close the channel indicating it's done.
      (<!! thread-chan))))


(defn synchronize-updates
  "Finds any items in Catalog REST, loads the concepts from Catalog REST, saves them in the
  Metadata DB and indexes them."
  [system provider-id concept-type params]
  (info "Synchronizing" concept-type "updates for" provider-id)
  (add-updates-to-work-table system provider-id concept-type params)
  (->> (process-items-from-work-table system)
       (map-missing-items-to-concepts system provider-id concept-type)
       (process-missing-concepts system)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Missing code

(defmulti add-missing-to-work-table-stmt
  (fn [system provider-id concept-type params]
    (set (keys params))))

(defmethod add-missing-to-work-table-stmt :default
  [system provider-id concept-type params]
  (errors/internal-error! (str "Can not handle parameter combination of: " (pr-str params))))

(defmethod add-missing-to-work-table-stmt #{}
  [system provider-id concept-type params]
  ;; Theres are two subselects that are executed here. The first finds items that don't exist at all
  ;; in metadata db but exist in catalog rest. The second one finds items that have a tombstone in
  ;; metadata db and exist in catalog rest. The purpose of this one is to find items that were
  ;; potentially deleted in Metadata DB. We can find extra items to process with this because
  ;; updates idempotent.
  [(format "insert into sync_work (id, concept_id) select ROWNUM, concept_id from
           (select %s concept_id from %s where %s not in (select distinct concept_id from %s) union
           select %s concept_id from %s where %s in
           (select distinct concept_id from %s where deleted = 1))"
           ;; First select
           (mu/concept-type->catalog-rest-id-field concept-type)
           (mu/catalog-rest-table system provider-id concept-type)
           (mu/concept-type->catalog-rest-id-field concept-type)
           (tables/get-table-name provider-id concept-type)
           ;; Second select
           (mu/concept-type->catalog-rest-id-field concept-type)
           (mu/catalog-rest-table system provider-id concept-type)
           (mu/concept-type->catalog-rest-id-field concept-type)
           (tables/get-table-name provider-id concept-type))])

(defmethod add-missing-to-work-table-stmt #{:entry-title}
  [system provider-id concept-type {:keys [entry-title]}]
  (let [concept-clause (concept-matches-dataset-id-clause system provider-id concept-type)]
    [(format "insert into sync_work (id, concept_id) select ROWNUM, concept_id from
             (select %s concept_id from %s where %s and %s not in (select distinct concept_id from %s ) union
             select %s concept_id from %s where %s and %s in
             (select distinct concept_id from %s where deleted = 1))"
             ;; First select
             (mu/concept-type->catalog-rest-id-field concept-type)
             (mu/catalog-rest-table system provider-id concept-type)
             concept-clause
             (mu/concept-type->catalog-rest-id-field concept-type)
             (tables/get-table-name provider-id concept-type)
             ;; Second select
             (mu/concept-type->catalog-rest-id-field concept-type)
             (mu/catalog-rest-table system provider-id concept-type)
             concept-clause
             (mu/concept-type->catalog-rest-id-field concept-type)
             (tables/get-table-name provider-id concept-type))
     entry-title entry-title]))

(defn- add-missing-to-work-table
  "Finds all the items that were ingested between start-date and end-date in Catalog REST and adds
  them to the sync_work table. These are all not necessarily missing from Metadata DB. We'll treat
  them as if they were since we know ingest into Metadata DB is idempotent."
  [system provider-id concept-type params]
  (truncate-work-table system)
  (let [stmt (add-missing-to-work-table-stmt system provider-id concept-type params)]
    (j/execute! (:db system) stmt)))

(defn synchronize-missing
  "Finds items missing from Metadata DB, loads the concepts from Catalog REST, saves them in the
  Metadata DB and indexes them."
  [system provider-id concept-type params]
  (info "Synchronizing" concept-type "missing for" provider-id)
  (add-missing-to-work-table system provider-id concept-type params)
  (->> (process-items-from-work-table system)
       (map-missing-items-to-concepts system provider-id concept-type)
       (process-missing-concepts system)))


(comment
  (def system (get-in user/system [:apps :bootstrap]))

  (add-missing-to-work-table system provider-id concept-type params)

  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Delete code

(defn- truncate-delete-work-table
  "Removes everything from the sync delete work table."
  [system]
  (j/execute! (:db system) ["delete from sync_delete_work"]))

(defmulti add-deleted-items-to-delete-work-table-stmt
  (fn [system provider-id concept-type params]
    (set (keys params))))

(defmethod add-deleted-items-to-delete-work-table-stmt :default
  [system provider-id concept-type params]
  (errors/internal-error! (str "Can not handle parameter combination of: " (pr-str params))))

(defmethod add-deleted-items-to-delete-work-table-stmt #{}
  [system provider-id concept-type params]
  [(format "insert into sync_delete_work (concept_id, revision_id, deleted)
           select concept_id, revision_id, deleted from %s
           where concept_id not in (select %s from %s)"
           (tables/get-table-name provider-id concept-type)
           (mu/concept-type->catalog-rest-id-field concept-type)
           (mu/catalog-rest-table system provider-id concept-type))])

(defmethod add-deleted-items-to-delete-work-table-stmt #{:entry-title}
  [system provider-id concept-type {:keys [entry-title]}]
  (let [match-dataset-id-clause (concept-matches-dataset-id-clause system provider-id concept-type)
        match-entry-title-clause (concept-matches-entry-title-clause system provider-id concept-type)]
    [(format "insert into sync_delete_work (concept_id, revision_id, deleted)
             select concept_id, revision_id, deleted from %s
             where %s and concept_id not in (select %s from %s where %s)"
             (tables/get-table-name provider-id concept-type)
             match-entry-title-clause
             (mu/concept-type->catalog-rest-id-field concept-type)
             (mu/catalog-rest-table system provider-id concept-type)
             match-dataset-id-clause)
     entry-title
     entry-title]))

(defn- add-deleted-items-to-delete-work-table
  "Find items that exist in Metadata DB but do not exist in Catalog REST. This includes all
  revisions and tombstones. These items are inserted into sync_delete_work."
  [system provider-id concept-type params]
  (truncate-delete-work-table system)
  (j/execute! (:db system)
              (add-deleted-items-to-delete-work-table-stmt system provider-id concept-type params)))

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
  [system provider-id concept-type params]
  ;; Find items that exist in Metadata DB but do not exist in Catalog REST. This includes
  ;; all revisions and tombstones. These items are inserted into sync_delete_work.
  (add-deleted-items-to-delete-work-table system provider-id concept-type params)
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
        (util/while-let [items (<! item-batch-chan)]
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
  (try
    (let [mdb-context {:system (:metadata-db system)}
          indexer-context {:system (:indexer system)}]
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

(defn process-deletes
  "Starts a series of threads that read items one at a time off the channel then creates a tombstone
  and unindexes them. Returns when all items have been processed or an error has occured."
  [system tuples-chan]
  (let [thread-chans (for [n (range 0 (db-sync-num-delete-threads))
                           :let [log (fn [& args]
                                       (debug "process-deletes" n ":" (str/join " " args)))]]
                       (async/thread
                         (log "starting")
                         (try
                           (util/while-let [[concept-id revision-id] (<!! tuples-chan)]
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
  [system provider-id concept-type params]
  (info "Synchronizing deletes for" provider-id concept-type)
  (add-deleted-items-to-work-table system provider-id concept-type params)
  (->> (process-items-from-work-table system)
       (map-extra-items-batches-to-deletes system provider-id concept-type)
       (process-deletes system)))

(defn synchronize-databases
  "Finds differences in Metadata DB and Catalog REST and brings Metadata DB along with the indexer
  back in sync with Catalog REST."
  [system params]
  (let [{:keys [sync-types provider-id]} params
        sync-types (set sync-types)
        providers (filter #(or (nil? provider-id) (= % provider-id))
                          (provider-service/get-providers {:system system}))
        params (dissoc params :provider-id :sync-types)
        ;; start date and end date are only supported for updates
        params-without-dates (dissoc params :start-date :end-date)]
    (doseq [provider providers]
      ;; Collections
      (when (:missing sync-types)
        (synchronize-missing system provider :collection params-without-dates))
      (when (:updates sync-types)
        (synchronize-updates system provider :collection params))
      (when (:deletes sync-types)
        (synchronize-deletes system provider :collection params-without-dates))
      ;; Granules
      (when (:missing sync-types)
        (synchronize-missing system provider :granule params-without-dates))
      (when (:updates sync-types)
        (synchronize-updates system provider :granule params))
      (when (:deletes sync-types)
        (synchronize-deletes system provider :granule params-without-dates))))
  (info "Synchronization complete"))


;; Background task to handle provider bulk index requests
(defn handle-db-synchronization-requests
  "Handle any requests for indexing providers."
  [system]
  (info "Starting background task for monitoring db synchronize channel.")
  (let [channel (:db-synchronize-channel system)]
    (async/thread
      (while true
        (try ; catch any errors and log them, but don't let the thread die
          (let [params (<!! channel)]
            (synchronize-databases system params))
          (catch Throwable e
            (error e (.getMessage e))))))))

