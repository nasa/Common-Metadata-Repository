(ns cmr.search.services.association-validation
  "This contains functions for validating the business rules of a generic association"
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common.concepts :as concepts]
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

(defn- validate-concept-concept-id
  "Validates the association concept concept-id is not in the set of inaccessible-concept-ids,
  returns the association with errors appended if applicable."
  [inaccessible-concept-ids association]
  (if (contains? inaccessible-concept-ids (:concept-id association))
    (append-error association (assoc-msg/inaccessible-concept (:concept-id association)))
    association))

(defn- validate-concept-revision
  "Validates the association concept revision is not in the set of tombstone-coll-revisions
  and inaccessible-coll-revisions, returns the association with errors appended if applicable."
  [assoc-type tombstone-revisions inaccessible-revisions association]
  (let [concept-revision (select-keys association [:concept-id :revision-id])]
    (if (contains? tombstone-revisions concept-revision)
      (append-error association (assoc-msg/tombstone-concept assoc-type concept-revision))
      (if (contains? inaccessible-revisions concept-revision)
        (append-error association (assoc-msg/inaccessible-concept-revision concept-revision))
        association))))

(defn validate-generic-association-conflict-for-concept
  "Validates concept with concept-id can not be associated with the concept in the association
  with and without the revisions." 
  [context concept-id association]
  (let [;;Get all the generic associations between the concept with concept-id and the concept
        ;;in the association. Depending on the order of the sorting between concept-id and (:concept-id association),
        ;;concept-id could be at source-concept-identifier or associated-concept-id.
        concept-id-as-source? (if (= concept-id (first (sort [concept-id (:concept-id association)])))
                                true
                                false)
        generic-assocs (if concept-id-as-source? 
                         (->> (mdb/find-concepts context
                                                 {:source-concept-identifier concept-id
                                                  :associated-concept-id (:concept-id association)
                                                  :exclude-metadata true
                                                  :latest true}
                                                  :generic-association)
                              (filter #(not (:deleted %))))
                         (->> (mdb/find-concepts context
                                                 {:source-concept-identifier (:concept-id association)
                                                  :associated-concept-id concept-id 
                                                  :exclude-metadata true
                                                  :latest true}
                                                  :generic-association)
                              (filter #(not (:deleted %)))))
        concept-revision-ids (if concept-id-as-source?
                               (map #(get-in % [:extra-fields :associated-revision-id]) generic-assocs)
                               (map #(get-in % [:extra-fields :source-revision-id]) generic-assocs))
        not-nil-revision-ids (remove nil? concept-revision-ids)]
    (cond
      ;; there is no existing generic association found between the two concepts, no need to validate
      (= 0 (count concept-revision-ids))
      nil

      ;; there are existing generic associations and they are all on concept revisions
      (= (count concept-revision-ids) (count not-nil-revision-ids))
      (when-not (:revision-id association)
        (format (str "There are already generic associations between concept id [%s] and "
                     "concept id [%s] revision ids [%s], cannot create generic association "
                     "on the same concept without revision id.")
                concept-id (:concept-id association) (string/join ", " concept-revision-ids)))

      ;; there are existing generic associations and they are all on concept without revision
      (= 0 (count not-nil-revision-ids))
      (when-let [revision-id (:revision-id association)]
        (format (str "There are already generic associations between concept id [%s] and "
                     "concept id [%s] without revision id, cannot create generic association "
                     "on the same concept with revision id [%s].")
                concept-id (:concept-id association) revision-id))

      ;; there are conflicts within the existing generic associations in metadata-db already
      :else
      (format (str "Concept can only be associated with a concept or a concept revision, "
                   "never both at the same time. There are already conflicting generic associations "
                   "in metadata-db between concept id [%s] and concept id [%s] , "
                   "please delete one of the conflicting generic associations.")
              concept-id (:concept-id association)))))

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

(defmethod validate-association-conflict-for-collection :tool
  [context assoc-type tool-concept-id tool-association]
  (let [vas (->> (mdb/find-concepts context
                                    {:tool-concept-id tool-concept-id
                                     :associated-concept-id (:concept-id tool-association)
                                     :exclude-metadata true
                                     :latest true}
                                    :tool-association)
                 (filter #(not (:deleted %))))
        coll-revision-ids (map #(get-in % [:extra-fields :associated-revision-id]) vas)
        not-nil-revision-ids (remove nil? coll-revision-ids)]
    (cond
      ;; there is no existing tool association found, no need to validate
      (= 0 (count coll-revision-ids))
      nil

      ;; there are existing tool associations and they are all on collection revisions
      (= (count coll-revision-ids) (count not-nil-revision-ids))
      (when-not (:revision-id tool-association)
        (format (str "There are already tool associations with tool concept id [%s] on "
                     "collection [%s] revision ids [%s], cannot create tool association "
                     "on the same collection without revision id.")
                tool-concept-id (:concept-id tool-association) (string/join ", " coll-revision-ids)))

      ;; there are existing tool associations and they are all on collection without revision
      (= 0 (count not-nil-revision-ids))
      (when-let [revision-id (:revision-id tool-association)]
        (format (str "There are already tool associations with tool concept id [%s] on "
                     "collection [%s] without revision id, cannot create tool association "
                     "on the same collection with revision id [%s].")
                tool-concept-id (:concept-id tool-association) revision-id))

      ;; there are conflicts within the existing tool associations in metadata-db already
      :else
      (format (str "Service can only be associated with a collection or a collection revision, "
                   "never both at the same time. There are already conflicting tool associations "
                   "in metadata-db with tool concept id [%s] on collection [%s] , "
                   "please delete one of the conflicting tool associations.")
              tool-concept-id (:concept-id tool-association)))))

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

(defn- validate-generic-association-conflict
  "A concept can NOT be associated with another concept with and without revision
  at the same time. Returns the association with errors appended if applicable."
  [context concept-id concept-revision-id association]
  ;;For generic associations, request can pass in revision-ids for both the source
  ;;and the destination. so we need to validate both of the following:
  ;;1. The source can not be associated with the destination with and without revision.
  ;;2. The destination can not be associated with the source with and without revision
  (let [source-error-msg (validate-generic-association-conflict-for-concept
                          context concept-id association)
        destination-error-msg (validate-generic-association-conflict-for-concept
                               context
                               (:concept-id association)
                               {:concept-id concept-id :revision-id concept-revision-id})
        error-msg (str source-error-msg "; " destination-error-msg)]
    (if (or source-error-msg destination-error-msg)
      (append-error association error-msg)
      association)))

(defn- validate-collection-identifier
  "Validates the association concept-id and revision-id (if given) satisfy association rules,
  i.e. collection specified exist and are viewable by the token,
  collection specified are not tombstones."
  [context assoc-type no-permission-concept-ids inaccessible-concept-ids tombstone-coll-revisions
   inaccessible-coll-revisions association association?]
  (if (contains? no-permission-concept-ids (:concept-id association))
    (if association?
      (append-error association (assoc-msg/no-permission-collection-assoc (:concept-id association)))
      (append-error association (assoc-msg/no-permission-collection-dissoc (:concept-id association))))
    (if (:revision-id association)
      (validate-collection-revision
        assoc-type tombstone-coll-revisions inaccessible-coll-revisions association)
      (validate-collection-concept-id inaccessible-concept-ids association))))

(defn- validate-concept-identifier
  "Validates the association concept-id and revision-id (if given) satisfy association rules,
  i.e. concepts specified exist and are viewable by the token,
  collection specified are not tombstones."
  [context assoc-type no-permission-concept-ids inaccessible-concept-ids tombstone-revisions
   inaccessible-revisions association association?]
  (if (contains? no-permission-concept-ids (:concept-id association))
    (if association?
      (append-error association (assoc-msg/no-permission-concept-assoc (:concept-id association)))
      (append-error association (assoc-msg/no-permission-concept-dissoc (:concept-id association))))
    (if (:revision-id association)
      (validate-concept-revision
        assoc-type tombstone-revisions inaccessible-revisions association)
      (validate-concept-concept-id inaccessible-concept-ids association))))

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

(defn- validate-generic-conflicts-within-request
  "Validates the two lists have no intersection, otherwise there are conflicts within the same
  request, throws service error if conflicts are found."
  [assoc-type concept-id-only-assocs revision-assocs]
  (let [conflict-assocs (set/intersection (set (map :concept-id concept-id-only-assocs))
                                          (set (map :concept-id revision-assocs)))]
    (when (seq conflict-assocs)
      (errors/throw-service-error
        :invalid-data (assoc-msg/conflict-generic-associations assoc-type conflict-assocs)))))

(defn validate-no-same-concept-generic-association
  "Validates that the concept is not associating with itself in the associations."
  [concept associations]
  (if (contains? (set (map :concept-id associations)) (:concept-id concept))
    (errors/throw-service-error
        :invalid-data (assoc-msg/same-concept-generic-association (:concept-id concept)))
    associations))

(defn- get-assoc-type
  "Return association type given the concept-id and the assoc-id."
  [concept-id assoc-id]
  (let [source-concept-id (first (sort [concept-id assoc-id]))
        dest-concept-id (second (sort [concept-id assoc-id]))
        source-concept-type-str (name (concepts/concept-id->type source-concept-id))
        dest-concept-type-str (name (concepts/concept-id->type dest-concept-id))]
    (string/upper-case (str source-concept-type-str "-" dest-concept-type-str))))

(defn validate-generic-association-combination-types
  "Disallow collection to be associated with services, tools and variables through
  generic association api. because the old association api and the new ones have different
  native-id, concept-id, association-type structures."
  [concept associations]
  (let [concept-id (:concept-id concept)
        concept-type (concepts/concept-id->type concept-id)
        associated-concept-ids (map :concept-id associations)
        associated-concept-types (map #(concepts/concept-id->type %) associated-concept-ids)
        checking-types [:variable :service :tool :collection]
        disallowed-association-types ["COLLECTION-VARIABLE" "COLLECTION-SERVICE" "COLLECTION-TOOL"]]
    (if (and (some #(= concept-type %) checking-types)
             (seq (set/intersection (set associated-concept-types) (set checking-types))))
      (let [cannot-assoc-ids (for [associated-concept-id associated-concept-ids
                                   :let [assoc-type (get-assoc-type concept-id associated-concept-id)]
                                   :when (some #(= assoc-type %) disallowed-association-types)]
                               associated-concept-id)]
        (if (seq cannot-assoc-ids)
          (errors/throw-service-error
           :invalid-data
           (assoc-msg/cannot-assoc-msg concept-id (pr-str cannot-assoc-ids)))
          associations))
      associations)))

(defn validate-generic-association-types
  "Validates only certain concept types are supported for generic associations."
  [concept associations]
  ;;We currently only support generic associations among collection and all generic concept types
  ;;Other types could be ingested, but since the reindex event is not being handled
  ;;yet, the associations won'be shown in the concept search. We should support them after
  ;;having the event handler in place.
  (let [concept-ids (concat [(:concept-id concept)] (map :concept-id associations))
        concept-types (map #(concepts/concept-id->type %) concept-ids)
        supported-concept-types (set (concat [:collection :variable :service :tool]
                                             (concepts/get-generic-concept-types-array)))
        non-supported-types (remove nil? 
                                    (map #(when-not (contains? supported-concept-types %) %)
                                         concept-types))]
    (if (seq non-supported-types)
      (errors/throw-service-error
        :invalid-data (assoc-msg/non-supported-generic-association-types (pr-str non-supported-types)))
      associations)))
         
(defn- partition-associations
  "Returns the associations as a list partitioned by if there is a revision-id."
  [associations]
  (let [has-revision-id? #(contains? % :revision-id)
        {concept-id-only-assocs false revision-assocs true} (group-by has-revision-id? associations)]
    [concept-id-only-assocs revision-assocs]))

(defn- found-id?
  "Returns true if id is found in ids."
  [ids id]
  (some #(= id %) ids))

(defn- no-ingest-management-permission?
  "Return true if the user doesn't have update permission on INGEST_MANAGEMENT_ACL for
  a provider."
  [context provider-id]
  (try
    (acl/verify-ingest-management-permission-for-provider
     context :update :provider-object provider-id)
    false
    (catch Exception e
      true)))

(defn- get-no-permission-concept-ids
  "Return all the collection concept ids that the user doesn't have update permission on
  INGEST_MANAGEMENT_ACL for their providers."
  [context concept-ids]
  (for [concept-id concept-ids
        :let [provider-id (concepts/concept-id->provider-id concept-id)]
        :when (no-ingest-management-permission? context provider-id)]
    concept-id))

(defmulti validate-associations
  "Validates the associations for the given association type (:tag or :variable) based on the
  operation type, which is either :insert or :delete. Id is the identifier value of the tag or
  variable that is associated with the collections. Returns the associations with errors found
  appended to them. If the provided associations fail the basic rules validation (e.g. empty
  associations, conflicts within the request), throws a service error."
  (fn [context assoc-type assoc-id associations operation-type]
    operation-type))

(defmulti validate-generic-associations
  "Validates the associations based on the operation type, which is either :insert or :delete. 
  assoc-typeand assoc-id are the concept type and concept id that is associated with the concepts
  in the associations. Returns the associations with errors found
  appended to them. If the provided associations fail the basic rules validation (e.g. empty
  associations, conflicts within the request), throws a service error."
  (fn [context assoc-type assoc-id assoc-revision-id associations operation-type]
    operation-type))

(defmethod validate-associations :insert
  [context assoc-type assoc-id associations operation-type]
  (validate-empty-associations assoc-type associations)
  (let [[concept-id-only-assocs revision-assocs] (partition-associations associations)
        _ (validate-conflicts-within-request assoc-type concept-id-only-assocs revision-assocs)
        ;; A user can make an association if the user has update permission on
        ;; INGEST_MANAGEMENT_ACL for the provider of the collection.
        ;; find all the collections in the associations that the user doesn't have
        ;; update Ingest Management permission for their providers.
        no-permission-concept-ids (when (or (= :tool assoc-type) (= :service assoc-type))
                                    (get-no-permission-concept-ids
                                     context (map :concept-id associations)))
        inaccessible-concept-ids (get-inaccessible-concept-ids
                                  context (map :concept-id concept-id-only-assocs))
        {:keys [tombstones inaccessibles]} (get-bad-collection-revisions context associations)
        ;;remove any no permission concept-ids from inaccessible-concept-ids, tombstones
        ;;and inaccessibles because we don't want multiple errors for the same collection concept.
        inaccessible-concept-ids (remove #(found-id? no-permission-concept-ids %)
                                         inaccessible-concept-ids)
        tombstones (remove #(found-id? no-permission-concept-ids (:concept-id %))
                           tombstones)
        inaccessibles (remove #(found-id? no-permission-concept-ids (:concept-id %))
                              inaccessibles)]
    (->> associations
         (map #(validate-collection-identifier
                context
                assoc-type
                (set no-permission-concept-ids)
                (set inaccessible-concept-ids)
                (set tombstones)
                (set inaccessibles)
                %
                true))
         (map #(validate-association-data assoc-type %))
         (map #(validate-association-conflict context assoc-type assoc-id %)))))

(defmethod validate-associations :delete
  [context assoc-type assoc-id associations operation-type]
  (validate-empty-associations assoc-type associations)
  (let [[concept-id-only-assocs revision-assocs] (partition-associations associations)
        ;; A user can delete an association if the user has update permission on
        ;; INGEST_MANAGEMENT_ACL for the provider of the associated service/tool, or the collection
        ;; in the associations.
        ;; so we only need to find no-permission-concept-ids if the user does NOT
        ;; have the update Ingest Management permission on the assoc-id's provider.
        no-permission-concept-ids (when (and (or (= :tool assoc-type) (= :service assoc-type))
                                             (no-ingest-management-permission?
                                              context
                                              (concepts/concept-id->provider-id assoc-id)))
                                    (get-no-permission-concept-ids
                                     context (map :concept-id associations)))

        inaccessible-concept-ids (get-inaccessible-concept-ids
                                   context (map :concept-id concept-id-only-assocs))
        {:keys [tombstones inaccessibles]} (get-bad-collection-revisions context associations)
        ;;remove any no permission concept-ids from inaccessible-concept-ids, tombstones
        ;;and inaccessibles because we don't want multiple errors for the same collection concept.
        inaccessible-concept-ids (remove #(found-id? no-permission-concept-ids %)
                                         inaccessible-concept-ids)
        tombstones (remove #(found-id? no-permission-concept-ids (:concept-id %))
                           tombstones)
        inaccessibles (remove #(found-id? no-permission-concept-ids (:concept-id %))
                              inaccessibles)]
    (->> associations
         (map #(validate-collection-identifier
                context
                assoc-type
                (set no-permission-concept-ids)
                (set inaccessible-concept-ids)
                (set tombstones)
                (set inaccessibles)
                %
                false)))))

(defn- get-bad-concept-revisions
  "Returns the bad concept revisions of the given associations partitioned into a set of
  concept revisions that are tombstones and a set of concept revisions that are inaccessible."
  [context associations]
  (when (seq associations)
    (let [concept-ids-by-types (group-by concepts/concept-id->type (map :concept-id associations))
          concept-ids-by-valid-types (dissoc concept-ids-by-types nil)
          accessible-concept-revisions
           (flatten (for [concept-type (keys concept-ids-by-valid-types)
                          :let [concept-ids (concept-type concept-ids-by-valid-types)
                                query (cqm/query {:concept-type concept-type
                                                  :condition (cqm/string-conditions :concept-id concept-ids true)
                                                  :page-size :unlimited
                                                  :result-format :query-specified
                                                  :result-fields [:concept-id :revision-id :deleted]
                                                  :all-revisions? true
                                                  :skip-acls? false})
                                concepts (->> (qe/execute-query context query)
                                              :items
                                              (map #(select-keys % [:concept-id :revision-id :deleted])))]]
                      concepts))
          ids-fn (fn [concept] (select-keys concept [:concept-id :revision-id]))
          concepts-set (set (map ids-fn associations))
          matched-concepts (filter #(contains? concepts-set (ids-fn %)) accessible-concept-revisions)
          tombstone-concept-revisions (set (map ids-fn (filter :deleted matched-concepts)))
          inaccessible-concept-revisions (set/difference
                                          concepts-set
                                          (set (map ids-fn matched-concepts)))]
      {:tombstoned-revisions tombstone-concept-revisions
       :inaccessible-revisions inaccessible-concept-revisions})))

(defn- get-generic-inaccessible-concept-ids
  "Returns the concept-ids within the given list that are invalid,
  i.e. the concepts for the given concept-ids do not exist or are not viewable by the token."
  [context generic-concept-ids]
  (when (seq generic-concept-ids)
    (let [concept-ids-by-types (group-by concepts/concept-id->type generic-concept-ids)
          concept-ids-by-valid-types (dissoc concept-ids-by-types nil)
          accessible-concept-ids
           (flatten (for [concept-type (keys concept-ids-by-valid-types)
                          :let [concept-ids (concept-type concept-ids-by-valid-types)
                                query (cqm/query {:concept-type concept-type
                                                  :condition (cqm/string-conditions :concept-id concept-ids true)
                                                  :page-size :unlimited
                                                  :result-format :query-specified
                                                  :result-fields [:concept-id]
                                                  :skip-acls? false})
                                ids (->> (qe/execute-query context query)
                                         :items
                                         (map :concept-id))]]
                      ids))]
      (set/difference (set generic-concept-ids) (set accessible-concept-ids)))))

(defmethod validate-generic-associations :insert
  [context assoc-type assoc-id assoc-revision-id associations operation-type]
  (validate-empty-associations assoc-type associations)
  (let [[concept-id-only-assocs revision-assocs] (partition-associations associations)
        _ (validate-generic-conflicts-within-request assoc-type concept-id-only-assocs revision-assocs)
        no-permission-concept-ids (get-no-permission-concept-ids
                                   context (map :concept-id associations))

        ;;Find inaccessible concept ids in concept-id-only-assocs.
        ;;This will include the tombstoned concept ids because they won't be returned
        ;;in the search result without using all_revision = true. So, the tombstoned ones
        ;;are also inaccessible through non-all-revisions search.
        inaccessible-concept-only-concept-ids (get-generic-inaccessible-concept-ids
                                               context (map :concept-id concept-id-only-assocs))
        ;;Find inaccessible concept revisions and tombstoned revisions from revision-assocs.
        ;;This search uses all_revision=true, which can return the deleted revisions so we
        ;;could distinguish the two. 
        {:keys [tombstoned-revisions inaccessible-revisions]} (get-bad-concept-revisions context revision-assocs)
        inaccessible-concept-only-concept-ids (remove #(found-id? no-permission-concept-ids %)
                                              inaccessible-concept-only-concept-ids)
        tombstoned-revisions (remove #(found-id? no-permission-concept-ids (:concept-id %))
                                     tombstoned-revisions)
        inaccessible-revisions (remove #(found-id? no-permission-concept-ids (:concept-id %))
                                       inaccessible-revisions)]
    (->> associations
         (map #(validate-concept-identifier
                context
                assoc-type
                (set no-permission-concept-ids)
                (set inaccessible-concept-only-concept-ids)
                (set tombstoned-revisions)
                (set inaccessible-revisions)
                %
                true))
         (map #(validate-association-data assoc-type %))
         (map #(validate-generic-association-conflict context assoc-id assoc-revision-id %)))))

(defmethod validate-generic-associations :delete
  [context assoc-type assoc-id assoc-revision-id associations operation-type]
  (validate-empty-associations assoc-type associations)
  (let [[concept-id-only-assocs revision-assocs] (partition-associations associations)
        no-permission-concept-ids (get-no-permission-concept-ids
                                   context (map :concept-id associations))

        ;;Find inaccessible concept ids in concept-id-only-assocs.
        ;;This will include the tombstoned concept ids because they won't be returned
        ;;in the search result without using all_revision = true. So, the tombstoned ones
        ;;are also inaccessible through non-all-revisions search.
        inaccessible-concept-only-concept-ids (get-generic-inaccessible-concept-ids
                                               context (map :concept-id concept-id-only-assocs))
        ;;Find inaccessible concept revisions and tombstoned revisions from revision-assocs.
        ;;This search uses all_revision=true, which can return the deleted revisions so we
        ;;could distinguish the two.
        {:keys [tombstoned-revisions inaccessible-revisions]} (get-bad-concept-revisions context revision-assocs)
        inaccessible-concept-only-concept-ids (remove #(found-id? no-permission-concept-ids %)
                                              inaccessible-concept-only-concept-ids)
        tombstoned-revisions (remove #(found-id? no-permission-concept-ids (:concept-id %))
                                     tombstoned-revisions)
        inaccessible-revisions (remove #(found-id? no-permission-concept-ids (:concept-id %))
                                       inaccessible-revisions)]
    (->> associations
         (map #(validate-concept-identifier
                context
                assoc-type
                (set no-permission-concept-ids)
                (set inaccessible-concept-only-concept-ids)
                (set tombstoned-revisions)
                (set inaccessible-revisions)
                %
                false)))))

(defn validate-concept-for-generic-associations
  "Validate the concept is accessible and is not tombstoned."
  [context concept]
  (let [no-permission-concept-ids (get-no-permission-concept-ids context [(:concept-id concept)])]
    (if (seq no-permission-concept-ids)
      (errors/throw-service-error
       :unauthorized
       (format "User has no permission on the the provider for concept id [%s]." (:concept-id concept)))
      (if (:revision-id concept)
        ;;check to see if the concept revision is tombstoned or inaccessible. if so, throw error.
        (let [{:keys [tombstoned-revisions inaccessible-revisions]} (get-bad-concept-revisions context [concept])]
          (when (or (seq tombstoned-revisions) (seq inaccessible-revisions))
            (errors/throw-service-error
             :invalid-data
             (format "Concept with concept-id [%s] and revision-id [%s] is either tombstoned, or inaccessible."
                     (:concept-id concept) (:revision-id concept)))))
        ;;check to see if the concept is inaccessible. If so, throw error.
        (let [inaccessible-concept-ids (get-generic-inaccessible-concept-ids context [(:concept-id concept)])]
          (when (seq inaccessible-concept-ids)
            (errors/throw-service-error
             :invalid-data
             (format "Concept with concept-id [%s] is inaccessible." (:concept-id concept)))))))))
