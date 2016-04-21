(ns cmr.search.services.tagging.tag-association-validation
  "This contains functions for validating the business rules of tag association."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-model :as cqm]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.search.services.tagging.tag-validation :as tv]
            [cmr.search.services.tagging.json-schema-validation :as jv]
            [cmr.search.services.tagging.tagging-service-messages :as msg]))

(defn tag-associations-json->tag-associations
  "Validates the tag associations json and returns the parsed json"
  [tag-associations-json]
  (jv/validate-tag-associations-json (tv/sanitized-json tag-associations-json))
  (->> (json/parse-string tag-associations-json true)
       (map util/map-keys->kebab-case)))

(defn- get-inaccessible-concept-ids
  "Returns the collection concept-ids within the given list that are invalid,
  i.e. the collections for the given concept-ids do not exist or are not viewable by the token."
  [context coll-concept-ids]
  (when (seq coll-concept-ids)
    (let [query (cqm/query {:concept-type :collection
                            :condition (cqm/string-conditions :concept-id coll-concept-ids true)
                            :page-size :unlimited
                            :result-format :query-specified
                            :fields [:concept-id]
                            :skip-acls? false})
          concept-ids (->> (qe/execute-query context query)
                           :items
                           (map :concept-id))]
      (set/difference (set coll-concept-ids) (set concept-ids)))))

