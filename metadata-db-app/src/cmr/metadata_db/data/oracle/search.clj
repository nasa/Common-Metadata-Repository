(ns cmr.metadata-db.data.oracle.search
  "Provides implementations of the cmr.metadata-db.data.concepts/ConceptStore protocol methods
  for retrieving concepts using parameters"
  (:require
   [clojure.java.jdbc :as j]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.concepts :as cc]
   [cmr.common.util :as util]
   [cmr.metadata-db.data.concepts :as c]
   [cmr.metadata-db.data.oracle.concept-tables :as tables]
   [cmr.metadata-db.data.oracle.concepts :as oc]
   [cmr.metadata-db.data.oracle.sql-helper :as sh]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import
   (cmr.oracle.connection OracleStore)))

(def association-concept-type->generic-association
  "Mapping of various association concept types to columns needed for migration to CMR_ASSOCIATIONS table."
  {:service-association {:association_type "SERVICE-COLLECTION" :kebab-key-mapping {:service-concept-id :source-concept-identifier}}
   :tag-association {:association_type "TAG-COLLECTION" :kebab-key-mapping {:tag-key :source-concept-identifier}}
   :tool-association {:association_type "TOOL-COLLECTION" :kebab-key-mapping {:tool-concept-id :source-concept-identifier}}
   :variable-association {:association_type "VARIABLE-COLLECTION" :kebab-key-mapping {:variable-concept-id :source-concept-identifier}}})

