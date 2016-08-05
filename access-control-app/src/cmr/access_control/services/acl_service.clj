(ns cmr.access-control.services.acl-service
  (:require [clojure.string :as str]
            [cmr.access-control.services.acl-service-messages :as msg]
            [cmr.common.log :refer [info debug]]
            [cmr.common.util :as u]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [cmr.common.validations.core :as v]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.metadata-db :as mdb1]
            [cmr.transmit.metadata-db2 :as mdb]
            [cmr.common-app.services.search :as cs]
            [cmr.common-app.services.search.params :as cp]
            [cmr.common-app.services.search.parameter-validation :as cpv]
            [cmr.common-app.services.search.query-model :as common-qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [cmr.common.concepts :as concepts]
            [cmr.access-control.services.group-service :as groups]
            [cmr.transmit.metadata-db :as mdb1]
            [cmr.umm-spec.legacy :as umm-legacy]
            [cmr.acl.core :as acl]
            [cmr.umm.acl-matchers :as acl-matchers]
            [cmr.common.util :as util]
            [cmr.common.date-time-parser :as dtp]
            [cmr.access-control.data.acls :as acls]
            [clj-time.core :as t]))

;;; Validations

(defn- catalog-item-identity-collection-applicable-validation
  [key-path cat-item-id]
  (when (and (:collection-identifier cat-item-id)
             (not (:collection-applicable cat-item-id)))
    {key-path ["collection_applicable must be true when collection_identifier is specified"]}))

(defn- catalog-item-identity-granule-applicable-validation
  [key-path cat-item-id]
  (when (and (:granule-identifier cat-item-id)
             (not (:granule-applicable cat-item-id)))
    {key-path ["granule_applicable must be true when granule_identifier is specified"]}))

(defn- catalog-item-identity-collection-or-granule-validation
  "Validates minimal catalog_item_identity fields."
  [key-path cat-item-id]
  (when-not (or (:collection-applicable cat-item-id)
                (:granule-applicable cat-item-id))
    {key-path ["when catalog_item_identity is specified, one or both of collection_applicable or granule_applicable must be true"]}))

(defn- make-collection-entry-titles-validation
  "Returns a validation for the entry_titles part of a collection identifier, closed over the context and ACL to be validated."
  [context acl]
  (let [provider-id (-> acl :catalog-item-identity :provider-id)]
    (v/every (fn [key-path entry-title]
               (when-not (seq (mdb1/find-concepts context {:provider-id provider-id :entry-title entry-title} :collection))
                 {key-path [(format "collection with entry-title [%s] does not exist in provider [%s]" entry-title provider-id)]})))))

(defn- access-value-validation
  "Validates the access_value part of a collection or granule identifier."
  [key-path access-value-map]
  (let [{:keys [min-value max-value include-undefined-value]} access-value-map]
    (cond
      (and include-undefined-value (or min-value max-value))
      {key-path ["min_value and/or max_value must not be specified if include_undefined_value is true"]}

      (and (not include-undefined-value) (not (or min-value max-value)))
      {key-path ["min_value and/or max_value must be specified when include_undefined_value is false"]})))

(defn temporal-identifier-validation
  "A validation for the temporal part of an ACL collection or granule identifier."
  [key-path temporal]
  (let [{:keys [start-date end-date]} temporal]
    (when (and start-date end-date
               (t/after? (dtp/parse-datetime start-date) (dtp/parse-datetime end-date)))
      {key-path ["start_date must be before end_date"]})))

(defn- make-collection-identifier-validation
  "Returns a validation for an ACL catalog_item_identity.collection_identifier closed over the given context and ACL to be validated."
  [context acl]
  {:entry-titles (v/when-present (make-collection-entry-titles-validation context acl))
   :access-value (v/when-present access-value-validation)
   :temporal (v/when-present temporal-identifier-validation)})

(def granule-identifier-validation
  "Validation for the catalog_item_identity.granule_identifier portion of an ACL."
  {:access-value access-value-validation
   :temporal temporal-identifier-validation})

(defn- make-catalog-item-identity-validations
  "Returns a standard validation for an ACL catalog_item_identity field closed over the given context and ACL to be validated."
  [context acl]
  [catalog-item-identity-collection-or-granule-validation
   catalog-item-identity-collection-applicable-validation
   catalog-item-identity-granule-applicable-validation
   {:collection-identifier (v/when-present (make-collection-identifier-validation context acl))
    :granule-identifier (v/when-present granule-identifier-validation)}])

(defn- make-acl-validations
  "Returns a sequence of validations closed over the given context for validating ACL records."
  [context acl]
  {:catalog-item-identity (v/when-present (make-catalog-item-identity-validations context acl))})

