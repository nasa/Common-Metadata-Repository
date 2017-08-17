(ns cmr.indexer.services.index-service
  "Provide functions to index concept"
  (:require
    [cheshire.core :as cheshire]
    [clj-time.core :as t]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clojure.edn :as edn]
    [clojure.string :as s]
    [cmr.acl.acl-fetcher :as acl-fetcher]
    [cmr.common.cache :as cache]
    [cmr.common.concepts :as cs]
    [cmr.common.config :refer [defconfig]]
    [cmr.common.date-time-parser :as date]
    [cmr.common.lifecycle :as lifecycle]
    [cmr.common.log :as log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.time-keeper :as tk]
    [cmr.common.util :as util]
    [cmr.elastic-utils.connect :as es-util]
    [cmr.indexer.config :as config]
    [cmr.indexer.data.concept-parser :as cp]
    [cmr.indexer.data.elasticsearch :as es]
    [cmr.indexer.data.humanizer-fetcher :as humanizer-fetcher]
    [cmr.indexer.data.index-set :as idx-set]
    [cmr.indexer.data.metrics-fetcher :as metrics-fetcher]
    [cmr.message-queue.config :as qcfg]
    [cmr.message-queue.services.queue :as queue]
    [cmr.message-queue.queue.queue-protocol :as queue-protocol]
    [cmr.transmit.cubby :as cubby]
    [cmr.transmit.echo.rest :as rest]
    [cmr.transmit.index-set :as tis]
    [cmr.transmit.metadata-db :as meta-db]
    [cmr.transmit.metadata-db2 :as meta-db2]
    [cmr.umm.umm-core :as umm]))

(defconfig use-doc-values-fields
  "Indicates whether search fields should use the doc-values fields or not. If false the field data
  cache fields will be used. This is a temporary configuration to toggle the feature off if there
  are issues. It is duplicated from the search application."
  {:type Boolean
   :default true})

(def query-field->elastic-doc-values-fields
  "Maps the query-field names to the field names used in elasticsearch when using doc-values. Field
  names are excluded from this map if the query field name matches the field name in elastic search."
  {:granule {:provider-id :provider-id-doc-values
             :collection-concept-id :collection-concept-id-doc-values}})

(defn query-field->elastic-field
  "Returns the elastic field name for the equivalent query field name. Duplicated the mappings from
  the search application here."
  [field concept-type]
  (if (use-doc-values-fields)
    (get-in query-field->elastic-doc-values-fields [concept-type field] field)
    field))

(defn filter-expired-concepts
  "Remove concepts that have an expired delete-time."
  [batch]
  (filter (fn [concept]
            (let [delete-time-str (get-in concept [:extra-fields :delete-time])
                  delete-time (when delete-time-str
                                (date/parse-datetime delete-time-str))]
              (or (nil? delete-time)
                  (t/after? delete-time (tk/now)))))
          batch))

(defmulti prepare-batch
  "Returns the batch of concepts into elastic docs for bulk indexing."
  (fn [context batch options]
    (cs/concept-id->type (:concept-id (first batch)))))

(defmethod prepare-batch :default
  [context batch options]
  (es/prepare-batch context (filter-expired-concepts batch) options))

(defmethod prepare-batch :collection
  [context batch options]
  ;; Get the tag associations and variable associations as well.
  (let [batch (map (fn [concept]
                     (let [tag-associations (meta-db/get-associations-for-collection
                                             context concept :tag-association)
                           variable-associations (meta-db/get-associations-for-collection
                                                  context concept :variable-association)]
                       (-> concept
                           (assoc :tag-associations tag-associations)
                           (assoc :variable-associations variable-associations))))
                   batch)]
    (es/prepare-batch context (filter-expired-concepts batch) options)))

(defmethod prepare-batch :variable
  [context batch options]
  ;; Get the variable associations as well.
  (let [batch (map (fn [concept]
                     (let [variable-associations (meta-db/get-associations-for-variable
                                                  context concept)]
                       (assoc concept :variable-associations variable-associations)))
                   batch)]
    (es/prepare-batch context (filter-expired-concepts batch) options)))