(def common-columns
  "A set of common columns for all concept types."
  #{:native_id :concept_id :revision_date :metadata :deleted :revision_id :format :transaction_id})

(def concept-type->columns
  "A map of concept type to the columns for that type in the database."
 (merge 
  {:granule (into common-columns
                  [:provider_id :parent_collection_id :delete_time :granule_ur])
   :collection (into common-columns
                     [:provider_id :entry_title :entry_id :short_name :version_id :delete_time
                      :user_id])
   :tag (into common-columns [:user_id])
   :tag-association (into common-columns
                          [:associated_concept_id :associated_revision_id
                           :source_concept_identifier :user_id])
   :access-group (into common-columns [:provider_id :user_id])
   :service (into common-columns [:provider_id :service_name :user_id])
   :tool (into common-columns [:provider_id :tool_name :user_id])
   :acl (into common-columns [:provider_id :user_id :acl_identity])
   :humanizer (into common-columns [:user_id])
   :subscription (into common-columns
                       [:provider_id :subscription_name :subscriber_id
                        :collection_concept_id :user_id
                        :normalized_query :subscription_type])
   :variable (into common-columns [:provider_id :variable_name :measurement :user_id :fingerprint])
   :variable-association (into common-columns
                               [:associated_concept_id :associated_revision_id
                                :source_concept_identifier :user_id])
   :service-association (into common-columns
                              [:associated_concept_id :associated_revision_id
                               :source_concept_identifier :user_id])
   :tool-association (into common-columns
                           [:associated_concept_id :associated_revision_id
                            :source_concept_identifier :user_id])
   :generic-association (into common-columns
                               [:associated_concept_id :associated_revision_id
                                :source_concept_identifier :source_revision_id :user_id])}
   (zipmap (cc/get-generic-concept-types-array)
           (repeat (into common-columns [:provider_id :document_name :schema :user_id :created_at])))))

(def single-table-with-providers-concept-type?
  "The set of concept types that are stored in a single table with a provider column. These concept
   types must include the provider id as part of the sql params"
  #{:access-group :variable :service :tool :subscription})

(defn columns-for-find-concept
  "Returns the table columns that should be included in a find-concept sql query"
  [concept-type params]
  (let [exclude-metadata? (= "true" (:exclude-metadata params))
        all-fields (concept-type->columns concept-type)]
    (disj all-fields (when exclude-metadata? :metadata))))

(defn- params->sql-params
  "Converts the search params into params that can be converted into a sql condition clause."
  [concept-type providers params]
  (if (or (every? :small providers) 
          (single-table-with-providers-concept-type? concept-type)
          (cc/generic-concept? concept-type))
    (dissoc params :concept-type :exclude-metadata)
    (dissoc params :concept-type :exclude-metadata :provider-id)))

(defmulti gen-find-concepts-in-table-sql
  "Create the SQL for the given params and table.
  If :include-all is true, all revisions of the concepts will be returned. This is needed for the
  find-latest-concepts function to later filter out the latest concepts that satisfy the search in memory."
  (fn [concept-type table fields params]
    (condp = concept-type
      :granule :granule-search
      concept-type)))

;; special case added to address OB_DAAC granule search not using existing index problem
;; where the native id or granule ur index is not being used by Oracle optimizer
(defmethod gen-find-concepts-in-table-sql :granule-search
  [concept-type table fields params]
  (let [stmt (gen-find-concepts-in-table-sql :dummy table fields params)]
    ;; add index hint to the generated sql statement
    (update-in stmt [0] #(string/replace % #"SELECT" (format "SELECT /*+ INDEX(%s) */" table)))))

(defmethod gen-find-concepts-in-table-sql :default
  [concept-type table fields params]
  (if (:include-all params)
    (let [params (dissoc params :include-all)]
      (su/build (select (vec fields)
                        (from table)
                        (where `(in :concept_id
                                    ~(select [:concept_id]
                                             (from table)
                                             (when-not (empty? params)
                                               (where (sh/find-params->sql-clause params)))))))))
    (su/build (select (vec fields)
                      (from table)
                      (when-not (empty? params)
                        (where (sh/find-params->sql-clause params)))))))

(defmethod gen-find-concepts-in-table-sql :subscription
  [_concept-type table fields params]
  (let [fields (cons :last_notified_at fields)
        root-query (gen-find-concepts-in-table-sql :default table fields params)
        updated-query (string/replace-first
                       (first root-query)
                       #"WHERE"
                       (str " LEFT JOIN cmr_sub_notifications"
                            " ON cmr_subscriptions.concept_id = cmr_sub_notifications.subscription_concept_id"
                            " WHERE"))]
    (cons updated-query (rest root-query))))

(defn- find-batch-starting-id
  "Returns the first id that would be returned in a batch."
  ([conn table params]
   (find-batch-starting-id conn table params 0))
  ([conn table params min-id]
   (let [existing-params (when (seq params) (sh/find-params->sql-clause params))
         params-clause (if existing-params
                         `(and (>= :id ~min-id)
                               ~existing-params)
                         `(>= :id ~min-id))
         stmt (select ['(min :id)]
                      (from table)
                      (where params-clause))]
     (-> (su/find-one conn stmt)
         vals
         first))))

(defn- append-stmt
  "Returns the sql statement string with the given statement part properly appended to the statement."
  [stmt-str part]
  (let [stmt-str (string/replace stmt-str #"where null" "")]
    (if (re-matches #"(?i).* where .*" stmt-str)
      (format "%s and %s" stmt-str part)
      (format "%s where %s" stmt-str part))))

(defn- find-batch-starting-id-with-stmt
  "Returns the first id that would be returned in a batch."
  ([conn stmt]
   (find-batch-starting-id-with-stmt conn stmt 0))
  ([conn stmt min-id]
   (let [stmt (-> stmt
                  (string/replace #"^select \*" "select min(id)")
                  (string/replace #"^select a\.\*" "select min(id)")
                  (append-stmt (format "id >= %s" min-id))
                  vector)]
     (-> (su/query conn stmt)
         first
         vals
         first))))

(defmulti find-concepts-in-table
  "Retrieve concept maps from the given table, handling small providers separately from
  normal providers."
  (fn [db table concept-type providers params]
    (or (:small (first providers))
        (= :variable concept-type)
        (= :service concept-type)
        (= :tool concept-type)
        (= :subscription concept-type)
        (cc/generic-concept? concept-type))))

;; Execute a query against a single table where provider_id is a column
(defmethod find-concepts-in-table true
  [db table concept-type providers params]
  (let [fields (columns-for-find-concept concept-type params)
        params (params->sql-params concept-type
                                   providers
                                   (assoc params :provider-id (map :provider-id providers)))
        stmt (gen-find-concepts-in-table-sql concept-type table fields params)]
    (j/with-db-transaction
      [conn db]
      (doall
        (mapv #(oc/db-result->concept-map concept-type conn (:provider_id %) %)
              (su/query conn stmt))))))

;; Execute a query against a normal (not small) provider table
(defmethod find-concepts-in-table :default
  [db table concept-type providers params]
  {:pre [(or (single-table-with-providers-concept-type? concept-type)
             (== 1 (count providers)))]}
  (let [provider-ids (map :provider-id providers)
        fields (disj (columns-for-find-concept concept-type params) :provider_id)
        params (params->sql-params concept-type providers (assoc params :provider-id provider-ids))
        params (if (or (= :variable-association concept-type)
                       (= :tag-association concept-type)
                       (= :service-association concept-type)
                       (= :tool-association concept-type))
                 (-> params
                     (set/rename-keys
                      (get-in association-concept-type->generic-association [concept-type :kebab-key-mapping]))
                     (assoc :association-type
                            (get-in association-concept-type->generic-association [concept-type :association_type])))
                 params)
        stmt (gen-find-concepts-in-table-sql concept-type table fields params)]
    (j/with-db-transaction
      [conn db]
      ;; doall is necessary to force result retrieval while inside transaction - otherwise
      ;; connection closed errors will occur
      (doall (mapv (fn [result]
                    (oc/db-result->concept-map concept-type conn
                                               (or (:provider_id result)
                                                   (:provider-id (first providers)))
                                               result))
                   (su/query conn stmt))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-concepts
  [db providers params]
  {:pre [(coll? providers)]}
  (let [concept-type (:concept-type params)
        tables-to-providers (group-by #(tables/get-table-name % concept-type) providers)]
    (util/mapcatv (fn [[table provider-list]]
                    (find-concepts-in-table db table concept-type provider-list params))
                  tables-to-providers)))

(defn find-concepts-in-batches
  ([db provider params batch-size]
   (find-concepts-in-batches db provider params batch-size 0))
  ([db provider params batch-size requested-start-index]
   (let [{:keys [concept-type]} params
         provider-id (:provider-id provider)
         params (params->sql-params concept-type [provider] params)
         table (tables/get-table-name provider concept-type)]
     (letfn [(find-batch
               [start-index]
               (j/with-db-transaction
                 [conn db]
                 (let [conditions [`(>= :id ~start-index)
                                   `(< :id ~(+ start-index batch-size))]
                       _ (info (format "Finding batch for provider [%s] concept type [%s] from id >= %s and id < %s"
                                       provider-id
                                       (name concept-type)
                                       start-index
                                       (+ start-index batch-size)))
                       conditions (if (empty? params)
                                    conditions
                                    (cons (sh/find-params->sql-clause params) conditions))
                       stmt (su/build (select [:*]
                                              (from table)
                                              (where (cons `and conditions))))
                       batch-result (su/query db stmt)]
                   (mapv (partial oc/db-result->concept-map concept-type conn provider-id)
                         batch-result))))
             (lazy-find
               [start-index]
               (let [batch (find-batch start-index)]
                 (if (empty? batch)
                   ;; We couldn't find any items  between start-index and start-index + batch-size
                   ;; Look for the next greatest id and to see if there's a gap that we can restart from.
                   (do
                     (info "Couldn't find batch so searching for more from" start-index)
                     (when-let [next-id (find-batch-starting-id db table params start-index)]
                       (info "Found next-id of" next-id)
                       (lazy-find next-id)))
                   ;; We found a batch. Return it and the next batch lazily
                   (cons batch (lazy-seq (lazy-find (+ start-index batch-size)))))))]
       ;; If there's no minimum found so there are no concepts that match
       (when-let [start-index (find-batch-starting-id db table params)]
         (lazy-find (max requested-start-index start-index)))))))

