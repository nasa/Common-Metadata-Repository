(ns cmr.search.services.association-validation
  "This contains functions for validating the business rules of an association between a collection
   and a tag or a variable."
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.services.tagging.json-schema-validation :as jv]
   [cmr.search.services.tagging.tag-validation :as tv]
   [cmr.search.services.messages.association-messages :as assoc-msg]
   [cmr.transmit.metadata-db :as mdb]))

(defn associations-json->associations
  "Validates the associations json and returns the parsed json"
  [associations-json]
  (jv/validate-associations-json (tv/sanitized-json associations-json))
  (->> (json/parse-string associations-json true)
       (map #(set/rename-keys % {:concept_id :concept-id :revision_id :revision-id}))))

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

(defn- append-error
  "Returns the association with the given error message appended to it."
  [association error-msg]
  (update association :errors concat [error-msg]))

(defn- validate-collection-concept-id
  "Validates the association collection concept-id is not in the set of inaccessible-concept-ids,
  returns the association with errors appended if applicable."
  [inaccessible-concept-ids association]
  (if (contains? inaccessible-concept-ids (:concept-id association))
    (append-error association (assoc-msg/inaccessible-collection (:concept-id association)))
    association))

(defn- validate-collection-revision
  "Validates the association collection revision is not in the set of tombstone-coll-revisions
  and inaccessible-coll-revisions, returns the association with errors appended if applicable."
  [assoc-type tombstone-coll-revisions inaccessible-coll-revisions association]
  (let [coll-revision (select-keys association [:concept-id :revision-id])]
    (if (contains? tombstone-coll-revisions coll-revision)
      (append-error association (assoc-msg/tombstone-collection assoc-type coll-revision))
      (if (contains? inaccessible-coll-revisions coll-revision)
        (append-error association (assoc-msg/inaccessible-collection-revision coll-revision))
        association))))

(defmulti validate-association-conflict-for-collection
  "Validate the given association does not conflict with existing tag associations in that
  a tag/variable cannot be associated with a collection revision and the same collection
  without revision at the same time."
  (fn [context assoc-type assoc-id association]
    assoc-type))

(defmethod validate-association-conflict-for-collection :tag
  [context assoc-type tag-key tag-association]
  (let [tas (->> (mdb/find-concepts context
                                    {:tag-key tag-key
                                     :associated-concept-id (:concept-id tag-association)
                                     :exclude-metadata true
                                     :latest true}
                                    :tag-association)
                 (filter #(not (:deleted %))))
        coll-revision-ids (map #(get-in % [:extra-fields :associated-revision-id]) tas)
        not-nil-revision-ids (remove nil? coll-revision-ids)]
    (cond
      ;; there is no existing tag association found, no need to validate
      (= 0 (count coll-revision-ids))
      nil

      ;; there are existing tag associations and they are all on collection revisions
      (= (count coll-revision-ids) (count not-nil-revision-ids))
      (when-not (:revision-id tag-association)
        (format (str "There are already tag associations with tag key [%s] on collection [%s] "
                     "revision ids [%s], cannot create tag association on the same collection without revision id.")
                tag-key (:concept-id tag-association) (string/join ", " coll-revision-ids)))

      ;; there are existing tag associations and they are all on collection without revision
      (= 0 (count not-nil-revision-ids))
      (when-let [revision-id (:revision-id tag-association)]
        (format (str "There are already tag associations with tag key [%s] on collection [%s] "
                     "without revision id, cannot create tag association on the same collection "
                     "with revision id [%s].")
                tag-key (:concept-id tag-association) revision-id))

      ;; there are conflicts within the existing tag associations in metadata-db already
      :else
      (format (str "Tag can only be associated with a collection or a collection revision, "
                   "never both at the same time. There are already conflicting tag associations "
                   "in metadata-db with tag key [%s] on collection [%s] , please delete "
                   "one of the conflicting tag associations.")
              tag-key (:concept-id tag-association)))))

(defmethod validate-association-conflict-for-collection :variable
  [context assoc-type variable-concept-id variable-association]
  (let [vas (->> (mdb/find-concepts context
                                    {:variable-concept-id variable-concept-id
                                     :associated-concept-id (:concept-id variable-association)
                                     :exclude-metadata true
                                     :latest true}
                                    :variable-association)
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

(defmethod validate-association-conflict-for-collection :service
  [context assoc-type service-concept-id service-association]
  (let [vas (->> (mdb/find-concepts context
                                    {:service-concept-id service-concept-id
                                     :associated-concept-id (:concept-id service-association)
                                     :exclude-metadata true
                                     :latest true}
                                    :service-association)
                 (filter #(not (:deleted %))))
        coll-revision-ids (map #(get-in % [:extra-fields :associated-revision-id]) vas)
        not-nil-revision-ids (remove nil? coll-revision-ids)]
    (cond
      ;; there is no existing service association found, no need to validate
      (= 0 (count coll-revision-ids))
      nil

      ;; there are existing service associations and they are all on collection revisions
      (= (count coll-revision-ids) (count not-nil-revision-ids))
      (when-not (:revision-id service-association)
        (format (str "There are already service associations with service concept id [%s] on "
                     "collection [%s] revision ids [%s], cannot create service association "
                     "on the same collection without revision id.")
                service-concept-id (:concept-id service-association) (string/join ", " coll-revision-ids)))

      ;; there are existing service associations and they are all on collection without revision
      (= 0 (count not-nil-revision-ids))
      (when-let [revision-id (:revision-id service-association)]
        (format (str "There are already service associations with service concept id [%s] on "
                     "collection [%s] without revision id, cannot create service association "
                     "on the same collection with revision id [%s].")
                service-concept-id (:concept-id service-association) revision-id))

      ;; there are conflicts within the existing service associations in metadata-db already
      :else
      (format (str "Service can only be associated with a collection or a collection revision, "
                   "never both at the same time. There are already conflicting service associations "
                   "in metadata-db with service concept id [%s] on collection [%s] , "
                   "please delete one of the conflicting service associations.")
              service-concept-id (:concept-id service-association)))))

(defn- validate-association-conflict
  "Validates the association (either on a specific revision or over the whole collection)
  does not conflict with one or more existing associations in Metadata DB. Tag/Variable
  cannot be associated with a collection revision and the same collection without revision
  at the same time. Returns the association with errors appended if applicable."
  [context assoc-type assoc-id association]
  (if-let [error-msg (validate-association-conflict-for-collection
                       context assoc-type assoc-id association)]
    (append-error association error-msg)
    association))

(defn- validate-collection-identifier
  "Validates the association concept-id and revision-id (if given) satisfy association rules,
  i.e. collection specified exist and are viewable by the token,
  collection specified are not tombstones."
  [context assoc-type inaccessible-concept-ids tombstone-coll-revisions
    inaccessible-coll-revisions association]
  (if (:revision-id association)
    (validate-collection-revision
      assoc-type tombstone-coll-revisions inaccessible-coll-revisions association)
    (validate-collection-concept-id inaccessible-concept-ids association)))

(defn- validate-association-data
  "Validates the association data are within the maximum length requirement after written in JSON."
  [assoc-type association]
  (if (> (count (json/generate-string (:data association))) jv/maximum-data-length)
    (append-error association (assoc-msg/association-data-too-long assoc-type association))
    association))

(defn- validate-empty-associations
  "Validates the given association is not empty, throws service error if it is."
  [assoc-type associations]
  (when-not (seq associations)
    (errors/throw-service-error :invalid-data (assoc-msg/no-associations assoc-type))))

(defn- validate-conflicts-within-request
  "Validates the two lists have no intersection, otherwise there are conflicts within the same
  request, throws service error if conflicts are found."
  [assoc-type concept-id-only-assocs revision-assocs]
  (let [conflict-assocs (set/intersection (set (map :concept-id concept-id-only-assocs))
                                       (set (map :concept-id revision-assocs)))]
    (when (seq conflict-assocs)
      (errors/throw-service-error
        :invalid-data (assoc-msg/conflict-associations assoc-type conflict-assocs)))))

(defn- partition-associations
  "Returns the associations as a list partitioned by if there is a revision-id."
  [associations]
  (let [has-revision-id? #(contains? % :revision-id)
        {concept-id-only-assocs false revision-assocs true} (group-by has-revision-id? associations)]
    [concept-id-only-assocs revision-assocs]))

(defmulti validate-associations
  "Validates the associations for the given association type (:tag or :variable) based on the
  operation type, which is either :insert or :delete. Id is the identifier value of the tag or
  variable that is associated with the collections. Returns the associations with errors found
  appended to them. If the provided associations fail the basic rules validation (e.g. empty
  associations, conflicts within the request), throws a service error."
  (fn [context assoc-type assoc-id associations operation-type]
    operation-type))

(defmethod validate-associations :insert
  [context assoc-type assoc-id associations operation-type]
  (validate-empty-associations assoc-type associations)
  (let [[concept-id-only-assocs revision-assocs] (partition-associations associations)
        _ (validate-conflicts-within-request assoc-type concept-id-only-assocs revision-assocs)
        inaccessible-concept-ids (get-inaccessible-concept-ids
                                   context (map :concept-id concept-id-only-assocs))
        {:keys [tombstones inaccessibles]} (get-bad-collection-revisions context associations)]
    (->> associations
         (map #(validate-collection-identifier
                 context assoc-type inaccessible-concept-ids tombstones inaccessibles %))
         (map #(validate-association-data assoc-type %))
         (map #(validate-association-conflict context assoc-type assoc-id %)))))

(defmethod validate-associations :delete
  [context assoc-type assoc-id associations operation-type]
  (validate-empty-associations assoc-type associations)
  (let [[concept-id-only-assocs revision-assocs] (partition-associations associations)
        inaccessible-concept-ids (get-inaccessible-concept-ids
                                   context (map :concept-id concept-id-only-assocs))
        {:keys [tombstones inaccessibles]} (get-bad-collection-revisions context associations)]
    (->> associations
         (map #(validate-collection-identifier
                 context assoc-type inaccessible-concept-ids tombstones inaccessibles %)))))
