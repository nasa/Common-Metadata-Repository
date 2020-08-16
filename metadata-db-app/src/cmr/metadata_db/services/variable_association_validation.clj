(ns cmr.metadata-db.services.variable-association-validation
  "Functions for validating variable association to collection."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common-app.services.search.related-item-resolver :as related-item-resolver]
   [cmr.common.util :as util]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.search-service :as mdb-ss]))

(defmethod qe/concept-type-specific-query-processing :collection
  [context query]
  [context (related-item-resolver/resolve-related-item-conditions query context)])

(defn- append-error
  "Returns the association with the given error message appended to it."
  [association error-msg]
  (update association :errors concat [error-msg]))

(defn- validate-association-conflict-for-collection 
  "Make sure we don't have both associations with the collection revision-id and without revision-id."
  [context variable-concept-id variable-association]
  (let [vas (->> (mdb-ss/find-concepts context
                                      {:concept-type :variable-association
                                       :variable-concept-id variable-concept-id
                                       :associated-concept-id (:concept-id variable-association)
                                       :exclude-metadata true
                                       :latest true})
                 (filter #(not (:deleted %))))
        coll-revision-ids (map #(get-in % [:extra-fields :associated-revision-id]) vas)
        not-nil-revision-ids (remove nil? coll-revision-ids)]
    (cond
      ;; there is no existing variable association found, no need to validate
      (= 0 (count coll-revision-ids))
      nil

      ;; there are existing variable associations and they are all on collection revisions
      (= (count coll-revision-ids) (count not-nil-revision-ids))
      (when-not (:revision-id variable-association)
        (format (str "There are already variable associations with variable concept id [%s] on "
                     "collection [%s] revision ids [%s], cannot create variable association "
                     "on the same collection without revision id.")
                variable-concept-id (:concept-id variable-association) (string/join ", " coll-revision-ids)))

      ;; there are existing variable associations and they are all on collection without revision
      (= 0 (count not-nil-revision-ids))
      (when-let [revision-id (:revision-id variable-association)]
        (format (str "There are already variable associations with variable concept id [%s] on "
                     "collection [%s] without revision id, cannot create variable association "
                     "on the same collection with revision id [%s].")
                variable-concept-id (:concept-id variable-association) revision-id))

      ;; there are conflicts within the existing variable associations in metadata-db already
      :else
      (format (str "Variable can only be associated with a collection or a collection revision, "
                   "never both at the same time. There are already conflicting variable associations "
                   "in metadata-db with variable concept id [%s] on collection [%s] , "
                   "please delete one of the conflicting variable associations.")
              variable-concept-id (:concept-id variable-association)))))

(defn- validate-association-conflict
  "Validates the association (either on a specific revision or over the whole collection)
  does not conflict with one or more existing associations in Metadata DB. Tag/Variable
  cannot be associated with a collection revision and the same collection without revision
  at the same time. Returns the association with errors appended if applicable."
  [context assoc-var-id association]
  (if-let [error-msg (validate-association-conflict-for-collection
                       context assoc-var-id association)]
    (append-error association error-msg)
    association))

(defn- validate-collection-concept-id
  "Validates the association collection concept-id is not in the set of inaccessible-concept-ids,
  returns the association with errors appended if applicable."
  [inaccessible-concept-ids association]
  (if (contains? inaccessible-concept-ids (:concept-id association))
    (append-error association (msg/inaccessible-collection (:concept-id association)))
    association))

(defn- validate-collection-revision
  "Validates the association collection revision is not in the set of tombstone-coll-revisions
  and inaccessible-coll-revisions, returns the association with errors appended if applicable."
  [tombstone-coll-revisions inaccessible-coll-revisions association]
  (let [coll-revision (select-keys association [:concept-id :revision-id])]
    (if (contains? tombstone-coll-revisions coll-revision)
      (append-error association (msg/tombstone-collection :variable coll-revision))
      (if (contains? inaccessible-coll-revisions coll-revision)
        (append-error association (msg/inaccessible-collection-revision coll-revision))
        association))))

(defn- validate-collection-identifier
  "Validates the association concept-id and revision-id (if given) satisfy association rules,
  i.e. collection specified exist and are viewable by the token,
  collection specified are not tombstones."
  [context inaccessible-concept-ids tombstone-coll-revisions
    inaccessible-coll-revisions association]
  (if (:revision-id association)
    (validate-collection-revision
      tombstone-coll-revisions inaccessible-coll-revisions association)
    (validate-collection-concept-id inaccessible-concept-ids association)))

(defn- get-bad-collection-revisions
  "Returns the bad collection revisions of the given associations partitioned into a set of
  collection revisions that are tombstones and a set of collection revisions that are inaccessible."
  [context associations]
  (when (seq associations)
    (let [query (cqm/query {:concept-type :collection
                            :condition (cqm/string-conditions
                                         :concept-id (map :concept-id associations) true)
                            :page-size :unlimited
                            :result-format :query-specified
                            :result-fields [:concept-id :revision-id :deleted]
                            :all-revisions? true
                            :skip-acls? false})
          collections (->> (qe/execute-query context query)
                           :items
                           (map #(select-keys % [:concept-id :revision-id :deleted])))
          ids-fn (fn [coll] (select-keys coll [:concept-id :revision-id]))
          colls-set (set (map ids-fn associations))
          matched-colls (filter #(contains? colls-set (ids-fn %)) collections)
          tombstone-coll-revisions (set (map ids-fn (filter :deleted matched-colls)))
          inaccessible-coll-revisions (set/difference
                                        colls-set
                                        (set (map ids-fn matched-colls)))]
      {:tombstones tombstone-coll-revisions
       :inaccessibles inaccessible-coll-revisions})))

(defn- get-inaccessible-concept-ids
  "Returns the collection concept-ids within the given list that are invalid,
  i.e. the collections for the given concept-ids do not exist or are not viewable by the token."
  [context coll-concept-ids]
  (when (seq coll-concept-ids)
    (let [query (cqm/query {:concept-type :collection
                            :condition (cqm/string-conditions :concept-id coll-concept-ids true)
                            :page-size :unlimited
                            :result-format :query-specified
                            :result-fields [:concept-id]
                            :skip-acls? false})
          concept-ids (->> (qe/execute-query context query)
                           :items
                           (map :concept-id))]
      (set/difference (set coll-concept-ids) (set concept-ids)))))

(defn- partition-associations
  "Returns the associations as a list partitioned by if there is a revision-id."
  [associations]
  (let [has-revision-id? #(contains? % :revision-id)
        {concept-id-only-assocs false revision-assocs true} (group-by has-revision-id? associations)]
    [concept-id-only-assocs revision-assocs]))

(defn validate-associations
  "Validate if the collection is accessible or tombstoned, and if the association has conflict."
  [context assoc-var-id associations]
  (let [[concept-id-only-assocs revision-assocs] (partition-associations associations)
        inaccessible-concept-ids (get-inaccessible-concept-ids
                                   context (map :concept-id concept-id-only-assocs))
        {:keys [tombstones inaccessibles]} (get-bad-collection-revisions context associations)]
    (->> associations
         (map #(validate-collection-identifier
                 context inaccessible-concept-ids tombstones inaccessibles %))
         (map #(validate-association-conflict context assoc-var-id %)))))
