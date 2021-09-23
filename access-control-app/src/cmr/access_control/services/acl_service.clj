(ns cmr.access-control.services.acl-service
  (:require
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.access-control.data.access-control-index :as index]
   [cmr.access-control.data.acl-json-results-handler :as result-handler]
   [cmr.access-control.data.acl-schema :as schema]
   [cmr.access-control.services.acl-authorization :as acl-auth]
   [cmr.access-control.services.acl-service-messages :as acl-msg]
   [cmr.access-control.services.acl-util :as acl-util]
   [cmr.access-control.services.acl-validation :as v]
   [cmr.access-control.services.auth-util :as auth-util]
   [cmr.access-control.services.messages :as msg]
   [cmr.access-control.services.parameter-validation :as pv]
   [cmr.acl.core :as acl]
   [cmr.common.cache :as cache]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.services.search.elastic-search-index :as common-esi]
   [cmr.common-app.services.search.params :as cp]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer [info debug]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.metadata-db :as mdb1]
   [cmr.transmit.metadata-db2 :as mdb]
   [cmr.transmit.urs :as urs]
   [cmr.umm-spec.acl-matchers :as acl-matchers]))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized msg/token-required)))

(defn- fetch-acl-concept
  "Fetches the latest version of ACL concept by concept id. Handles unknown concept ids by
  throwing a service error."
  [context concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (when (not= :acl concept-type)
      (errors/throw-service-error :bad-request (acl-msg/bad-acl-concept-id concept-id))))

  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found (acl-msg/acl-deleted concept-id))
      concept)
    (errors/throw-service-error :not-found (acl-msg/acl-does-not-exist concept-id))))

(defn create-acl
  "Save a new ACL to Metadata DB. Returns map with concept and revision id of created acl."
  [context acl]
  (common-enabled/validate-write-enabled context "access control")
  (v/validate-acl-save! context acl :create)
  (acl-auth/authorize-acl-action context :create acl)
  (let [acl (acl-util/sync-entry-titles-concept-ids context acl)]
    (acl-util/create-acl context acl)))

(defn- parse-validate-revision-id
  "Parse revision id and return it if it is positive"
  [revision-id]
  (try
    (let [revision-id (Integer/parseInt revision-id)]
      (when (pos? revision-id)
        revision-id))
    (catch NumberFormatException _)))

(defn- get-valid-revision-id
  "Return the revision id if it is valid."
  [revision-id]
  (if-let [valid-revision-id (parse-validate-revision-id revision-id)]
    valid-revision-id
    (errors/throw-service-error
      :invalid-data
      (msg/invalid-revision-id revision-id))))

(defn- set-revision-id-in-concept
  "Set the revision-id in concept if the revision-id is provided and valid."
  [concept revision-id]
  (if revision-id
    (assoc concept :revision-id (get-valid-revision-id revision-id))
    concept))

