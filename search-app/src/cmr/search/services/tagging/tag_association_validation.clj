(ns cmr.search.services.tagging.tag-association-validation
  "This contains functions for validating the business rules of tag association."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-model :as cqm]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.search.services.tagging.json-schema-validation :as jv]
            [cmr.search.services.tagging.tagging-service-messages :as msg]))

(defn collections-json->collections
  "Validates the collections json and returns the parsed collections"
  [collections-json]
  (jv/validate-collections-json collections-json)
  (json/parse-string collections-json true))

(defn- validate-collection-concept-ids
  "Validates the collection concept-ids are valid,
  i.e. all collections for the given concept-ids exist and are viewable by the token."
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
                           (map :concept-id))
          inaccessible-concept-ids (set/difference (set coll-concept-ids) (set concept-ids))]
      (when (seq inaccessible-concept-ids)
        (errors/throw-service-error
          :invalid-data (msg/inaccessible-collections inaccessible-concept-ids))))))

(defn- validate-collection-revisions
  "Validates the collection revisions are valid,
  i.e. all collections for the given concept-id and revision-id exist, are viewable by the token
  and are not tombstones."
  [context colls]
  (when (seq colls)
    (let [query (cqm/query {:concept-type :collection
                            :condition (cqm/string-conditions :concept-id (map :concept-id colls) true)
                            :page-size :unlimited
                            :result-format :query-specified
                            :fields [:concept-id :revision-id :deleted]
                            :all-revisions? true
                            :skip-acls? false})
          collections (->> (qe/execute-query context query)
                           :items
                           (map #(select-keys % [:concept-id :revision-id :deleted])))
          ids-fn (fn [coll] (select-keys coll [:concept-id :revision-id]))
          colls-set (set (map ids-fn colls))
          matched-colls (filter #(contains? colls-set (ids-fn %)) collections)
          tombstone-colls (filter :deleted matched-colls)]
      (when (seq tombstone-colls)
        (errors/throw-service-error
          :invalid-data (msg/tombstone-collections tombstone-colls)))
      (when-let [inaccessible-coll-revisions (seq
                                               (set/difference
                                                 colls-set
                                                 (set (map ids-fn matched-colls))))]
        (errors/throw-service-error
          :invalid-data (msg/inaccessible-collection-revisions inaccessible-coll-revisions))))))

(defn- validate-tag-association-conflict-for-collection
  "Validate tag association conflict for a single collection, returns the validation error.
  A tag association conflict occurs if a tag is associated with a collection revision and the same
  collection without revision at the same time."
  [context tag-key coll]
  (let [tas (->> (mdb/find-concepts context
                                    {:tag-key tag-key
                                     :associated-concept-id (:concept-id coll)
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
      (when-not (:revision-id coll)
        [(format (str "There are already tag associations with tag key [%s] on collection [%s] "
                      "revision ids [%s], cannot create tag association on the same collection without revision id.")
                 tag-key (:concept-id coll) (str/join ", " coll-revision-ids))])

      ;; there are existing tag associations and they are all on collection without revision
      (= 0 (count not-nil-revision-ids))
      (when-let [revision-id (:revision-id coll)]
        [(format (str "There are already tag associations with tag key [%s] on collection [%s] "
                      "without revision id, cannot create tag association on the same collection "
                      "with revision id [%s].")
                 tag-key (:concept-id coll) revision-id)])

      ;; there are conflicts within the existing tag associations in metadata-db already
      :else
      [(format (str "Tag can only be associated with a collection or a collection revision, "
                    "never both at the same time. There are already conflicting tag associations "
                    "in metadata-db with tag key [%s] on collection [%s] , please delete "
                    "one of the conflicting tag associations.")
               tag-key (:concept-id coll))])))

(defn- validate-tag-association-conflicts
  "Validate tag association conflict with existing tag associations in metadata-db in that a tag
  cannot be associated with a collection revision and the same collection without revision at the
  same time, throws service error if conflict is found."
  [context tag-key colls]
  (when (seq colls)
    (let [err-msgs (mapcat (partial validate-tag-association-conflict-for-collection context tag-key)
                           colls)]
      (when (seq err-msgs)
        (errors/throw-service-errors :invalid-data err-msgs)))))

(defn- validate-collection-identifiers
  "Validates the collection concept-ids and revision-ids (if given) satisfy tag association rules,
  i.e. collections specified exist and are viewable by the token,
  collections specified are not tombstones
  tag associations specified do not conflict with the ones in the same request."
  [context collections]
  (when-not (seq collections)
    (errors/throw-service-error
      :invalid-data (msg/no-collections)))

  (let [has-revision-id? (fn [c] (if (:revision-id c) true false))
        {concept-id-only-colls false revision-colls true} (group-by has-revision-id? collections)
        conflict-colls (set/intersection (set (map :concept-id concept-id-only-colls))
                                         (set (map :concept-id revision-colls)))]
    ;; validate for tag association conflict within the request
    (when (seq conflict-colls)
      (errors/throw-service-error
        :invalid-data (msg/conflict-collections conflict-colls)))

    (validate-collection-concept-ids context (map :concept-id concept-id-only-colls))
    (validate-collection-revisions context revision-colls)))

(defn- validate-tag-association-data
  "Validates the tag association data are within the maximum length requirement after written in JSON."
  [collections]
  (let [too-much-data-fn (fn [c]
                           (> (count (json/generate-string (:data c))) jv/maximum-data-length))
        data-too-long-colls (filter too-much-data-fn collections)]
    (when (seq data-too-long-colls)
      (errors/throw-service-error
        :bad-request (msg/collections-data-too-long (map :concept-id data-too-long-colls))))))

(defn validate-tag-association
  "Validates the tag association for the given tag-key and collections,
  throws service error if it is invalid."
  [context tag-key collections]
  (validate-collection-identifiers context collections)
  (validate-tag-association-data collections)
  (validate-tag-association-conflicts context tag-key collections))
