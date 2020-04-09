(ns cmr.access-control.services.group-service
  "Provides functions for creating, updating, deleting, retrieving, and finding groups."
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [cmr.access-control.data.access-control-index :as index]
    [cmr.access-control.services.acl-util :as acl-util]
    [cmr.access-control.services.auth-util :as auth]
    [cmr.access-control.services.group-service-messages :as g-msg]
    [cmr.access-control.services.messages :as msg]
    [cmr.common-app.api.enabled :as common-enabled]
    [cmr.common-app.services.search :as cs]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.parameter-validation :as cpv]
    [cmr.common-app.services.search.params :as cp]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.common.concepts :as concepts]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :as u]
    [cmr.common.validations.core :as v]
    [cmr.transmit.config :as transmit-config]
    [cmr.transmit.echo.rest :as rest]
    [cmr.transmit.echo.tokens :as tokens]
    [cmr.transmit.metadata-db2 :as mdb]
    [cmr.transmit.urs :as urs])
  ;; Must be required to be available at runtime
  (:require
    cmr.access-control.data.group-json-results-handler)
  (:import
    (java.util UUID)))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized msg/token-required)))

(def SYSTEM_PROVIDER_ID
  "The provider id used when a group is a system provider."
  "CMR")

;; to avoid reordering code (used in group creation)
(declare search-for-groups)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB Concept Map Manipulation

(defn- group->mdb-provider-id
  "Returns the provider id to use in metadata db for the group"
  [group]
  (get group :provider-id SYSTEM_PROVIDER_ID))

(defn- group->new-concept
  "Converts a group into a new concept that can be persisted in metadata db."
  [context group]
  {:concept-type :access-group
   ;; We don't care about a group's native ID, but it's required by metadata DB, so we "set it and forget it" here.
   :native-id (str (UUID/randomUUID))
   ;; Provider id is optional in group. If it is a system level group then it's owned by the CMR.
   :provider-id (group->mdb-provider-id group)
   :metadata (pr-str group)
   :user-id (context->user-id context)
   ;; The first version of a group should always be revision id 1. We always specify a revision id
   ;; when saving groups to help avoid conflicts
   :revision-id 1
   :format mt/edn})

(defn- fetch-group-concept
  "Fetches the latest version of a group concept by concept id. Handles unknown concept ids by
  throwing a service error."
  [context concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (when (not= :access-group concept-type)
      (errors/throw-service-error :bad-request (g-msg/bad-group-concept-id concept-id))))

  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found (g-msg/group-deleted concept-id))
      concept)
    (errors/throw-service-error :not-found (g-msg/group-does-not-exist concept-id))))

(defn- save-updated-group-concept
  "Saves an updated group concept"
  [context existing-concept updated-group]
  (let [updated-concept (-> existing-concept
                            (assoc :metadata (pr-str updated-group)
                                   :deleted false
                                   :user-id (context->user-id context))
                            (dissoc :revision-date)
                            (update :revision-id inc))
         result (mdb/save-concept context updated-concept)]
    ;; Index the group since group updates are synchronous
    (index/index-group context updated-concept)
    result))

(defn- save-deleted-group-concept
  "Saves an existing group concept as a tombstone"
  [context existing-concept]
  (let [deleted-concept (-> existing-concept
                            ;; Remove fields not allowed when creating a tombstone.
                            (dissoc :metadata :format :provider-id :native-id :revision-date
                                    :transaction-id)
                            (assoc :deleted true
                                   :user-id (context->user-id context))
                            (update :revision-id inc))
        result (mdb/save-concept context deleted-concept)]
    (index/unindex-group context (:concept-id existing-concept) (:revision-id deleted-concept))
    result))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations

(defn- validate-group-provider-exists
  "Validates that the groups provider exists."
  [context fieldpath provider-id]
  (when (and provider-id
             (not (some #{provider-id} (map :provider-id (mdb/get-providers context)))))
    {fieldpath [(msg/provider-does-not-exist provider-id)]}))

(defn- non-existent-users
  "Returns the list of users that does not exist in URS among the given list of users."
  [context usernames]
  (seq (remove #(urs/user-exists? context %) (distinct usernames))))

(defn- validate-group-members-exist
  "Validates that the given usernames exist. Throws an exception if they do not."
  [context fieldpath usernames]
  (when-let [non-existent-users (non-existent-users context usernames)]
    {fieldpath [(msg/users-do-not-exist non-existent-users)]}))

(defn- create-group-validations
  "Service level validations when creating a group."
  [context]
  {:provider-id #(validate-group-provider-exists context %1 %2)
   :members #(validate-group-members-exist context %1 %2)})

(defn- validate-create-group
  "Validates a group create."
  [context group]
  (v/validate! (create-group-validations context) group))

(defn- update-group-validations
  "Service level validations when updating a group."
  [_]
  [(v/field-cannot-be-changed :provider-id)
   (v/field-cannot-be-changed :legacy-guid true)])

(defn- validate-update-group
  "Validates a group update."
  [context existing-group updated-group]
  (v/validate! (update-group-validations context) (assoc updated-group :existing existing-group)))

(defn validate-members-exist
  "Validates that the given usernames exist. Throws an exception if they do not."
  [context usernames]
  (when-let [non-existent-users (non-existent-users context usernames)]
    (errors/throw-service-error :bad-request (msg/users-do-not-exist non-existent-users))))

(defn group-exists?
  "Returns true if group exists."
  [context concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (when (not= :access-group concept-type)
      (errors/throw-service-error :bad-request (g-msg/bad-group-concept-id concept-id))))
  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (not (:deleted concept))
    false))

(defn- validate-managing-group-id
  "Validates that the given managing group id exist. Throws an exception if it does not."
  [context managing-group-id]
  (when managing-group-id
    (when (sequential? managing-group-id)
      (errors/throw-service-error
       :bad-request "Parameter managing_group_id must have a single value."))
    (when (not (group-exists? context managing-group-id))
      (errors/throw-service-error
       :bad-request (msg/managing-group-does-not-exist managing-group-id)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Service level functions

(defn create-group
  "Creates the group by saving it to Metadata DB. Returns a map of the concept id and revision id of
  the created group."
  ([context group]
   (create-group context group nil))
  ([context group {:keys [skip-acls? managing-group-id]}]
   (common-enabled/validate-write-enabled context "access control")
   (validate-create-group context group)
   (when-not skip-acls?
     (auth/verify-can-create-group context group))

   (validate-managing-group-id (transmit-config/with-echo-system-token context) managing-group-id)
   ;; Verify there will not be a name collision
   (if-let [concept-id (->> (search-for-groups
                              context {:name (:name group)
                                       :provider (get group :provider-id SYSTEM_PROVIDER_ID)})
                            :results
                            :items
                            (some :concept_id))]

     ;; The group exists. Check if its latest revision is a tombstone.
     (let [concept (mdb/get-latest-concept context concept-id)]
       ;; The group exists and was not deleted. Reject this.
       (errors/throw-service-error :conflict (g-msg/group-already-exists group concept)))

     ;; The group doesn't exist.
     (let [new-concept (group->new-concept context group)
           {:keys [concept-id revision-id] :as result} (mdb/save-concept context new-concept)]
       ;; Index the group here. Group indexing is synchronous.
       (index/index-group context (assoc new-concept :concept-id concept-id :revision-id revision-id))
       ;; If managing group id exists, create the ACL to grant permission to the managing group
       ;; to update/delete the group.
       (when managing-group-id
         (acl-util/create-acl context
                              {:group-permissions [{:group-id managing-group-id
                                                    :permissions ["update" "delete"]}]
                               :single-instance-identity {:target-id concept-id
                                                          :target "GROUP_MANAGEMENT"}}))
       result))))

(defn get-group-by-concept-id
  "Retrieves a group with the given concept id, returns its parsed metadata."
  [context concept-id]
  (edn/read-string (:metadata (fetch-group-concept context concept-id))))

(defn get-group
  "Retrieves a group with the given concept id."
  [context concept-id]
  (let [group (get-group-by-concept-id context concept-id)]
    (auth/verify-can-read-group context (assoc group :concept-id concept-id))
    ;; Group response includes the number of members and not the actual members
    (-> group
        (assoc :num-members (count (:members group)))
        (dissoc :members))))

(defn delete-group
  "Deletes a group with the given concept id"
  [context concept-id]
  (common-enabled/validate-write-enabled context "access control")
  (let [group-concept (fetch-group-concept context concept-id)
        group (edn/read-string (:metadata group-concept))]
    (auth/verify-can-delete-group context (assoc group :concept-id concept-id))
    (save-deleted-group-concept context group-concept)))

(defn update-group
  "Updates an existing group with the given concept id"
  [context concept-id updated-group]
  (common-enabled/validate-write-enabled context "access control")
  (let [existing-concept (fetch-group-concept context concept-id)
        existing-group (edn/read-string (:metadata existing-concept))]
    (validate-update-group context existing-group updated-group)
    (auth/verify-can-update-group context (assoc existing-group :concept-id concept-id))
    ;; Verify the name does not conflict with an existing group
    (when (not= (:name updated-group) (:name existing-group))
      (debug (format "Group name update [%s] -> [%s] checking for conflicts"
                       (:name existing-group)
                       (:name updated-group)))
      (when-let [conflicting-concept-id
                 (->> (search-for-groups context
                                         {:name (:name updated-group)
                                          :provider (get existing-group :provider-id SYSTEM_PROVIDER_ID)})
                      :results
                      :items
                      (some :concept_id))]
        (let [concept (mdb/get-latest-concept context conflicting-concept-id)]
          ;; A name collision was found, reject the update.
          (errors/throw-service-error :conflict (g-msg/group-already-exists existing-group concept)))))

    ;; Avoid clobbering :members by merging the updated-group into existing-group. If updated-group
    ;; specifies :members then it will overwrite the existing value.
    (let [updated-group (merge existing-group updated-group)
          update-result (save-updated-group-concept context existing-concept updated-group)]
      update-result)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search functions

(defmethod cpv/params-config :access-group
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{:include-members}
    :multiple-value #{:provider :name :member :legacy-guid :concept-id}
    :always-case-sensitive #{}
    :disallow-pattern #{}
    :allow-or #{}}))

(defmethod cpv/valid-query-level-params :access-group
  [_]
  #{:include-members})

(defmethod cpv/valid-parameter-options :access-group
  [_]
  {:provider cpv/string-param-options
   :name cpv/string-param-options
   :legacy-guid cpv/string-param-options
   :concept-id cpv/string-param-options
   :member #{:pattern :and}})

(defn validate-group-search-params
  "Validates the parameters for a group search. Returns the parameters or throws an error if invalid."
  [context params]
  (let [[safe-params type-errors] (cpv/apply-type-validations
                                   params
                                   [(partial cpv/validate-map [:options])
                                    (partial cpv/validate-map [:options :provider])
                                    (partial cpv/validate-map [:options :name])
                                    (partial cpv/validate-map [:options :member])
                                    (partial cpv/validate-map [:options :concept-id])
                                    (partial cpv/validate-map [:options :legacy-guid])])]
    (cpv/validate-parameters
     :access-group safe-params
     (concat cpv/common-validations
             [(partial cpv/validate-boolean-param :include-members)])
     type-errors))
  params)

(defmethod common-qm/default-sort-keys :access-group
  [_]
  [{:field :provider-id :order :asc}
   {:field :name :order :asc}])

(defmethod cp/param-mappings :access-group
  [_]
  {:provider :access-group-provider
   :name :string
   :member :string
   :legacy-guid :string
   :concept-id :string})

(defmethod cp/parameter->condition :access-group-provider
  [context concept-type param value options]

  (if (sequential? value)
    (gc/group-conds (cp/group-operation param options)
                    (map #(cp/parameter->condition context concept-type param % options) value))
    ;; CMR indicates we should search for system groups
    (if (= (str/upper-case value) SYSTEM_PROVIDER_ID)
      (common-qm/not-exist-condition :provider)
      (cp/string-parameter->condition concept-type param value options))))

(defmethod cp/parse-query-level-params :access-group
  [concept-type params]
  (let [[params query-attribs] (cp/default-parse-query-level-params :access-group params)]
    [(dissoc params :include-members)
     (merge query-attribs
            (when (= (:include-members params) "true")
              {:result-features [:include-members]}))]))

(defn search-for-groups
  [context params]
  (let [[query-creation-time query] (u/time-execution
                                     (->> params
                                          cp/sanitize-params
                                          (validate-group-search-params :access-group)
                                          (cp/parse-parameter-query context :access-group)))
        [find-concepts-time results] (u/time-execution
                                      (cs/find-concepts context :access-group query))
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d access-groups in %d ms in format %s with params %s."
                  (:hits results) total-took (common-qm/base-result-format query) (pr-str params)))
    (-> results
        (assoc :took total-took)
        (update :results #(json/parse-string % keyword)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Member functions

(defn- add-members-to-group
  "Adds the new members to the group handling duplicates."
  [group members]
  (update group
          :members
          (fn [existing-members]
            (vec (distinct (concat existing-members members))))))

(defn add-members
  "Adds members to the group identified by the concept id persisting it to Metadata DB. Returns
  the new concept id and revision id."
  ([context concept-id members]
   (add-members context concept-id members nil))
  ([context concept-id members {:keys [skip-acls? skip-member-validation?]}]
   (when-not skip-member-validation?
     (validate-members-exist context members))
   (let [existing-concept (fetch-group-concept context concept-id)
         existing-group (edn/read-string (:metadata existing-concept))
         updated-group (add-members-to-group existing-group members)]
     (when-not skip-acls?
       (auth/verify-can-update-group context (assoc existing-group :concept-id concept-id)))
     (save-updated-group-concept context existing-concept updated-group))))

(defn- remove-members-from-group
  "Removes the members from the group."
  [group members]
  (update group
          :members
          (fn [existing-members]
            (vec (remove (set members) existing-members)))))

(defn remove-members
  "Removes members from the group identified by the concept id persisting it to Metadata DB. Returns
  the new concept id and revision id."
  [context concept-id members]
  (let [existing-concept (fetch-group-concept context concept-id)
        existing-group (edn/read-string (:metadata existing-concept))
        updated-group (remove-members-from-group existing-group members)]
    (auth/verify-can-update-group context (assoc existing-group :concept-id concept-id))
    (save-updated-group-concept context existing-concept updated-group)))

(defn get-members
  "Gets the members in the group."
  [context concept-id]
  (let [concept (fetch-group-concept context concept-id)
        group (edn/read-string (:metadata concept))]
    (auth/verify-can-read-group context (assoc group :concept-id concept-id))
    (get group :members [])))

(defn health
  "Returns the health state of the app."
  [context]
  (let [echo-rest-health (rest/health context)
        metadata-db-health (mdb/get-metadata-db-health context)
        ok? (every? :ok? [echo-rest-health metadata-db-health])]
    {:ok? ok?
     :dependencies {:echo echo-rest-health
                    :metadata-db metadata-db-health}}))