(defn update-acl
  "Update the ACL with the given concept-id and revision-id in Metadata DB.
  Returns map with concept and revision id of updated acl."
  ([context concept-id acl]
   (update-acl context concept-id nil acl))
  ([context concept-id revision-id acl]
   (common-enabled/validate-write-enabled context "access control")
   (v/validate-acl-save! context acl :update)
   (acl-auth/authorize-acl-action context :update acl)
   ;; This fetch acl call also validates if the ACL with the concept id does not exist or is deleted
   (let [existing-concept (fetch-acl-concept context concept-id)
         existing-legacy-guid (:legacy-guid (edn/read-string (:metadata existing-concept)))
         ;; An empty legacy guid can be passed in and we'll continue to use the same one
         acl (if existing-legacy-guid
               (update acl :legacy-guid #(or % existing-legacy-guid))
               acl)
         legacy-guid (:legacy-guid acl)]
     (when-not (= existing-legacy-guid legacy-guid)
       (errors/throw-service-error :invalid-data
                                   (format "ACL legacy guid cannot be updated, was [%s] and now [%s]"
                                           existing-legacy-guid legacy-guid)))
     (let [acl (acl-util/sync-entry-titles-concept-ids context acl)
           new-concept (merge (acl-util/acl->base-concept context acl)
                             {:concept-id concept-id
                              :native-id (:native-id existing-concept)})
           ;; set revision-id if provided and valid.
           new-concept (set-revision-id-in-concept new-concept revision-id)
           resp (mdb/save-concept context new-concept)]
       ;; index the saved ACL synchronously
       (index/index-acl context
                       (merge new-concept (select-keys resp [:concept-id :revision-id]))
                       {:synchronous? true})
       (info (acl-util/acl-log-message context new-concept existing-concept :update))
       resp))))

(defn delete-acl
  "Delete the ACL with the given concept id and revision-id"
  ([context concept-id]
   (delete-acl context concept-id nil))
  ([context concept-id revision-id]
   (common-enabled/validate-write-enabled context "access control")
   (let [acl-concept (fetch-acl-concept context concept-id)
         acl (edn/read-string (:metadata acl-concept))]
     (acl-auth/authorize-acl-action context :delete acl)
     (let [tombstone {:concept-id (:concept-id acl-concept)
                       :revision-id (if revision-id
                                      (get-valid-revision-id revision-id)
                                      (inc (:revision-id acl-concept)))
                       :deleted true}
           resp (mdb/save-concept context tombstone)]
       ;; unindexing is synchronous
       (index/unindex-acl context concept-id (:revision-id tombstone))
       (info (acl-util/acl-log-message context tombstone acl-concept :delete))
       resp))))

;; Member Functions

(defn get-acl
  "Returns the parsed metadata of the latest revision of the ACL concept by id."
  [context concept-id params]
  (let [acl (edn/read-string (:metadata (fetch-acl-concept context concept-id)))
        params (cp/sanitize-params params)]
    (acl-auth/authorize-acl-action context :read acl)
    (if (= "true" (:include-legacy-group-guid params))
      (result-handler/update-acl-legacy-group-guid context acl)
      acl)))

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

(def all-permissions
  "The set of all permissions checked and returned by the functions below."
  #{:create :read :order :update :delete})

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

(defn granule-identifier-matches-granule?
  "Returns true if granule identifier portion of ACL matches granule concept."
  [gran-identifier granule]
  (let [{:keys [access-value temporal]} gran-identifier]
    (and (if access-value
           (acl-matchers/matches-access-value-filter? :granule granule access-value)
           true)
         (if temporal
           (when-let [umm-temporal (util/lazy-get granule :temporal)]
             (acl-matchers/matches-temporal-filter? :granule umm-temporal temporal))
           true))))

(defn collection-identifier-matches-granule?
  "Returns true if the collection identifier (a field in catalog item identities in ACLs) is nil or
  it matches the granule concept."
  [collection-identifier granule]
  (if collection-identifier
    (acl-matchers/coll-matches-collection-identifier? (:parent-collection granule) collection-identifier)
    true))

(defn acl-matches-granule?
  "Returns true if the acl matches the concept indicating the concept is permitted."
  [acl granule]
  (let [{{:keys [provider-id granule-identifier collection-identifier granule-applicable]} :catalog-item-identity} acl]
    (and granule-applicable
         (= provider-id (:provider-id granule))
         (granule-identifier-matches-granule? granule-identifier granule)
         (collection-identifier-matches-granule? collection-identifier granule))))

(defn- grants-concept-permission?
  "Returns true if permission keyword is granted on concept to any sids by given acl."
  [acl permission concept sids]
  (and (acl/acl-matches-sids-and-permission? sids (name permission) acl)
       (case (:concept-type concept)
         :collection (acl-matchers/coll-applicable-acl? (:provider-id concept) concept acl)
         :granule (acl-matches-granule? acl concept))))

(defn- provider-acl?
  "Returns true if the ECHO-style acl specifically identifies the given provider id."
  [provider-id acl]
  (or
    (-> acl :provider-identity :provider-id (= provider-id))
    (-> acl :catalog-item-identity :provider-id (= provider-id))))

(defn- ingest-management-acl?
  "Returns true if the ACL targets a provider INGEST_MANAGEMENT_ACL."
  [acl]
  (-> acl :provider-identity :target (= schema/ingest-management-acl-target)))

(defn- concept-permissions-granted-by-acls
  "Returns the set of permission keywords (:read, :order, and :update) granted on concept
   to the seq of group guids by seq of acls."
  [concept sids acls]
  (let [provider-acls (filter #(provider-acl? (:provider-id concept) %) acls)
        ;; When a user has UPDATE on the provider's INGEST_MANAGEMENT_ACL target, then they have UPDATE and
        ;; DELETE permission on all of the provider's catalog items.
        ingest-management-permissions (when (some #(acl/acl-matches-sids-and-permission? sids "update" %)
                                                  (filter ingest-management-acl? provider-acls))
                                        [:update :delete])
        ;; The remaining catalog item ACLs can only grant READ or ORDER permission.
        catalog-item-acls (filter :catalog-item-identity provider-acls)
        catalog-item-permissions (for [permission [:read :order]
                                       :when (some #(grants-concept-permission? % permission concept sids)
                                                   catalog-item-acls)]
                                   permission)]
    (set
     (concat catalog-item-permissions
             ingest-management-permissions))))

(defn- add-acl-enforcement-fields
  "Adds all fields necessary for comparing concept map against ACLs."
  [context concept]
  (let [concept (acl-matchers/add-acl-enforcement-fields-to-concept concept)]
    (if-let [parent-id (:collection-concept-id concept)]
      (assoc concept :parent-collection
                     (acl-matchers/add-acl-enforcement-fields-to-concept
                       (mdb/get-latest-concept context parent-id)))
      concept)))

(defn get-catalog-item-permissions
  "Returns a map of concept ids to seqs of permissions granted on that concept for the given username."
  [context username-or-type concept-ids]
  (let [sids (auth-util/get-sids context username-or-type)
        acls (concat
               (acl-util/get-acl-concepts-by-identity-type-and-target
                context index/provider-identity-type-name schema/ingest-management-acl-target)
               (acl-util/get-acl-concepts-by-identity-type-and-target
                context index/catalog-item-identity-type-name nil))]
    (into {}
          (for [concept (mdb1/get-latest-concepts context concept-ids)
                :let [concept-with-acl-fields (add-acl-enforcement-fields context concept)]]
            [(:concept-id concept)
             (concept-permissions-granted-by-acls concept-with-acl-fields sids acls)]))))

(defn system-permissions-granted-by-acls
  "Returns a set of permission keywords granted on the system target to the given sids by the given acls."
  [system-object-target sids acls]
  (let [relevant-acls (filter #(-> % :system-identity :target (= system-object-target))
                              acls)]
    (set
      (for [permission [:create :read :update :delete]
            :when (some #(acl/acl-matches-sids-and-permission? sids (name permission) %)
                        relevant-acls)]
        permission))))

(defn get-system-permissions
  "Returns a map of the system object type to the set of permissions granted to the given username or user type."
  [context username-or-type system-object-target]
  (let [sids (auth-util/get-sids context username-or-type)
        acls (acl-util/get-acl-concepts-by-identity-type-and-target
              context index/system-identity-type-name system-object-target)]
    (hash-map system-object-target (system-permissions-granted-by-acls system-object-target sids acls))))

(defn provider-permissions-granted-by-acls
  "Returns all permissions granted to provider target for given sids and acls."
  [provider-id target sids acls]
  (collect-permissions (fn [acl permission]
                         (and (= target (:target (:provider-identity acl)))
                              (provider-acl? provider-id acl)
                              (acl/acl-matches-sids-and-permission? sids (name permission) acl)))
                       acls))

(defn get-provider-permissions
  "Returns a map of target object ids to permissions granted to the specified user for the specified provider id."
  [context username-or-type provider-id target]
  (let [sids (auth-util/get-sids context username-or-type)
        acls (acl-util/get-acl-concepts-by-identity-type-and-target
              context index/provider-identity-type-name target)]
    (hash-map target (provider-permissions-granted-by-acls provider-id target sids acls))))

(defn- group-permissions-granted-by-acls
  "Returns all permissions granted to the single instance identity target group id
   for the given sids and acls."
  [group-id sids acls]
  (collect-permissions (fn [acl permission]
                         (and (= group-id (get-in acl [:single-instance-identity :target-id]))
                              (acl/acl-matches-sids-and-permission? sids (name permission) acl)))
                       acls))

(defn- get-group-permissions
  "Returns a map of the target group concept ids to the set of permissions
   granted to the given username or user type."
  [context username-or-type target-group-ids]
  (let [sids (auth-util/get-sids context username-or-type)
        acls (acl-util/get-acl-concepts-by-identity-type-and-target
              context index/single-instance-identity-type-name nil)]
    (into {}
          (for [group-id target-group-ids]
            [group-id (group-permissions-granted-by-acls group-id sids acls)]))))

(defn get-permissions
  "Returns result of permissions check for the given parameters."
  [context params]
  (let [params (-> params
                   (update-in [:concept_id] util/seqify)
                   (update-in [:target_group_id] util/seqify))]
    (pv/validate-get-permission-params params)
    (let [{:keys [user_id user_type concept_id system_object provider target target_group_id]} params
          username-or-type (if user_type
                             (keyword user_type)
                             user_id)]
      (cond
        system_object (get-system-permissions context username-or-type system_object)
        target (get-provider-permissions context username-or-type provider target)
        target_group_id (get-group-permissions context username-or-type target_group_id)
        :else (get-catalog-item-permissions context username-or-type concept_id)))))

(defn get-current-sids
  "Returns result of check for current user's group sids"
  [context params]
  (let [user-token (:user-token params)]
    ;; If token is nil or empty, treat as guest
    (if (or (nil? user-token)(re-matches #"^[\s\t]*$" user-token))
      (auth-util/get-sids context :guest)
      (auth-util/get-sids context (tokens/get-user-id context user-token)))))

(defn- fix-single-string-with-multiple-values
  "Check if string has embedded array, if not then return result conj'd with results,
   if so then convert it into an array of strings and concat that with results."
  [results result]
  (let [result (string/trim result)]
    (if (string/starts-with? result "[")
      (as-> (string/replace result #"\[|\]|\\|\"" "") result
            (string/split result #",")
            (map string/trim result)
            (concat results result))
      (conj results result))))

(defn- parse-single-string-multi-valued-bucket-lists
  "Depending on how data is initially ingested, sometimes multiple values can be returned as a single string.
   Here we attempt to parse out that single string value(of multiple values) into the proper format."
  [results]
  (reduce fix-single-string-with-multiple-values [] results))

(defn- fetch-s3-buckets-by-sids
  "Fetch the list of S3 buckets available to the SIDs provided. Buckets
  associated with the list of providers will be returned. If no providers
  all buckets available to the SIDs will be returned."
  [context sids providers]
  (let [base-condition (->> sids
                            (map #(qm/string-condition :permitted-group-ids % true false))
                            gc/or-conds)
        condition (if (empty? providers)
                    base-condition
                    (gc/and
                     base-condition
                     (->> providers
                          (map #(qm/string-condition :provider-id %))
                          gc/or-conds)))
        query (qm/query {:concept-type :collection
                         :condition condition
                         :skip-acls? true
                         :page-size :unlimited
                         :result-format :query-specified
                         :result-fields [:s3-bucket-and-object-prefix-names]})
        es-results (qe/execute-query context query)]
    (->> es-results
         :items
         (map :s3-bucket-and-object-prefix-names)
         flatten
         distinct
         (remove nil?)
         parse-single-string-multi-valued-bucket-lists)))

(defn- get-and-cache-providers
  "Retrieves and caches the current providers in the database."
  [context providers-cache]
  (let [db-provs (mdb/get-providers context)]
    (cache/set-value providers-cache :providers db-provs)
    db-provs))

(defn- get-cached-providers
  "Retrieve the list of all providers from the cache. Setting the value
  if not present."
  [context]
  (let [providers-cache (cache/context->cache context :providers)]
    (or (cache/get-value providers-cache :providers)
        (get-and-cache-providers context providers-cache))))

(defn- validate-providers-exist
  "Throws an exception if the given provider-ids are invalid, otherwise returns the input."
  [context providers]
  (when-let [invalid-providers
             (seq (set/difference
                   (set providers)
                   (set (map :provider-id (get-cached-providers context)))))]
    (errors/throw-service-errors
     :bad-request
     (map msg/provider-does-not-exist invalid-providers)))
  providers)

(defmethod common-esi/concept-type->index-info :collection
  [context _ query]
  ;; This function mirrors the multimethod definition in search.
  ;; Search is not a dependency of access-control and this must be
  ;; defined for collection search to work
  {:index-name (if (:all-revisions? query)
                 "1_all_collection_revisions"
                 "collection_search_alias")
   :type-name "collection"})

(defn s3-buckets-for-user
  "Returns a list of s3 buckets and object prefix names by provider."
  [context user provider-ids]
  (when-not (urs/user-exists? context user)
    (errors/throw-service-error
     :bad-request
     (msg/users-do-not-exist [user])))

  (let [sids (map name (auth-util/get-sids context user))
        providers (when (seq provider-ids)
                    (validate-providers-exist context provider-ids))]
    (if (empty? sids)
      []
      (fetch-s3-buckets-by-sids context sids providers))))