(defn validate-acl-save!
  "Throws service errors if ACL is invalid."
  [context acl]
  (v/validate! (make-acl-validations context acl) acl))

;;; Misc constants and accessor functions

(def acl-provider-id
  "The provider ID for all ACLs. Since ACLs are not owned by individual
  providers, they fall under the CMR system provider ID."
  "CMR")

(defn acl-identity
  "Returns a string value representing the ACL's identity field."
  [acl]
  (str/lower-case
    (let [{:keys [system-identity provider-identity single-instance-identity catalog-item-identity]} acl]
      (cond
        system-identity          (str "system:" (:target system-identity))
        single-instance-identity (format "single-instance:%s:%s"
                                         (:target-id single-instance-identity)
                                         (:target single-instance-identity))
        provider-identity        (format "provider:%s:%s"
                                         (:provider-id provider-identity)
                                         (:target provider-identity))
        catalog-item-identity    (format "catalog-item:%s:%s"
                                         (:provider-id catalog-item-identity)
                                         (:name catalog-item-identity))
        :else                    (errors/throw-service-error
                                   :bad-request "malformed ACL")))))

(defn- fetch-acl-concept
  "Fetches the latest version of ACL concept by concept id. Handles unknown concept ids by
  throwing a service error."
  [context concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (when (not= :acl concept-type)
      (errors/throw-service-error :bad-request (msg/bad-acl-concept-id concept-id))))

  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found (msg/acl-deleted concept-id))
      concept)
    (errors/throw-service-error :not-found (msg/acl-does-not-exist concept-id))))