(defn bulk-index
  "Index many concepts at once using the elastic bulk api. The concepts to be indexed are passed
  directly to this function - it does not retrieve them from metadata db (tag associations for
  collections WILL be retrieved, however). The bulk API is invoked repeatedly if necessary -
  processing batch-size concepts each time. Returns the number of concepts that have been indexed.

  Valid options:
  * :all-revisions-index? - true indicates this should be indexed into the all revisions index
  * :force-version? - true indicates that we should overwrite whatever is in elasticsearch with the
  latest regardless of whether the version in the database is older than the _version in elastic."
  ([context concept-batches]
   (bulk-index context concept-batches nil))
  ([context concept-batches options]
   (reduce (fn [num-indexed batch]
             (let [batch (prepare-batch context batch options)]
               (es/bulk-index-documents context batch options)
               (+ num-indexed (count batch))))
           0
           concept-batches)))

(defn- get-max-revision-date
  "Takes a batch of concepts to index and returns the maximum revision date."
  [batch previous-max-revision-date]
  (->> batch
       ;; Get the revision date of each item
       (map :revision-date)
       ;; Parse the date
       (map #(f/parse (f/formatters :date-time) %))
       ;; Add on the last date
       (cons previous-max-revision-date)
       ;; Remove nil because previous-max-revision-date could be nil
       (remove nil?)
       (apply util/max-compare)))

(defn bulk-index-with-revision-date
  "See documentation for bulk-index. This is a temporary function added for supporting replication
  using DMS. It does the same work as bulk-index, but instead of returning the number of concepts
  indexed it returns a map with keys of :num-indexed and :max-revision-date."
  ([context concept-batches]
   (bulk-index-with-revision-date context concept-batches {}))
  ([context concept-batches options]
   (reduce (fn [{:keys [num-indexed max-revision-date]} batch]
             (let [max-revision-date (get-max-revision-date batch max-revision-date)
                   batch (prepare-batch context batch options)]
               (es/bulk-index-documents context batch options)
               {:num-indexed (+ num-indexed (count batch))
                :max-revision-date max-revision-date}))
           {:num-indexed 0 :max-revision-date nil}
           concept-batches)))

(defn- indexing-applicable?
  "Returns true if indexing is applicable for the given concept-type and all-revisions-index? flag.
  Indexing is applicable for all concept types if all-revisions-index? is false and only for
  collection, tag-association and variable-association concept types if all-revisions-index? is true."
  [concept-type all-revisions-index?]
  (or (not all-revisions-index?)
      (and all-revisions-index? (contains? #{:collection :tag-association :variable-association} concept-type))))

(def REINDEX_BATCH_SIZE 2000)

(defn reindex-provider-collections
  "Reindexes all the collections in the providers given.

  The optional all-revisions-index? will cause the following behavior changes:
  * nil - both latest and all revisions will be indexed.
  * true - only all revisions will be indexed
  * false - only the latest revisions will be indexed"
  ([context provider-ids]
   (reindex-provider-collections
     context provider-ids {:all-revisions-index? nil :refresh-acls? true :force-version? false}))
  ([context provider-ids {:keys [all-revisions-index? refresh-acls? force-version?]}]

   ;; We refresh this cache because it is fairly lightweight to do once for each provider and because
   ;; we want the latest humanizers on each of the Indexer instances that are processing these messages.
   (humanizer-fetcher/refresh-cache context)
   (metrics-fetcher/refresh-cache context)

   (if refresh-acls?
     ;; Refresh the ACL cache.
     ;; We want the latest permitted groups to be indexed with the collections
     (acl-fetcher/refresh-acl-cache context)
     ;; Otherwise we'll make sure we check cubby to make sure we have a consistent set of ACLs.
     ;; Ingest usually refreshes the cache and then sends a message without the flag relying on
     ;; cubby to indicate to indexers that they should fetch the latest ACLs. Without expiring
     ;; these here we'll think we have the latest.
     (acl-fetcher/expire-consistent-cache-hashes context))

   (doseq [provider-id provider-ids]
     (when (or (nil? all-revisions-index?) (not all-revisions-index?))
       (info "Reindexing latest collections for provider" provider-id)
       (let [latest-collection-batches (meta-db/find-in-batches
                                         context
                                         :collection
                                         REINDEX_BATCH_SIZE
                                         {:provider-id provider-id :latest true})]
         (bulk-index context latest-collection-batches {:all-revisions-index? false
                                                        :force-version? force-version?})))

     (when (or (nil? all-revisions-index?) all-revisions-index?)
       ;; Note that this will not unindex revisions that were removed directly from the database.
       ;; We will handle that with the index management epic.
       (info "Reindexing all collection revisions for provider" provider-id)
       (let [all-revisions-batches (meta-db/find-in-batches
                                     context
                                     :collection
                                     REINDEX_BATCH_SIZE
                                     {:provider-id provider-id})]
         (bulk-index context all-revisions-batches {:all-revisions-index? true
                                                    :force-version? force-version?}))))))

(defn reindex-tags
  "Reindexes all the tags. Only the latest revisions will be indexed"
  [context]
  (info "Reindexing tags")
  (let [latest-tag-batches (meta-db/find-in-batches
                             context
                             :tag
                             REINDEX_BATCH_SIZE
                             {:latest true})]
    (bulk-index context latest-tag-batches)))

(defn- log-ingest-to-index-time
  "Add a log message indicating the time it took to go from ingest to completed indexing."
  [{:keys [concept-id revision-date]}]
  (let [now (tk/now)
        rev-datetime (f/parse (f/formatters :date-time) revision-date)]
    ;; Guard against revision-date that is set to the future by a provider or a test.
    (if (t/before? rev-datetime now)
      ;; WARNING: Splunk is dependent on this log message. DO NOT change this without updating
      ;; Splunk searches used by ops.
      (info (format "Concept [%s] took [%d] ms from start of ingest to become visible in search."
                    concept-id
                    (t/in-millis (t/interval rev-datetime now))))
      (warn (format
              "Cannot compute time from ingest to search visibility for [%s] with revision date [%s]."
              concept-id
              revision-date)))))

(defn- get-elastic-version-with-associations
  "Returns the elastic version of the concept and its associations"
  [context concept tag-associations variable-associations]
  (es/get-elastic-version
   (-> concept
       (assoc :tag-associations tag-associations)
       (assoc :variable-associations variable-associations))))

(defmulti get-elastic-version
  "Returns the elastic version of the concept"
  (fn [context concept]
    (:concept-type concept)))

(defmethod get-elastic-version :default
  [context concept]
  (:revision-id concept))

(defmethod get-elastic-version :collection
  [context concept]
  (let [tag-associations (meta-db/get-associations-for-collection context concept :tag-association)
        variable-associations (meta-db/get-associations-for-collection
                               context concept :variable-association)]
    (get-elastic-version-with-associations context concept tag-associations variable-associations)))

(defmethod get-elastic-version :variable
  [context concept]
  (let [variable-associations (meta-db/get-associations-for-variable context concept)]
    (get-elastic-version-with-associations context concept nil variable-associations)))

(defmulti get-tag-associations
  "Returns the tag associations of the concept"
  (fn [context concept]
    (:concept-type concept)))

(defmethod get-tag-associations :default
  [context concept]
  nil)

(defmethod get-tag-associations :collection
  [context concept]
  (meta-db/get-associations-for-collection context concept :tag-association))

(defmulti get-variable-associations
  "Returns the variable associations of the concept"
  (fn [context concept]
    (:concept-type concept)))

(defmethod get-variable-associations :default
  [context concept]
  nil)

(defmethod get-variable-associations :collection
  [context concept]
  (meta-db/get-associations-for-collection context concept :variable-association))

(defmethod get-variable-associations :variable
  [context concept]
  (meta-db/get-associations-for-variable context concept))

(defmulti index-concept
  "Index the given concept with the parsed umm record. Indexing tag association and variable
   association concept indexes the associated collection conept."
  (fn [context concept parsed-concept options]
    (:concept-type concept)))

(defmethod index-concept :default
  [context concept parsed-concept options]
  (let [{:keys [all-revisions-index?]} options
        {:keys [concept-id revision-id concept-type]} concept]
    (when (indexing-applicable? concept-type all-revisions-index?)
      (info (format "Indexing concept %s, revision-id %s, all-revisions-index? %s"
                    concept-id revision-id all-revisions-index?))
      (let [concept-mapping-types (idx-set/get-concept-mapping-types context)
            delete-time (get-in parsed-concept [:data-provider-timestamps :delete-time])]
        (when (or (nil? delete-time) (t/after? delete-time (tk/now)))
          (let [tag-associations (get-tag-associations context concept)
                variable-associations (get-variable-associations context concept)
                elastic-version (get-elastic-version-with-associations
                                  context concept tag-associations variable-associations)
                tag-associations (es/parse-non-tombstone-associations
                                  context tag-associations)
                variable-associations (es/parse-non-tombstone-associations
                                       context variable-associations)
                concept-indexes (idx-set/get-concept-index-names context concept-id revision-id
                                                                 options concept)
                es-doc (es/parsed-concept->elastic-doc
                         context
                         (-> concept
                             (assoc :tag-associations tag-associations)
                             (assoc :variable-associations variable-associations))
                         parsed-concept)
                elastic-options (-> options
                                    (select-keys [:all-revisions-index? :ignore-conflict?])
                                    (assoc :ttl (when delete-time
                                                  (t/in-millis (t/interval (tk/now) delete-time)))))]
            (es/save-document-in-elastic
              context
              concept-indexes
              (concept-mapping-types concept-type)
              es-doc
              concept-id
              revision-id
              elastic-version
              elastic-options)))))))

(defn- index-associated-collection
  "Index the associated collection conept of the given concept. This is used by indexing tag
   association and variable association. Indexing them is essentially indexing their associated
   collection concept."
  [context concept options]
  (let [{{:keys [associated-concept-id associated-revision-id]} :extra-fields} concept
        {:keys [all-revisions-index?]} options
        coll-concept (meta-db/get-latest-concept context associated-concept-id)
        assoc-to-latest-revision? (or (nil? associated-revision-id)
                                      (= associated-revision-id (:revision-id coll-concept)))
        need-to-index? (or all-revisions-index? assoc-to-latest-revision?)
        coll-concept (if (and need-to-index? (not assoc-to-latest-revision?))
                       (meta-db/get-concept context associated-concept-id associated-revision-id)
                       coll-concept)]
    (when need-to-index?
      (let [parsed-coll-concept (cp/parse-concept context coll-concept)]
        (index-concept context coll-concept parsed-coll-concept options)))))

(defn- index-variable
  "Index the associated variable concept of the given variable concept id."
  [context variable-concept-id]
  (let [var-concept (meta-db/get-latest-concept context variable-concept-id)
        parsed-var-concept (cp/parse-concept context var-concept)]
    (index-concept context var-concept parsed-var-concept {})))

(defn- index-associated-variable
  "Index the associated variable concept of the given variable association concept."
  [context concept]
  (index-variable context (get-in concept [:extra-fields :variable-concept-id])))

(defn- reindex-associated-variables
  "Reindex variables associated with the collection"
  [context coll-concept-id coll-revision-id]
  (let [var-associations (meta-db/get-associations-by-collection-concept-id
                          context coll-concept-id coll-revision-id :variable-association)]
    (doseq [association var-associations]
      (index-variable context (get-in association [:extra-fields :variable-concept-id])))))

(defmethod index-concept :tag-association
  [context concept parsed-concept options]
  (index-associated-collection context concept options))

(defmethod index-concept :variable-association
  [context concept parsed-concept options]
  (index-associated-collection context concept options)
  (index-associated-variable context concept))

(defn index-concept-by-concept-id-revision-id
  "Index the given concept and revision-id"
  [context concept-id revision-id options]
  (when-not (and concept-id revision-id)
    (errors/throw-service-error
      :bad-request
      (format "Concept-id %s and revision-id %s cannot be null" concept-id revision-id)))

  (let [{:keys [all-revisions-index?]} options
        concept-type (cs/concept-id->type concept-id)]
    (when (indexing-applicable? concept-type all-revisions-index?)
      (let [concept (meta-db/get-concept context concept-id revision-id)
            parsed-concept (cp/parse-concept context concept)]
        (index-concept context concept parsed-concept options)
        (log-ingest-to-index-time concept)))))

(defn- cascade-collection-delete
  "Performs the cascade actions of collection deletion,
  i.e. propagate collection deletion to granules and variables"
  [context concept-mapping-types concept-id revision-id]
  (doseq [index (idx-set/get-granule-index-names-for-collection context concept-id)]
    (es/delete-by-query
     context
     index
     (concept-mapping-types :granule)
     {:term {(query-field->elastic-field :collection-concept-id :granule)
             concept-id}}))
  ;; reindex variables associated with the collection
  (reindex-associated-variables context concept-id revision-id))

(defmulti delete-concept
  "Delete the concept with the given id"
  (fn [context concept-id revision-id options]
    (cs/concept-id->type concept-id)))

(defmethod delete-concept :default
  [context concept-id revision-id options]
  ;; Assuming ingest will pass enough info for deletion
  ;; We should avoid making calls to metadata db to get the necessary info if possible
  (let [{:keys [all-revisions-index?]} options
        concept-type (cs/concept-id->type concept-id)
        concept (meta-db/get-concept context concept-id revision-id)
        elastic-version (get-elastic-version context concept)]
    (when (indexing-applicable? concept-type all-revisions-index?)
      (info (format "Deleting concept %s, revision-id %s, all-revisions-index? %s"
                    concept-id revision-id all-revisions-index?))
      (let [index-names (idx-set/get-concept-index-names
                         context concept-id revision-id options)
            concept-mapping-types (idx-set/get-concept-mapping-types context)
            elastic-options (select-keys options [:all-revisions-index? :ignore-conflict?])]
        (if all-revisions-index?
          ;; save tombstone in all revisions collection index
          (let [es-doc (es/parsed-concept->elastic-doc context concept (:extra-fields concept))]
            (es/save-document-in-elastic
             context index-names (concept-mapping-types concept-type)
             es-doc concept-id revision-id elastic-version elastic-options))
          ;; delete concept from primary concept index
          (do
            (es/delete-document
             context index-names (concept-mapping-types concept-type)
             concept-id revision-id elastic-version elastic-options)
            ;; propagate collection deletion to granules and variables
            (when (= :collection concept-type)
              (cascade-collection-delete
               context concept-mapping-types concept-id revision-id))))))))

(defn- index-association-concept
  "Index the association concept identified by the given concept-id and revision-id."
  [context concept-id revision-id options]
  (let [concept (meta-db/get-concept context concept-id revision-id)]
    (index-concept context concept nil options)))

(defmethod delete-concept :tag-association
  [context concept-id revision-id options]
  ;; When tag association is deleted, we want to re-index the associated collection.
  ;; This is the same thing we do when a tag association is update. So we call the same function.
  (index-association-concept context concept-id revision-id options))

(defmethod delete-concept :variable-association
  [context concept-id revision-id options]
  ;; When variable association is deleted, we want to re-index the associated collection.
  ;; This is the same thing we do when a variable association is update. So we call the same function.
  (index-association-concept context concept-id revision-id options))

(defn force-delete-all-collection-revision
  "Removes a collection revision from the all revisions index"
  [context concept-id revision-id]
  (let [index-names (idx-set/get-concept-index-names
                      context concept-id revision-id {:all-revisions-index? true})
        concept-mapping-types (idx-set/get-concept-mapping-types context)
        elastic-options {:ignore-conflict? false
                         :all-revisions-index? true}]
    (es/delete-document
      context
      index-names
      (concept-mapping-types :collection)
      concept-id
      revision-id
      nil ;; Null is sent in as the elastic version because we don't want to set a version for this
      ;; delete. The collection is going to be gone now and should never be indexed again.
      elastic-options)))

(defn delete-provider
  "Delete all the concepts within the given provider"
  [context provider-id]
  ;; Only collections and granules are unindexed here. Other concepts related to the provider
  ;; may be unindexed in other places when a :provider-delete message is handled,
  ;; e.g. unindexing access groups in access-control-app.
  (info (format "Deleting provider-id %s" provider-id))
  (let [{:keys [index-names]} (idx-set/get-concept-type-index-names context)
        concept-mapping-types (idx-set/get-concept-mapping-types context)
        ccmt (concept-mapping-types :collection)]
    ;; delete collections
    (doseq [index (vals (:collection index-names))]
      (es/delete-by-query
       context
       index
       ccmt
       {:term {(query-field->elastic-field :provider-id :collection) provider-id}}))

    ;; delete the granules
    (doseq [index-name (idx-set/get-granule-index-names-for-provider context provider-id)]
      (es/delete-by-query
       context
       index-name
       (concept-mapping-types :granule)
       {:term {(query-field->elastic-field :provider-id :granule) provider-id}}))

    ;; delete the variables
    (doseq [index (vals (:variable index-names))]
      (es/delete-by-query
       context
       index
       (concept-mapping-types :variable)
       {:term {(query-field->elastic-field :provider-id :collection) provider-id}}))))

(defn publish-provider-event
  "Put a provider event on the message queue."
  [context msg]
  (let [queue-broker (get-in context [:system :queue-broker])
        exchange-name (config/provider-exchange-name)]
    (queue/publish-message queue-broker exchange-name msg)))

(defn reindex-all-collections
  "Reindexes all collections in all providers. This is only called in the indexer when humanizers
  are updated and we only index the latest collection revision."
  [context]
  (let [providers (map :provider-id (meta-db2/get-providers context))]
    (info "Sending events to reindex collections in all providers:" (pr-str providers))
    (doseq [provider-id providers]
      (publish-provider-event
        context
        {:action :provider-collection-reindexing
         :provider-id provider-id
         :all-revisions-index? false}))
    (info "Reindexing all collection events submitted.")))

(defn update-humanizers
  "Update the humanizer cache and reindex all collections"
  [context]
  (humanizer-fetcher/refresh-cache context)
  (reindex-all-collections context))

(defn reset
  "Delegates reset elastic indices operation to index-set app as well as resetting caches"
  [context]
  (cache/reset-caches context)
  (es/reset-es-store context)
  (cache/reset-caches context))

(defn update-indexes
  "Updates the index mappings and settings."
  [context params]
  (cache/reset-caches context)
  (es/update-indexes context params)
  (cache/reset-caches context))

(def health-check-fns
  "A map of keywords to functions to be called for health checks"
  {:elastic_search #(es-util/health % :db)
   :echo rest/health
   :cubby cubby/get-cubby-health
   :metadata-db meta-db2/get-metadata-db-health
   :index-set tis/get-index-set-health
   :message-queue (fn [context]
                    (when-let [qb (get-in context [:system :queue-broker])]
                      (queue-protocol/health qb)))})

(defn health
  "Returns the health state of the app."
  [context]
  (let [dep-health (util/remove-nil-keys (util/map-values #(% context) health-check-fns))
        ok? (every? :ok? (vals dep-health))]
    {:ok? ok?
     :dependencies dep-health}))
