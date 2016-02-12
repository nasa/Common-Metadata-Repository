(ns cmr.access-control.services.group-service
  "Provides functions for creating, updating, deleting, retrieving, and finding groups."
  (:require [cmr.transmit.metadata-db2 :as mdb]
            [cmr.transmit.echo.rest :as rest]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.concepts :as concepts]
            [cmr.common.services.errors :as errors]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as u]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.validations.core :as v]
            [cmr.common-app.services.search :as cs]
            [cmr.common-app.services.search.params :as cp]
            [cmr.common-app.services.search.parameter-validation :as cpv]
            [cmr.common-app.services.search.query-model :as common-qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.transmit.urs :as urs]
            [cmr.access-control.services.group-service-messages :as msg]
            [clojure.edn :as edn]
            [clojure.string :as str]
            ;; Must be required to be available at runtime
            [cmr.access-control.data.group-json-results-handler]))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized msg/token-required-for-group-modification)))

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
   :native-id (:name group)
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
      (errors/throw-service-error :bad-request (msg/bad-group-concept-id concept-id))))

  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found (msg/group-deleted concept-id))
      concept)
    (errors/throw-service-error :not-found (msg/group-does-not-exist concept-id))))

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

(defn- validate-members-exist
  "Validates that the given usernames exist. Throws an exception if they do not."
  [context usernames]
  (when-let [non-existent-users (seq (remove #(urs/user-exists? context %) (distinct usernames)))]
    (errors/throw-service-error :bad-request (msg/users-do-not-exist non-existent-users))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Service level functions

(defn create-group
  "Creates the group by saving it to Metadata DB. Returns a map of the concept id and revision id of
  the created group."
  [context group]
  (validate-create-group context group)
  ;; Check if the group already exists
  (if-let [concept-id (mdb/get-concept-id context
                                          :access-group
                                          (group->mdb-provider-id group)
                                          (:name group))]

    ;; The group exists. Check if its latest revision is a tombstone
    (let [concept (mdb/get-latest-concept context concept-id)]
      (if (:deleted concept)
        ;; The group exists but was previously deleted.
        (save-updated-group-concept context concept group)

        ;; The group exists and was not deleted. Reject this.
        (errors/throw-service-error :conflict (msg/group-already-exists group concept-id))))

    ;; The group doesn't exist
    (mdb/save-concept context (group->new-concept context group))))

(defn get-group
  "Retrieves a group with the given concept id."
  [context concept-id]
  (let [group (edn/read-string (:metadata (fetch-group-concept context concept-id)))]
    ;; Group response includes the number of members and not the actual members
    (-> group
        (assoc :num-members (count (:members group)))
        (dissoc :members))))

(defn delete-group
  "Deletes a group with the given concept id"
  [context concept-id]
  (save-deleted-group-concept context (fetch-group-concept context concept-id)))

(defn update-group
  "Updates an existing group with the given concept id"
  [context concept-id updated-group]
  (let [existing-concept (fetch-group-concept context concept-id)
        existing-group (edn/read-string (:metadata existing-concept))]
    (validate-update-group context existing-group updated-group)
    (save-updated-group-concept context existing-concept updated-group)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search functions

(defmethod cpv/params-config :access-group
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{}
    :multiple-value #{:provider :name :member}
    :always-case-sensitive #{}
    :disallow-pattern #{}
    :allow-or #{}}))

(defmethod cpv/valid-parameter-options :access-group
  [_]
  {:provider cpv/string-param-options
   :name cpv/string-param-options
   :member #{:pattern :and}})

(defn validate-group-search-params
  "Validates the parameters for a group search. Returns the parameters or throws an error if invalid."
  [context params]
  (let [[safe-params type-errors] (cpv/apply-type-validations
                                   params
                                   [(partial cpv/validate-map [:options])
                                    (partial cpv/validate-map [:options :provider])
                                    (partial cpv/validate-map [:options :name])
                                    (partial cpv/validate-map [:options :member])])]
    (cpv/validate-parameters
     :access-group safe-params
     cpv/common-validations
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
   :member :string})

(defmethod cp/parameter->condition :access-group-provider
  [concept-type param value options]

  (if (sequential? value)
    (gc/group-conds (cp/group-operation param options)
                    (map #(cp/parameter->condition concept-type param % options) value))
    ;; CMR indicates we should search for system groups
    (if (= (str/upper-case value) SYSTEM_PROVIDER_ID)
      (common-qm/negated-condition (common-qm/exist-condition :provider))
      (cp/string-parameter->condition concept-type param value options))))


(defn search-for-groups
  [context params]
  (let [[query-creation-time query] (u/time-execution
                                     (->> params
                                          cp/sanitize-params
                                          (validate-group-search-params :access-group)
                                          (cp/parse-parameter-query :access-group)))
        [find-concepts-time results] (u/time-execution
                                      (cs/find-concepts context :access-group query))
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d access-groups in %d ms in format %s with params %s."
                  (:hits results) total-took (:result-format query) (pr-str params)))
    (assoc results :took total-took)))


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
  [context concept-id members]
  (validate-members-exist context members)
  (let [existing-concept (fetch-group-concept context concept-id)
        existing-group (edn/read-string (:metadata existing-concept))
        updated-group (add-members-to-group existing-group members)]
    (save-updated-group-concept context existing-concept updated-group)))

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
    (save-updated-group-concept context existing-concept updated-group)))

(defn get-members
  "Gets the members in the group."
  [context concept-id]
  (-> (fetch-group-concept context concept-id)
      :metadata
      edn/read-string
      (get :members [])))

(defn health
  "Returns the health state of the app."
  [context]
  (let [echo-rest-health (rest/health context)
        metadata-db-health (mdb/get-metadata-db-health context)
        ok? (every? :ok? [echo-rest-health metadata-db-health])]
    {:ok? ok?
     :dependencies {:echo echo-rest-health
                    :metadata-db metadata-db-health}}))