(defn- get-sids
  "Returns a seq of sids for the given username string or user type keyword
   for use in checking permissions against acls."
  [context username-or-type]
  (cond
    (contains? #{:guest :registered} username-or-type) [username-or-type]
    (string? username-or-type) (concat [:registered]
                                       (->> (groups/search-for-groups context {:member username-or-type})
                                            :results
                                            :items
                                            (map :concept_id)))))

(defn- acl->base-concept
  "Returns a basic concept map for the given request context and ACL map."
  [context acl]
  {:concept-type :acl
   :metadata (pr-str acl)
   :format mt/edn
   :provider-id acl-provider-id
   :user-id (when-let [token (:token context)]
              (tokens/get-user-id context token))
   ;; ACL-specific fields
   :extra-fields {:acl-identity (acl-identity acl)
                  :target-provider-id (acls/acl->provider-id acl)}})

(defn create-acl
  "Save a new ACL to Metadata DB. Returns map with concept and revision id of created acl."
  [context acl]
  (validate-acl-save! context acl)
  (mdb/save-concept context (merge (acl->base-concept context acl)
                                   {:revision-id 1
                                    :native-id (str (java.util.UUID/randomUUID))})))

(defn update-acl
  "Update the ACL with the given concept-id in Metadata DB. Returns map with concept and revision id of updated acl."
  [context concept-id acl]
  (validate-acl-save! context acl)
  ;; This fetch acl call also validates if the ACL with the concept id does not exist or is deleted
  (let [existing-concept (fetch-acl-concept context concept-id)
        existing-legacy-guid (:legacy-guid (edn/read-string (:metadata existing-concept)))
        legacy-guid (:legacy-guid acl)]
    (when-not (= existing-legacy-guid legacy-guid)
      (errors/throw-service-error
        :invalid-data (format "ACL legacy guid cannot be updated, was [%s] and now [%s]"
                              existing-legacy-guid legacy-guid)))
    (mdb/save-concept context (merge (acl->base-concept context acl)
                                     {:concept-id concept-id
                                      :native-id (:native-id existing-concept)}))))

(defn delete-acl
  "Saves a tombstone for the ACL with the given concept id."
  [context concept-id]
  (let [acl-concept (fetch-acl-concept context concept-id)]
    (mdb/save-concept context {:concept-id (:concept-id acl-concept)
                               :revision-id (inc (:revision-id acl-concept))
                               :deleted true})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search functions

(defmethod cpv/params-config :acl
  [_]
  (cpv/merge-params-config
    cpv/basic-params-config
    {:single-value #{}
     :multiple-value #{:permitted-group :identity-type :provider}
     :always-case-sensitive #{}
     :disallow-pattern #{:identity-type :permitted-user}
     :allow-or #{}}))

(defmethod cpv/valid-parameter-options :acl
  [_]
  {:permitted-group cpv/string-param-options
   :provider cpv/string-param-options
   :identity-type cpv/string-param-options
   :permitted-user #{}})

(defn- valid-permitted-group?
  "Returns true if the given permitted group is valid, i.e. guest, registered or conforms to
  access group id format."
  [group]
  (or (.equalsIgnoreCase "guest" group)
      (.equalsIgnoreCase "registered" group)
      (some? (re-find #"[Aa][Gg]\d+-.+" group))))

(defn- permitted-group-validation
  "Validates permitted group parameters."
  [context params]
  (let [permitted-groups (u/seqify (:permitted-group params))]
    (when-let [invalid-groups (seq (remove valid-permitted-group? permitted-groups))]
      [(format "Parameter permitted_group has invalid values [%s]. Only 'guest', 'registered' or a group concept id can be specified."
               (str/join ", " invalid-groups))])))

(def acl-identity-type->search-value
 "Maps identity type query paremter values to the actual values used in the index."
 {"system" "System"
  "single_instance" "Group"
  "provider" "Provider"
  "catalog_item" "Catalog Item"})

(defn- valid-identity-type?
  "Returns true if the given identity-type is valid, i.e., one of 'system', 'single_instance', 'provider', or 'catalog_item'."
  [identity-type]
  (contains? (set (keys acl-identity-type->search-value)) (str/lower-case identity-type)))

(defn- identity-type-validation
  "Validates identity-type parameters."
  [context params]
  (let [identity-types (u/seqify (:identity-type params))]
    (when-let [invalid-types (seq (remove valid-identity-type? identity-types))]
      [(format (str "Parameter identity_type has invalid values [%s]. "
                    "Only 'provider', 'system', 'single_instance', or 'catalog_item' can be specified.")
               (str/join ", " invalid-types))])))

(defn validate-acl-search-params
  "Validates the parameters for an ACL search. Returns the parameters or throws an error if invalid."
  [context params]
  (let [[safe-params type-errors] (cpv/apply-type-validations
                                    params
                                    [(partial cpv/validate-map [:options])
                                     (partial cpv/validate-map [:options :permitted-group])
                                     (partial cpv/validate-map [:options :identity-type])
                                     (partial cpv/validate-map [:options :provider])
                                     (partial cpv/validate-map [:options :permitted-user])])]
    (cpv/validate-parameters
      :acl safe-params
      (concat cpv/common-validations
              [permitted-group-validation identity-type-validation])
      type-errors))
  params)

(defmethod cp/always-case-sensitive-fields :acl
  [_]
  #{:concept-id :identity-type})

(defmethod common-qm/default-sort-keys :acl
  [_]
  [{:field :display-name :order :asc}])

(defmethod cp/param-mappings :acl
  [_]
  {:permitted-group :string
   :identity-type :acl-identity-type
   :provider :string
   :permitted-user :acl-permitted-user})

(defmethod cp/parameter->condition :acl-identity-type
 [context concept-type param value options]
 (if (sequential? value)
   (gc/group-conds (cp/group-operation param options)
                   (map #(cp/parameter->condition context concept-type param % options) value))
   (let [value (get acl-identity-type->search-value (str/lower-case value))]
     (cp/string-parameter->condition concept-type param value options))))

(defmethod cp/parameter->condition :acl-permitted-user
  [context concept-type param value options]
  ;; reject non-existent users
  (groups/validate-members-exist context [value])

  (let [groups (->> (get-sids context value)
                    (map name))]
    (cp/string-parameter->condition concept-type :permitted-group groups options)))

(defn search-for-acls
  [context params]
  (let [[query-creation-time query] (u/time-execution
                                      (->> params
                                           cp/sanitize-params
                                           (validate-acl-search-params :acl)
                                           (cp/parse-parameter-query context :acl)))
        [find-concepts-time results] (u/time-execution
                                       (cs/find-concepts context :acl query))
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d acls in %d ms in format %s with params %s."
                  (:hits results) total-took (common-qm/base-result-format query) (pr-str params)))
    (assoc results :took total-took)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Member functions

(defn get-acl
  "Returns the parsed metadata of the latest revision of the ACL concept by id."
  [context concept-id]
  (edn/read-string (:metadata (fetch-acl-concept context concept-id))))

(defn- echo-style-acl
  "Returns acl with the older ECHO-style keywords for consumption in utility functions from other parts of the CMR."
  [acl]
  (-> acl
      (set/rename-keys {:system-identity :system-object-identity
                        :provider-identity :provider-object-identity
                        :group-permissions :aces})
      (util/update-in-each [:aces] update-in [:user-type] keyword)
      (util/update-in-each [:aces] set/rename-keys {:group-id :group-guid})
      (update-in [:catalog-item-identity :collection-identifier :temporal]
                 (fn [t]
                   (when t
                     (-> t
                         (assoc :temporal-field :acquisition)
                         (update-in [:mask] keyword)
                         (update-in [:start-date] dtp/try-parse-datetime)
                         (update-in [:end-date] dtp/try-parse-datetime)))))
      (update-in [:catalog-item-identity :collection-identifier :access-value]
                 (fn [av]
                   (when av
                     (set/rename-keys av {:include-undefined-value :include-undefined}))))
      util/remove-nil-keys))

(defn get-all-acl-concepts
  "Returns all ACLs in metadata db."
  [context]
  (for [batch (mdb1/find-in-batches context :acl 1000 {:latest true})
        acl-concept batch]
    acl-concept))

(defn get-parsed-acl
  "Returns the ACL concept's metadata parased from EDN."
  [acl-concept]
  (edn/read-string (:metadata acl-concept)))

(defn- get-echo-style-acls
  "Returns all ACLs in metadata db, converted to \"ECHO-style\" keys for use with existing ACL functions."
  [context]
  (map echo-style-acl (map get-parsed-acl (get-all-acl-concepts context))))

(def all-permissions
  "The set of all permissions checked and returned by the functions below."
  #{:read :order :update :delete})

(def provider-level-permissions
  "The set of permissions that are checked at the provider level."
  #{:update :delete})

(defn- collect-permissions
  "Returns seq of any permissions where (grants-permission? acl permission) returns true for any acl in acls."
  [grants-permission? acls]
  (reduce (fn [granted-permissions acl]
            (if (= all-permissions granted-permissions)
              ;; terminate the reduce early, because all permissions have already been granted
              (reduced granted-permissions)
              ;; determine which permissions are granted by this specific acl
              (reduce (fn [acl-permissions permission]
                        (if (grants-permission? acl permission)
                          (conj acl-permissions permission)
                          acl-permissions))
                      ;; start with the set of permissions granted so far
                      granted-permissions
                      ;; and only reduce over permissions that have not yet been granted
                      (set/difference all-permissions granted-permissions))))
          #{}
          acls))

(defn- grants-concept-permission?
  "Returns true if permission keyword is granted on concept to any sids by given acl."
  [acl permission concept sids]
  (and (acl/acl-matches-sids-and-permission? sids (name permission) acl)
       (if (contains? provider-level-permissions permission)
         (when-let [acl-provider-id (-> acl :provider-object-identity :provider-id)]
           (= acl-provider-id (:provider-id concept)))
         ;; else check that the concept matches
         (condp = (:concept-type concept)
           :collection (acl-matchers/coll-applicable-acl? (:provider-id concept) concept acl)
           ;; part of CMR-2900 to be implemented in a future pull request
           :granule false))))

(defn- concept-permissions-granted-by-acls
  "Returns the set of permission keywords (:read, :update, :order, or :delete) granted on concept
   to the seq of group guids by seq of acls."
  [concept sids acls]
  (collect-permissions #(grants-concept-permission? %1 %2 concept sids)
                       acls))

(defn get-concept-permissions
  "Returns a map of concept ids to seqs of permissions granted on that concept for the given username."
  [context username-or-type concept-ids]
  (let [concepts (acl-matchers/add-acl-enforcement-fields
                   (mdb1/get-latest-concepts context concept-ids))
        sids (get-sids context username-or-type)
        acls (get-echo-style-acls context)]
    (into {}
          (for [concept concepts]
            [(:concept-id concept) (concept-permissions-granted-by-acls concept sids acls)]))))

(defn- system-permissions-granted-by-acls
  "Returns a set of permission keywords granted on the system target to the given sids by the given acls."
  [system-object-target sids acls]
  (collect-permissions (fn [acl permission]
                         (and (= system-object-target (:target (:system-object-identity acl)))
                              (acl/acl-matches-sids-and-permission? sids (name permission) acl)))
                       acls))

(defn get-system-permissions
  "Returns a map of the system object type to the set of permissions granted to the given username or user type."
  [context username-or-type system-object-target]
  (let [sids (get-sids context username-or-type)
        acls (get-echo-style-acls context)]
    (hash-map system-object-target (system-permissions-granted-by-acls system-object-target sids acls))))

(defn provider-permissions-granted-by-acls
  "Returns all permissions granted to provider target for given sids and acls."
  [provider-id target sids acls]
  (collect-permissions (fn [acl permission]
                         (and (= target (:target (:provider-object-identity acl)))
                              (acl/acl-matches-sids-and-permission? sids (name permission) acl)))
                       acls))

(defn get-provider-permissions
  "Returns a map of target object ids to permissions granted to the specified user for the specified provider id."
  [context username-or-type provider-id target]
  (let [sids (get-sids context username-or-type)
        acls (get-echo-style-acls context)]
    (hash-map target (provider-permissions-granted-by-acls provider-id target sids acls))))

