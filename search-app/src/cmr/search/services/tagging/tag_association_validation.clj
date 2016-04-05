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

(defn tag-associations-json->tag-associations
  "Validates the tag associations json and returns the parsed json"
  [tag-associations-json]
  (jv/validate-tag-associations-json tag-associations-json)
  (json/parse-string tag-associations-json true))

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
  "Validates the collection revisions specified in tag association are valid,
  i.e. all collections for the given concept-id and revision-id exist, are viewable by the token
  and are not tombstones."
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
        [(format (str "There are already tag associations with tag key [%s] on collection [%s] "
                      "revision ids [%s], cannot create tag association on the same collection without revision id.")
                 tag-key (:concept-id tag-association) (str/join ", " coll-revision-ids))])

      ;; there are existing tag associations and they are all on collection without revision
      (= 0 (count not-nil-revision-ids))
      (when-let [revision-id (:revision-id tag-association)]
        [(format (str "There are already tag associations with tag key [%s] on collection [%s] "
                      "without revision id, cannot create tag association on the same collection "
                      "with revision id [%s].")
                 tag-key (:concept-id tag-association) revision-id)])

      ;; there are conflicts within the existing tag associations in metadata-db already
      :else
      [(format (str "Tag can only be associated with a collection or a collection revision, "
                    "never both at the same time. There are already conflicting tag associations "
                    "in metadata-db with tag key [%s] on collection [%s] , please delete "
                    "one of the conflicting tag associations.")
               tag-key (:concept-id tag-association))])))

(defn- validate-tag-association-conflicts
  "Validates the tag association (either on a specific revision or over the whole collection)
  does not conflict with one or more existing tag associations in Metadata DB. Tag associations
  cannot be associated with a collection revision and the same collection without revision
  at the same time. It throws a service error if a conflict is found."
  [context tag-key tag-associations]
  (when (seq tag-associations)
    (when-let [err-msgs (seq (mapcat
                               #(validate-tag-association-conflict-for-collection context tag-key %)
                               tag-associations))]
      (errors/throw-service-errors :invalid-data err-msgs))))

(defn- validate-collection-identifiers
  "Validates the collection concept-ids and revision-ids (if given) satisfy tag association rules,
  i.e. collections specified exist and are viewable by the token,
  collections specified are not tombstones
  tag associations specified do not conflict with the ones in the same request."
  [context tag-associations]
  (when-not (seq tag-associations)
    (errors/throw-service-error
      :invalid-data (msg/no-tag-associations)))

  (let [has-revision-id? #(contains? % :revision-id)
        {concept-id-only-colls false revision-colls true} (group-by has-revision-id? tag-associations)
        conflict-colls (set/intersection (set (map :concept-id concept-id-only-colls))
                                         (set (map :concept-id revision-colls)))]
    ;; validate for tag association conflict within the request
    (when (seq conflict-colls)
      (errors/throw-service-error
        :invalid-data (msg/conflict-tag-associations conflict-colls)))

    (validate-collection-concept-ids context (map :concept-id concept-id-only-colls))
    (validate-collection-revisions context revision-colls)))

(defn- validate-tag-association-data
  "Validates the tag association data are within the maximum length requirement after written in JSON."
  [tag-associations]
  (let [too-much-data-fn (fn [c]
                           (> (count (json/generate-string (:data c))) jv/maximum-data-length))
        data-too-long-tas (filter too-much-data-fn tag-associations)]
    (when (seq data-too-long-tas)
      (errors/throw-service-error
        :bad-request (msg/tag-associations-data-too-long (map :concept-id data-too-long-tas))))))

(defn validate-tag-association
  "Validates the tag association for the given tag-key and tag-associations,
  throws service error if it is invalid."
  [context tag-key tag-associations]
  (validate-collection-identifiers context tag-associations)
  (validate-tag-association-data tag-associations)
  (validate-tag-association-conflicts context tag-key tag-associations))


