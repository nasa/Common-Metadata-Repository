(ns cmr.access-control.services.group-service
  "Provides functions for creating, updating, deleting, retrieving, and finding groups."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [cmr.access-control.services.acl-service :as acl-service]
   [cmr.access-control.services.auth-util :as auth]
   [cmr.access-control.services.group-service-messages :as g-msg]
   [cmr.access-control.services.messages :as msg]
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
   [cmr.transmit.echo.rest :as rest]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.metadata-db2 :as mdb]
   [cmr.transmit.urs :as urs])
  ;; Must be required to be available at runtime
  (:require
   cmr.access-control.data.group-json-results-handler
   cmr.access-control.data.acl-json-results-handler))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized msg/token-required)))

(def SYSTEM_PROVIDER_ID
  "The provider id used when a group is a system provider."
  "CMR")

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
   :native-id (str/lower-case (:name group))
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
  (mdb/save-concept
    context
    (-> existing-concept
        (assoc :metadata (pr-str updated-group)
               :deleted false
               :user-id (context->user-id context))
        (dissoc :revision-date)
        (update :revision-id inc))))

(defn- save-deleted-group-concept
  "Saves an existing group concept as a tombstone"
  [context existing-concept]
  (mdb/save-concept
    context
    (-> existing-concept
        ;; Remove fields not allowed when creating a tombstone.
        (dissoc :metadata :format :provider-id :native-id)
        (assoc :deleted true
               :user-id (context->user-id context))
        (dissoc :revision-date :transaction-id)
        (update :revision-id inc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations

(defn validate-provider-exists
  "Validates that the groups provider exists."
  [context fieldpath provider-id]
  (when (and provider-id
             (not (some #{provider-id} (map :provider-id (mdb/get-providers context)))))
    {fieldpath [(msg/provider-does-not-exist provider-id)]}))

(defn- create-group-validations
  "Service level validations when creating a group."
  [context]
  {:provider-id #(validate-provider-exists context %1 %2)})

(defn- validate-create-group
  "Validates a group create."
  [context group]
  (v/validate! (create-group-validations context) group))

(defn- update-group-validations
  "Service level validations when updating a group."
  [context]
  [(v/field-cannot-be-changed :name)
   (v/field-cannot-be-changed :provider-id)
   (v/field-cannot-be-changed :legacy-guid)])

(defn- validate-update-group
  "Validates a group update."
  [context existing-group updated-group]
  (v/validate! (update-group-validations context) (assoc updated-group :existing existing-group)))

(defn validate-members-exist
  "Validates that the given usernames exist. Throws an exception if they do not."
  [context usernames]
  (when-let [non-existent-users (seq (remove #(urs/user-exists? context %) (distinct usernames)))]
    (errors/throw-service-error :bad-request (msg/users-do-not-exist non-existent-users))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Service level functions

(defn create-group
  "Creates the group by saving it to Metadata DB. Returns a map of the concept id and revision id of
  the created group."
  ([context group]
   (create-group context group nil))
  ([context group {:keys [skip-acls?]}]
   (validate-create-group context group)
   (when-not skip-acls?
     (auth/verify-can-create-group context group))
   ;; Check if the group already exists - lower case the name to prevent duplicates.(CMR-2466)
   (if-let [concept-id (mdb/get-concept-id context
                                           :access-group
                                           (group->mdb-provider-id group)
                                           (str/lower-case (:name group)))]

     ;; The group exists. Check if its latest revision is a tombstone
     (let [concept (mdb/get-latest-concept context concept-id)]
       (if (:deleted concept)
         ;; The group exists but was previously deleted.
         (save-updated-group-concept context concept group)

         ;; The group exists and was not deleted. Reject this.
         (errors/throw-service-error :conflict (g-msg/group-already-exists group concept))))

     ;; The group doesn't exist
     (mdb/save-concept context (group->new-concept context group)))))

(defn group-exists?
  "Returns true if group exists."
  [context concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (when (not= :access-group concept-type)
      (errors/throw-service-error :bad-request (g-msg/bad-group-concept-id concept-id))))
  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (not (:deleted concept))
    false))

(defn get-group
  "Retrieves a group with the given concept id."
  [context concept-id]
  (let [group (edn/read-string (:metadata (fetch-group-concept context concept-id)))]
    (auth/verify-can-read-group context group)
    ;; Group response includes the number of members and not the actual members
    (-> group
        (assoc :num-members (count (:members group)))
        (dissoc :members))))

(defn delete-group
  "Deletes a group with the given concept id"
  [context concept-id]
  (let [group-concept (fetch-group-concept context concept-id)
        group (edn/read-string (:metadata group-concept))]
    (auth/verify-can-delete-group context group)
    ;; find and delete any ACLs that target this group
    (doseq [acl-concept (acl-service/get-all-acl-concepts context)
            :let [parsed-acl (acl-service/get-parsed-acl acl-concept)]
            :when (= concept-id (get-in parsed-acl [:single-instance-identity :target-id]))]
      (acl-service/delete-acl context (:concept-id acl-concept)))
    (save-deleted-group-concept context group-concept)))

(defn update-group
  "Updates an existing group with the given concept id"
  [context concept-id updated-group]
  (let [existing-concept (fetch-group-concept context concept-id)
        existing-group (edn/read-string (:metadata existing-concept))]
    (validate-update-group context existing-group updated-group)
    (auth/verify-can-update-group context existing-group)
    ;; Avoid clobbering :members by merging the updated-group into existing-group. If updated-group
    ;; specifies :members then it will overwrite the existing value.
    (save-updated-group-concept context existing-concept (merge existing-group updated-group))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
      (common-qm/negated-condition (common-qm/exist-condition :provider))
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
  ([context concept-id members {:keys [skip-acls?]}]
   (validate-members-exist context members)
   (let [existing-concept (fetch-group-concept context concept-id)
         existing-group (edn/read-string (:metadata existing-concept))
         updated-group (add-members-to-group existing-group members)]
     (when-not skip-acls?
       (auth/verify-can-update-group context existing-group))
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
    (auth/verify-can-update-group context existing-group)
    (save-updated-group-concept context existing-concept updated-group)))

(defn get-members
  "Gets the members in the group."
  [context concept-id]
  (let [concept (fetch-group-concept context concept-id)
        group (edn/read-string (:metadata concept))]
    (auth/verify-can-read-group context group)
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