(defn find-concepts-in-batches-with-stmt
  ([db provider params stmt batch-size]
   (find-concepts-in-batches-with-stmt db provider params stmt batch-size 0))
  ([db provider params stmt batch-size requested-start-index]
   (let [{:keys [provider-id]} provider
         {:keys [concept-type]} params]
     (letfn [(find-batch
              [start-index]
              (j/with-db-transaction
               [conn db]
               (let [_ (info (format "Finding batch for provider [%s] concept type [%s] from id >= %s and id < %s"
                                     provider-id
                                     (name concept-type)
                                     start-index
                                     (+ start-index batch-size)))
                     id-constraint (format "id >= %s and id < %s"
                                           start-index (+ start-index batch-size))
                     batch-result (su/query db [(append-stmt stmt id-constraint)])]

                 (mapv (partial oc/db-result->concept-map concept-type conn provider-id)
                       batch-result))))
             (lazy-find
              [start-index]
              (let [batch (find-batch start-index)]
                (if (empty? batch)
                  ;; We couldn't find any items  between start-index and start-index + batch-size
                  ;; Look for the next greatest id and to see if there's a gap that we can restart from.
                  (do
                    (info "Couldn't find batch so searching for more from" start-index)
                    (when-let [next-id (find-batch-starting-id-with-stmt db stmt start-index)]
                      (info "Found next-id of" next-id)
                      (lazy-find next-id)))
                  ;; We found a batch. Return it and the next batch lazily
                  (cons batch (lazy-seq (lazy-find (+ start-index batch-size)))))))]
       ;; If there's no minimum found so there are no concepts that match
       (when-let [start-index (find-batch-starting-id-with-stmt db stmt)]
         (lazy-find (max requested-start-index start-index)))))))

(defn find-latest-concepts
  [db provider params]
  {:pre [(:concept-type params)]}
  ;; First we find all revisions of the concepts that have at least one revision that matches the
  ;; search parameters. Then we find the latest revisions of those concepts and match with the
  ;; search parameters again in memory to find what we are looking for.
  (let [concept-type (:concept-type params)
        table (tables/get-table-name provider concept-type)
        revision-concepts (find-concepts-in-table db table concept-type [provider]
                                                  (assoc params :include-all true))
        latest-concepts (->> revision-concepts
                             (group-by :concept-id)
                             (map (fn [[concept-id concepts]]
                                    (->> concepts (sort-by :revision-id) reverse first))))]
    (c/search-with-params latest-concepts params)))

(def behaviour
  {:find-concepts find-concepts
   :find-concepts-in-batches find-concepts-in-batches
   :find-concepts-in-batches-with-stmt find-concepts-in-batches-with-stmt
   :find-latest-concepts find-latest-concepts})

(extend OracleStore
        c/ConceptSearch
        behaviour)
