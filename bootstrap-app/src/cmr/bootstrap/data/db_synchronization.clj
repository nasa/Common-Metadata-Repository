(ns cmr.bootstrap.data.db-synchronization
  "TODO"
  (:require [clojure.core.async :as async :refer [go >! go-loop <! <!!]]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [sqlingvo.core :as sql :refer [sql select insert from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]
            [cmr.bootstrap.data.migration-utils :as mu]
            [cmr.common.util :as util]
            [cmr.common.concepts :as concepts]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.metadata-db.data.oracle.sql-utils :as sql-utils]
            [cmr.metadata-db.data.oracle.concepts :as mdb-concepts]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.provider-service :as provider-service]))

;; TODO document and rename
(def BATCH_SIZE 1000)

(comment


  async/timeout
  async/alts!!

  (let [main (async/chan)]
    (go
      (Thread/sleep 200)
      (>! main :hello))
    (println (async/alts!! [main (async/timeout 500)]))
    )


  (def system (get-in user/system [:apps :bootstrap]))

  (add-missing-items-to-work-table system "CPROV1" :collection)

  (get-work-items-batch system 1 10)

  (get-latest-concept-id-revision-ids system "CPROV1" :collection (get-work-items-batch system 1 10))

  (get-concept-from-catalog-rest system "CPROV1" :collection "C1-CPROV1" 1)


)

(defn add-missing-items-to-work-table
  "TODO"
  [system provider-id concept-type]

  ;; TODO this sql should be updated to find items that were INGEST_UPDATED_AT > start date and
  ;; INGEST_UPDATED_AT < end date
  (let [delete-sql "delete from sync_work"
        sql (format "insert into sync_work (id, concept_id)
                    select ROWNUM, %s from %s"
                    (mu/concept-type->catalog-rest-id-field concept-type)
                    (mu/catalog-rest-table system provider-id concept-type))]
    (j/with-db-transaction
      [conn (:db system)]
      (j/execute! conn [delete-sql])
      (j/execute! conn [sql]))))

(defn get-work-items-batch
  "Selects n work items from the table starting at the given index and retrieving up to n items"
  [system start-index n]
  (let [sql "select id, concept_id from sync_work where id >= ? and id < ?"
        stmt [sql start-index (+ start-index n)]]
    (mapv :concept_id (sql-utils/query (:db system) stmt))))

(defn- in-clause
  "TODO"
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
  ;; TODO handle the case that the item doesn't exist in the metadata db
  ;; It won't be returned from this query and should be revision id 1
  (let [sql (format
              "select concept_id, revision_id from %s where %s"
              (tables/get-table-name provider-id concept-type)
              (in-clause "concept_id" (count concept-ids)))
        stmt (cons sql concept-ids)]
    (mapv (fn [{:keys [concept_id revision_id]}]
            {:concept-id concept_id
             :revision-id (long revision_id)})
          (sql-utils/query (:db system) stmt))))

(defmulti get-concept-from-catalog-rest
  "TODO"
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
  ;; TODO implement this
  )

(defn- <!t
  "Similar to the existing core.async read method except can timeout. Returns nil if it times out.
  Must be called within a go block."
  ([c]
   (<!t c 15000))
  ([c msecs]

   (let [timeout-chan (async/timeout msecs)
         [v port] (async/alts! [c (async/timeout msecs)] :priority true)]
     (when (identical? timeout-chan port)
       ;; a timeout occurred
       (throw (Exception. "Timeout reading")))
     v)))

(defn- <!!t
  "Similar to the existing core.async read method except can timeout. Returns nil if it times out."
  ([c]
   (<!t c 15000))
  ([c msecs]
   (let [timeout-chan (async/timeout msecs)
         [v port] (async/alts!! [c (async/timeout msecs)] :priority true)]
     (when (identical? timeout-chan port)
       ;; a timeout occurred
       (throw (Exception. "Timeout reading")))
     v)))