(defn- get-bad-collection-revisions
  "Returns the bad collection revisions of the given tag associations partitioned into a set of
  collection revisions that are tombstones and a set of collection revisions that are inaccessible."
  [context tag-associations]
  (when (seq tag-associations)
    (let [query (cqm/query {:concept-type :collection
                            :condition (cqm/string-conditions
                                         :concept-id (map :concept-id tag-associations) true)
                            :page-size :unlimited
                            :result-format :query-specified
                            :fields [:concept-id :revision-id :deleted]
                            :all-revisions? true
                            :skip-acls? false})
          collections (->> (qe/execute-query context query)
                           :items
                           (map #(select-keys % [:concept-id :revision-id :deleted])))
          ids-fn (fn [coll] (select-keys coll [:concept-id :revision-id]))
          colls-set (set (map ids-fn tag-associations))
          matched-colls (filter #(contains? colls-set (ids-fn %)) collections)
          tombstone-coll-revisions (set (map ids-fn (filter :deleted matched-colls)))
          inaccessible-coll-revisions (set/difference
                                        colls-set
                                        (set (map ids-fn matched-colls)))]
      {:tombstones tombstone-coll-revisions
       :inaccessibles inaccessible-coll-revisions})))

(defn- append-error
  "Returns the tag association with the given error message appended to it."
  [tag-association error-msg]
  (update tag-association :errors concat [error-msg]))

(defn- validate-collection-concept-id
  "Validates the tag association collection concept-id is not in the set of inaccessible-concept-ids,
  returns the tag association with errors appended if applicable."
  [inaccessible-concept-ids tag-association]
  (if (contains? inaccessible-concept-ids (:concept-id tag-association))
    (append-error tag-association (msg/inaccessible-collection (:concept-id tag-association)))
    tag-association))

(defn- validate-collection-revision
  "Validates the tag association collection revision is not in the set of tombstone-coll-revisions
  and inaccessible-coll-revisions, returns the tag association with errors appended if applicable."
  [tombstone-coll-revisions inaccessible-coll-revisions tag-association]
  (let [coll-revision (select-keys tag-association [:concept-id :revision-id])]
    (if (contains? tombstone-coll-revisions coll-revision)
      (append-error tag-association (msg/tombstone-collection coll-revision))
      (if (contains? inaccessible-coll-revisions coll-revision)
        (append-error tag-association (msg/inaccessible-collection-revision coll-revision))
        tag-association))))

(defn- validate-tag-association-conflict-for-collection
  "Validate the given tag association does not conflict with existing tag associations in that
  a tag cannot be associated with a collection revision and the same collection without revision
  at the same time."
  [context tag-key tag-association]
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
                tag-key (:concept-id tag-association) (str/join ", " coll-revision-ids)))

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

(defn- validate-tag-association-conflict
  "Validates the tag association (either on a specific revision or over the whole collection)
  does not conflict with one or more existing tag associations in Metadata DB. Tag associations
  cannot be associated with a collection revision and the same collection without revision
  at the same time. Returns the tag association with errors appended if applicable."
  [context tag-key tag-association]
  (if-let [error-msg (validate-tag-association-conflict-for-collection
                       context tag-key tag-association)]
    (append-error tag-association error-msg)
    tag-association))

(defn- validate-collection-identifier
  "Validates the tag association concept-id and revision-id (if given) satisfy tag association rules,
  i.e. collection specified exist and are viewable by the token,
  collection specified are not tombstones."
  [context inaccessible-concept-ids tombstone-coll-revisions inaccessible-coll-revisions tag-association]
  (if (:revision-id tag-association)
    (validate-collection-revision
      tombstone-coll-revisions inaccessible-coll-revisions tag-association)
    (validate-collection-concept-id inaccessible-concept-ids tag-association)))

(defn- validate-tag-association-data
  "Validates the tag association data are within the maximum length requirement after written in JSON."
  [tag-association]
  (if (> (count (json/generate-string (:data tag-association))) jv/maximum-data-length)
    (append-error tag-association (msg/tag-association-data-too-long tag-association))
    tag-association))

(defn- validate-empty-tag-associations
  "Validates the given tag association is not empty, throws service error if it is."
  [tag-associations]
  (when-not (seq tag-associations)
    (errors/throw-service-error :invalid-data (msg/no-tag-associations))))

(defn- validate-conflicts-within-request
  "Validates the two lists have no intersection, otherwise there are conflicts within the same
  request, throws service error if conflicts are found."
  [concept-id-only-tas revision-tas]
  (let [conflict-tas (set/intersection (set (map :concept-id concept-id-only-tas))
                                       (set (map :concept-id revision-tas)))]
    (when (seq conflict-tas)
      (errors/throw-service-error
        :invalid-data (msg/conflict-tag-associations conflict-tas)))))

(defn- partition-tag-associations
  "Returns tag associations as a list partitioned by if there is a revision-id."
  [tag-associations]
  (let [has-revision-id? #(contains? % :revision-id)
        {concept-id-only-tas false revision-tas true} (group-by has-revision-id? tag-associations)]
    [concept-id-only-tas revision-tas]))

(defmulti validate-tag-associations
  "Validates the tag association for the given tag key and tag associations based on the operation
  type, which is either :insert or :delete. Returns the tag associations with errors found appended
  to them. If the provided tag associations fail the basic rules validation (i.e. empty tag
  associations, conflicts within the request), throws a service error."
  (fn [context operation-type tag-key tag-associations]
    operation-type))

(defmethod validate-tag-associations :insert
  [context operation-type tag-key tag-associations]
  (validate-empty-tag-associations tag-associations)
  (let [[concept-id-only-tas revision-tas] (partition-tag-associations tag-associations)
        _ (validate-conflicts-within-request concept-id-only-tas revision-tas)
        inaccessible-concept-ids (get-inaccessible-concept-ids
                                   context (map :concept-id concept-id-only-tas))
        {:keys [tombstones inaccessibles]} (get-bad-collection-revisions context tag-associations)]
    (->> tag-associations
         (map #(validate-collection-identifier
                 context inaccessible-concept-ids tombstones inaccessibles %))
         (map validate-tag-association-data)
         (map #(validate-tag-association-conflict context tag-key %)))))

(defmethod validate-tag-associations :delete
  [context operation-type tag-key tag-associations]
  (validate-empty-tag-associations tag-associations)
  (let [[concept-id-only-tas revision-tas] (partition-tag-associations tag-associations)
        inaccessible-concept-ids (get-inaccessible-concept-ids
                                   context (map :concept-id concept-id-only-tas))
        {:keys [tombstones inaccessibles]} (get-bad-collection-revisions context tag-associations)]
    (->> tag-associations
         (map #(validate-collection-identifier
                 context inaccessible-concept-ids tombstones inaccessibles %)))))

