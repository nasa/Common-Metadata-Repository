(ns cmr.metadata-db.data.oracle.search
  "Provides implementations of the cmr.metadata-db.data.concepts/ConceptStore protocol methods
  for retrieving concepts using parameters"
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.data.oracle.concepts :as oc]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as util]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.oracle.sql-helper :as sh]
            [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore))

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

(def common-columns
  "A set of common columns for all concept types."
  #{:native_id :concept_id :revision_date :metadata :deleted :revision_id :format})

(def concept-type->columns
  "A map of concept type to the columns for that type in the database."
  {:granule (into common-columns
                  [:provider_id :parent_collection_id :delete_time :granule_ur])
   :collection (into common-columns
                     [:provider_id :entry_title :entry_id :short_name :version_id :delete_time
                      :user_id])
   :tag (into common-columns [:user_id])
   :tag-association (into common-columns [:associated_concept_id :associated_revision_id :user_id])
   :access-group (into common-columns [:provider_id :user_id])
   :service (into common-columns [:provider_id :entry_title :entry_id :delete_time :user_id])})

(defn columns-for-find-concept
  "Returns the table columns that should be included in a find-concept sql query"
  [concept-type params]
  (let [exclude-metadata? (= "true" (:exclude-metadata params))
        all-fields (concept-type->columns concept-type)]
    (disj all-fields (when exclude-metadata? :metadata))))

(defn- params->sql-params
  "Converts the search params into params that can be converted into a sql condition clause."
  [provider params]
  (if (:small provider)
    (dissoc params :concept-type :exclude-metadata)
    (dissoc params :concept-type :exclude-metadata :provider-id)))

(defn- gen-find-concepts-in-table-sql
  "Create the SQL for the given params and table.
  If :include-all is true, all revisions of the concepts will be returned. This is needed for the
  find-latest-concepts function to later filter out the latest concepts that satisfy the search in memory."
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

(defmulti find-concepts-in-table
  "Retrieve concept maps from the given table, handling small providers separately from
  normal providers."
  (fn [db table concept-type providers params]
    (:small (first providers))))

;; Execute a query against the small providers table
(defmethod find-concepts-in-table true
  [db table concept-type providers params]
  (let [fields (columns-for-find-concept concept-type params)
        params (params->sql-params (first providers)
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
  {:pre [(== 1 (count providers))]}
  (let [provider (first providers)
        provider-id (:provider-id provider)
        fields (disj (columns-for-find-concept concept-type params) :provider_id)
        params (params->sql-params provider (assoc params :provider-id provider-id))
        stmt (gen-find-concepts-in-table-sql concept-type table fields params)]
    (j/with-db-transaction
      [conn db]
      ;; doall is necessary to force result retrieval while inside transaction - otherwise
      ;; connection closed errors will occur
      (doall (mapv #(oc/db-result->concept-map concept-type conn provider-id %)
                   (su/query conn stmt))))))

(extend-protocol c/ConceptSearch
  OracleStore

  (find-concepts
    [db providers params]
    {:pre [(coll? providers)]}
    (let [concept-type (:concept-type params)
          tables-to-providers (group-by #(tables/get-table-name % concept-type) providers)]
      (util/mapcatv (fn [[table provider-list]]
                      (find-concepts-in-table db table concept-type provider-list params))
                    tables-to-providers)))

  (find-concepts-in-batches
    ([db provider params batch-size]
     (c/find-concepts-in-batches db provider params batch-size 0))
    ([db provider params batch-size requested-start-index]
     (let [{:keys [concept-type]} params
           provider-id (:provider-id provider)
           params (params->sql-params provider params)
           table (tables/get-table-name provider concept-type)]
       (letfn [(find-batch
                 [start-index]
                 (j/with-db-transaction
                   [conn db]
                   (let [conditions [`(>= :id ~start-index)
                                     `(< :id ~(+ start-index batch-size))]
                         _ (debug "Finding batch for provider" provider-id "from id >=" start-index " and id <" (+ start-index batch-size))
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
                       (debug "Couldn't find batch so searching for more from" start-index)
                       (when-let [next-id (find-batch-starting-id db table params start-index)]
                         (debug "Found next-id of" next-id)
                         (lazy-find next-id)))
                     ;; We found a batch. Return it and the next batch lazily
                     (cons batch (lazy-seq (lazy-find (+ start-index batch-size)))))))]
         ;; If there's no minimum found so there are no concepts that match
         (when-let [start-index (find-batch-starting-id db table params)]
           (lazy-find (max requested-start-index start-index)))))))

  (find-latest-concepts
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
      (c/search-with-params latest-concepts params))))