(defn process-items-from-work-table
  "TODO"
  [system]
  (let [missing-items-chan (async/chan 1)]
    (go
      (debug "process-items-from-work-table starting")
      (try
        (loop [start-index 1]
          (when-let [items (seq (get-work-items-batch system start-index BATCH_SIZE))]
            (do
              (debug "process-items-from-work-table: Found" (count items) "items")
              (>! missing-items-chan items)
              (recur (+ start-index (count items))))))
        (finally
          (async/close! missing-items-chan)
          (debug "process-items-from-work-table completed"))))
    missing-items-chan))

(defn map-missing-items-to-concepts
  "TODO"
  [system provider-id concept-type missing-items-chan]
  (let [concepts-chan (async/chan 10)]
    (go
      (debug "map-missing-items-to-concepts starting")
      (try
        (loop []
          (when-let [items (<! missing-items-chan)]
            (do
              (debug "map-missing-items-to-concepts: Received" (count items) "items")
              (doseq [{:keys [concept-id revision-id]} (get-latest-concept-id-revision-ids
                                                         system provider-id concept-type items)]
                (debug "map-missing-items-to-concepts: Processing" concept-id "-" revision-id)
                (>! concepts-chan (get-concept-from-catalog-rest
                                    system provider-id concept-type concept-id (inc revision-id))))
              (recur))))
        (finally
          (async/close! concepts-chan)
          (async/close! missing-items-chan)
          (debug "map-missing-items-to-concepts completed"))))
    concepts-chan))


(def NUM_PROCESS_INSERT_THREADS
  "TODO"
  5)

(defn process-concept-insert
  "TODO"
  [system concept]

  (concept-service/save-concept {:system system} concept)

  ;; Things that could happen between finding a concept to copy and actually trying to insert it.
  ;; - It's the first time an item is going to be sent to the metadata db. In the meantime the item
  ;; could have been had any of the following
  ;;   - deleted -> The delete would be sent to ingest and return a 404 to Cat Rest. We would go
  ;; ahead and copy the item into metadata db. It would then be found during the delete synchronization.
  ;;   - updated -> The update would arrive in Mdb as the save revision id we're trying to save with.
  ;; A conflict would be found and thrown.
  ;; - An item is already in metadata db. Catalog REST got an update that wasn't sent to metadata db.
  ;; While we're synching that update another one comes:
  ;;   - delete -> The delete would result in a tombstone in mdb. That should be a revision conflict
  ;; when we try to save.
  ;;   - update -> The update would result in a new revision in mdb. That should be a revision
  ;; conflict when we try to save.


  ;; TODO index using the indexer


  ;; TODO catch conflict failures and log them. They're ok.

  )

(defn process-concept-inserts
  [system concepts-chan]
  (let [thread-chans (for [n (range 0 NUM_PROCESS_INSERT_THREADS)
                           :let [log (fn [& args]
                                       (debug "process-concept-inserts" n ":" (str/join " " args)))]]
                       (async/thread
                         (log "starting")
                         (try
                           (loop []
                             (when-let [concept (<!! concepts-chan)]
                               (do
                                 (log "Inserting" (:concept-id concept))
                                 (process-concept-insert system concept)
                                 (recur))))
                           (finally
                             (async/close! concepts-chan)
                             (log "completed")))))]
    ;; Force iteration over all thread chans
    (doseq [thread-chan (doall thread-chans)]
      ;; Wait for the thread to close the channel indicating it's done.
      (<!! thread-chan))))


(defn synchronize-inserts
  "TODO"
  [system provider-id concept-type]
  (info "Synchronizing" concept-type "inserts for" provider-id)
  (add-missing-items-to-work-table system provider-id concept-type)
  (->> (process-items-from-work-table system)
       (map-missing-items-to-concepts system provider-id concept-type)
       (process-concept-inserts system)))

(defn synchronize-deletes
  "TODO"
  [system provider-id concept-type]
  ;; TODO implement this

  )

(defn synchronize-databases
  "TODO"
  [system]
  ;; TODO pass the start date and end data around to make the select performance better
  ;; TODO get providers from metadata db
  (doseq [provider (provider-service/get-providers {:system system})]
    (synchronize-inserts system provider :collection)
    (synchronize-deletes system provider :collection)

    ;; TODO add granules

    ))